// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBList
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import jp.titze.intellij.helix.settings.HelixSettings
import java.awt.event.KeyEvent
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class HelixToolWindowNavigatorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixToolWindowState.reset()
        HelixSettings.instance.toolWindowNavigationEnabled = true
    }

    override fun tearDown() {
        HelixSettings.instance.toolWindowNavigationEnabled = false
        HelixToolWindowState.reset()
        super.tearDown()
    }

    fun testStateCountAccumulation() {
        assertEquals(null, HelixToolWindowState.getPendingCount())
        HelixToolWindowState.appendCountDigit(1)
        HelixToolWindowState.appendCountDigit(2)
        assertEquals(12, HelixToolWindowState.takeCount())
        assertEquals(null, HelixToolWindowState.getPendingCount())
    }

    fun testInterceptedKeyConsumption() {
        HelixToolWindowState.markInterceptedKey('j')
        assertTrue(HelixToolWindowState.consumeInterceptedKey('j'))
        assertFalse(HelixToolWindowState.consumeInterceptedKey('j'))
    }

    fun testInterceptedKeyConsumptionWithUndefinedChar() {
        HelixToolWindowState.markInterceptedKey(KeyEvent.VK_J, KeyEvent.CHAR_UNDEFINED)
        assertTrue(HelixToolWindowState.consumeInterceptedKey('j'))
        assertFalse(HelixToolWindowState.consumeInterceptedKey('j'))
    }

    fun testPendingSequence() {
        assertEquals("", HelixToolWindowState.getPendingSequence())
        HelixToolWindowState.setPendingSequence("g")
        assertEquals("g", HelixToolWindowState.getPendingSequence())
        HelixToolWindowState.clearPendingSequence()
        assertEquals("", HelixToolWindowState.getPendingSequence())
    }

    fun testTreeVerticalMovement() {
        val root = DefaultMutableTreeNode("Root")
        val child1 = DefaultMutableTreeNode("Child 1")
        val child2 = DefaultMutableTreeNode("Child 2")
        root.add(child1)
        root.add(child2)
        val tree = Tree(DefaultTreeModel(root))
        tree.setSelectionRow(0)

        assertEquals(0, tree.leadSelectionRow)

        // Simulate 'j' via actionMap
        val nextAction = tree.actionMap.get("selectNext")
        nextAction.actionPerformed(
            java.awt.event.ActionEvent(tree, java.awt.event.ActionEvent.ACTION_PERFORMED, "selectNext"),
        )
        assertEquals(1, tree.leadSelectionRow)

        // Simulate 'k' via actionMap
        val prevAction = tree.actionMap.get("selectPrevious")
        prevAction.actionPerformed(
            java.awt.event.ActionEvent(tree, java.awt.event.ActionEvent.ACTION_PERFORMED, "selectPrevious"),
        )
        assertEquals(0, tree.leadSelectionRow)
    }

    fun testDispatchJAndKMovesTreeSelection() {
        val root = DefaultMutableTreeNode("Root")
        val child1 = DefaultMutableTreeNode("Child 1")
        val child2 = DefaultMutableTreeNode("Child 2")
        root.add(child1)
        root.add(child2)
        val tree = Tree(DefaultTreeModel(root))
        val panel = JPanel()
        panel.putClientProperty("ToolWindow.id", "Project")
        panel.add(tree)

        tree.setSelectionRow(0)
        assertEquals(0, tree.leadSelectionRow)

        val jEvent = KeyEvent(
            tree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_J,
            KeyEvent.CHAR_UNDEFINED,
        )
        val handledJ = HelixToolWindowNavigator.dispatch(jEvent)
        assertTrue(handledJ)
        assertTrue(jEvent.isConsumed)
        assertEquals(1, tree.leadSelectionRow)
        assertTrue(HelixToolWindowState.consumeInterceptedKey('j'))

        val kEvent = KeyEvent(
            tree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_K,
            KeyEvent.CHAR_UNDEFINED,
        )
        val handledK = HelixToolWindowNavigator.dispatch(kEvent)
        assertTrue(handledK)
        assertTrue(kEvent.isConsumed)
        assertEquals(0, tree.leadSelectionRow)
        assertTrue(HelixToolWindowState.consumeInterceptedKey('k'))
    }

    fun testDispatchEnterPassesThrough() {
        val tree = Tree()
        val panel = JPanel()
        panel.putClientProperty("ToolWindow.id", "Project")
        panel.add(tree)

        val enterEvent = KeyEvent(tree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n')
        val handled = HelixToolWindowNavigator.dispatch(enterEvent)
        assertFalse(handled)
        assertFalse(enterEvent.isConsumed)
    }

    fun testDispatchEscapeResetsAndPassesThrough() {
        val tree = Tree()
        val panel = JPanel()
        panel.putClientProperty("ToolWindow.id", "Project")
        panel.add(tree)

        HelixToolWindowState.appendCountDigit(5)
        HelixToolWindowState.setPendingSequence("g")

        val escEvent = KeyEvent(
            tree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_ESCAPE,
            KeyEvent.CHAR_UNDEFINED,
        )
        val handled = HelixToolWindowNavigator.dispatch(escEvent)
        assertFalse(handled)
        assertFalse(escEvent.isConsumed)
        assertNull(HelixToolWindowState.getPendingCount())
        assertEquals("", HelixToolWindowState.getPendingSequence())
    }

    fun testListVerticalMovement() {
        val model = DefaultListModel<String>()
        model.addElement("Item 1")
        model.addElement("Item 2")
        model.addElement("Item 3")
        val list = JBList(model)
        list.selectedIndex = 0

        com.intellij.ui.ScrollingUtil.moveDown(list, 0)
        assertEquals(1, list.selectedIndex)

        com.intellij.ui.ScrollingUtil.moveDown(list, 0)
        assertEquals(2, list.selectedIndex)

        com.intellij.ui.ScrollingUtil.moveUp(list, 0)
        com.intellij.ui.ScrollingUtil.moveUp(list, 0)
        assertEquals(0, list.selectedIndex)
    }

    fun testTableHomeAndEnd() {
        val model = DefaultTableModel(arrayOf("Col"), 0)
        for (i in 1..10) {
            model.addRow(arrayOf("Row $i"))
        }
        val table = JBTable(model)
        table.setRowSelectionInterval(0, 0)
        assertEquals(0, table.selectedRow)

        com.intellij.ui.ScrollingUtil.moveEnd(table)
        assertEquals(9, table.selectedRow)

        com.intellij.ui.ScrollingUtil.moveHome(table)
        assertEquals(0, table.selectedRow)
    }

    fun testEstEventDispatcherPreventsSpeedSearchAndHandlesRollover() {
        com.intellij.testFramework.runInEdtAndWait {
            val root = DefaultMutableTreeNode("Root")
            root.add(DefaultMutableTreeNode("Alpha"))
            root.add(DefaultMutableTreeNode("Beta"))
            root.add(DefaultMutableTreeNode("Gamma"))
            val tree = Tree(DefaultTreeModel(root))
            val panel = JPanel()
            panel.putClientProperty("ToolWindow.id", "Project")
            panel.add(tree)
            com.intellij.ui.TreeSpeedSearch.installOn(tree)

            tree.setSelectionRow(0)
            assertEquals(0, tree.leadSelectionRow)

            val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()

            // Press 'j' with CHAR_UNDEFINED, then KEY_TYPED 'j'
            val jPressed = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_J,
                KeyEvent.CHAR_UNDEFINED,
            )
            assertTrue(dispatcher.dispatch(jPressed))
            assertTrue(jPressed.isConsumed)
            assertEquals(1, tree.leadSelectionRow)

            val jTyped = KeyEvent(tree, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'j')
            assertTrue(dispatcher.dispatch(jTyped))
            assertTrue(jTyped.isConsumed)
            if (!jTyped.isConsumed) tree.dispatchEvent(jTyped)
            val supply = com.intellij.ui.speedSearch.SpeedSearchSupply.getSupply(tree, true)
            assertNotNull(supply)
            assertFalse(supply!!.isPopupActive)
            assertNull(com.intellij.ui.speedSearch.SpeedSearchSupply.getSupply(tree))

            // Key rollover: press 'k' before releasing 'j'
            val kPressed = KeyEvent(tree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_K, 'k')
            assertTrue(dispatcher.dispatch(kPressed))
            assertTrue(kPressed.isConsumed)
            assertEquals(0, tree.leadSelectionRow)

            val jReleased = KeyEvent(
                tree,
                KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_J,
                KeyEvent.CHAR_UNDEFINED,
            )
            assertFalse(dispatcher.dispatch(jReleased))

            val kTyped = KeyEvent(tree, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'k')
            assertTrue(dispatcher.dispatch(kTyped))
            assertTrue(kTyped.isConsumed)
            if (!kTyped.isConsumed) tree.dispatchEvent(kTyped)
            assertFalse(supply.isPopupActive)

            // Unmapped letter 'x' should also be swallowed so SpeedSearch does not open
            val xPressed = KeyEvent(tree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_X, 'x')
            assertTrue(dispatcher.dispatch(xPressed))
            val xTyped = KeyEvent(tree, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'x')
            assertTrue(dispatcher.dispatch(xTyped))
            assertTrue(xTyped.isConsumed)
            assertFalse(supply.isPopupActive)
        }
    }

    fun testGgGeAndCountWithCharUndefined() {
        com.intellij.testFramework.runInEdtAndWait {
            val root = DefaultMutableTreeNode("Root")
            for (i in 1..4) {
                root.add(DefaultMutableTreeNode("Item $i"))
            }
            val tree = Tree(DefaultTreeModel(root))
            val panel = JPanel()
            panel.putClientProperty("ToolWindow.id", "Project")
            panel.add(tree)
            tree.setSelectionRow(0)

            val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()

            fun sendCharUndefinedKey(keyCode: Int, typedChar: Char) {
                val pressed = KeyEvent(
                    tree,
                    KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    keyCode,
                    KeyEvent.CHAR_UNDEFINED,
                )
                assertTrue(dispatcher.dispatch(pressed))
                assertTrue(pressed.isConsumed)

                val typed = KeyEvent(
                    tree,
                    KeyEvent.KEY_TYPED,
                    System.currentTimeMillis(),
                    0,
                    KeyEvent.VK_UNDEFINED,
                    typedChar,
                )
                assertTrue(dispatcher.dispatch(typed))
                assertTrue(typed.isConsumed)
            }

            // 2j -> row 2
            sendCharUndefinedKey(KeyEvent.VK_2, '2')
            sendCharUndefinedKey(KeyEvent.VK_J, 'j')
            assertEquals(2, tree.leadSelectionRow)

            // ge -> last row (4)
            sendCharUndefinedKey(KeyEvent.VK_G, 'g')
            sendCharUndefinedKey(KeyEvent.VK_E, 'e')
            assertEquals(4, tree.leadSelectionRow)

            // gg -> first row (0)
            sendCharUndefinedKey(KeyEvent.VK_G, 'g')
            sendCharUndefinedKey(KeyEvent.VK_G, 'g')
            assertEquals(0, tree.leadSelectionRow)
        }
    }
}
