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
        HelixToolWindowState.clearSearchState()
        HelixSettings.instance.toolWindowNavigationEnabled = true
    }

    override fun tearDown() {
        HelixCommitToolWindowActions.actionExecutor = null
        HelixWindowFocusNavigator.toolWindowJumpHook = null
        HelixSettings.instance.toolWindowNavigationEnabled = false
        HelixSettings.instance.toolWindowOtherEnabled = false
        HelixSettings.instance.toolWindowSeamlessFocusJump = true
        HelixSettings.instance.toolWindowSearchConfirmOnEnter = true
        HelixToolWindowState.reset()
        HelixToolWindowState.clearSearchState()
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

    fun testCommitSpaceTogglesCheckboxAndSuppressesKeyTyped() {
        com.intellij.testFramework.runInEdtAndWait {
            val root = com.intellij.ui.CheckedTreeNode("Changes")
            val fileNode = com.intellij.ui.CheckedTreeNode("App.kt")
            fileNode.isChecked = false
            root.add(fileNode)
            val tree = object : com.intellij.ui.CheckboxTree(
                object : CheckboxTreeCellRenderer() {
                    override fun customizeRenderer(
                        tree: javax.swing.JTree?,
                        value: Any?,
                        selected: Boolean,
                        expanded: Boolean,
                        leaf: Boolean,
                        row: Int,
                        hasFocus: Boolean,
                    ) {
                        textRenderer.append(value?.toString() ?: "")
                    }
                },
                root,
            ) {}
            val panel = JPanel()
            panel.putClientProperty("ToolWindow.id", "Commit")
            panel.add(tree)
            tree.expandRow(0)
            tree.selectionPath = javax.swing.tree.TreePath(arrayOf(root, fileNode))

            val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()
            val spacePressed = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_SPACE,
                KeyEvent.CHAR_UNDEFINED,
            )
            assertTrue(dispatcher.dispatch(spacePressed))
            assertTrue(spacePressed.isConsumed)
            assertTrue(fileNode.isChecked)

            val spaceTyped = KeyEvent(
                tree,
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_UNDEFINED,
                ' ',
            )
            assertTrue(dispatcher.dispatch(spaceTyped))
            assertTrue(spaceTyped.isConsumed)
        }
    }

    fun testCommitSpaceStagesAndUnstagesInGitStageTree() {
        val root = DefaultMutableTreeNode("Root")
        val stagedGroup = DefaultMutableTreeNode("STAGED")
        val stagedFile = DefaultMutableTreeNode("StagedFile.kt")
        stagedGroup.add(stagedFile)
        val unstagedGroup = DefaultMutableTreeNode("UNSTAGED")
        val unstagedFile = DefaultMutableTreeNode("UnstagedFile.kt")
        unstagedGroup.add(unstagedFile)
        root.add(stagedGroup)
        root.add(unstagedGroup)

        val stageTree = object : Tree(DefaultTreeModel(root)) {
            private val showCheckboxesFlag = false

            @Suppress("unused")
            fun isShowCheckboxes(): Boolean = showCheckboxesFlag
        }
        stageTree.expandRow(0)
        stageTree.expandPath(javax.swing.tree.TreePath(arrayOf(root, stagedGroup)))
        stageTree.expandPath(javax.swing.tree.TreePath(arrayOf(root, unstagedGroup)))

        val panel = JPanel()
        panel.putClientProperty("ToolWindow.id", "Commit")
        panel.add(stageTree)

        val executed = mutableListOf<String>()
        HelixCommitToolWindowActions.actionExecutor = { actionId, _ ->
            executed.add(actionId)
            true
        }

        stageTree.selectionPath = javax.swing.tree.TreePath(arrayOf(root, unstagedGroup, unstagedFile))
        val spaceUnstaged = KeyEvent(
            stageTree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_SPACE,
            ' ',
        )
        assertTrue(HelixToolWindowNavigator.dispatch(spaceUnstaged))
        assertEquals(listOf("Git.Stage.Add"), executed)

        stageTree.selectionPath = javax.swing.tree.TreePath(arrayOf(root, stagedGroup, stagedFile))
        val spaceStaged = KeyEvent(
            stageTree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_SPACE,
            ' ',
        )
        assertTrue(HelixToolWindowNavigator.dispatch(spaceStaged))
        assertEquals(listOf("Git.Stage.Add", "Git.Stage.Reset"), executed)
    }

    fun testCommitSpacePassesThroughForChangesTreeWithCheckboxes() {
        val changesTree = object : Tree() {
            private val showCheckboxesFlag = true

            @Suppress("unused")
            fun isShowCheckboxes(): Boolean = showCheckboxesFlag
        }
        val panel = JPanel()
        panel.putClientProperty("ToolWindow.id", "Commit")
        panel.add(changesTree)

        val spaceEvent = KeyEvent(
            changesTree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_SPACE,
            ' ',
        )
        assertFalse(HelixToolWindowNavigator.dispatch(spaceEvent))
        assertFalse(spaceEvent.isConsumed)
    }

    fun testSpaceNotInterceptedOutsideCommitToolWindow() {
        val tree = Tree()
        val panel = JPanel()
        panel.putClientProperty("ToolWindow.id", "Project")
        panel.add(tree)

        val spaceEvent = KeyEvent(tree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ')
        assertFalse(HelixToolWindowNavigator.dispatch(spaceEvent))
        assertFalse(spaceEvent.isConsumed)
    }

    fun testCommitAndVcsDiffPreviewShortcut() {
        val executed = mutableListOf<String>()
        HelixCommitToolWindowActions.actionExecutor = { actionId, _ ->
            executed.add(actionId)
            true
        }

        val commitTree = Tree()
        val commitPanel = JPanel()
        commitPanel.putClientProperty("ToolWindow.id", "Commit")
        commitPanel.add(commitTree)

        val dCommit = KeyEvent(commitTree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_D, 'd')
        assertTrue(HelixToolWindowNavigator.dispatch(dCommit))
        assertEquals(listOf("Diff.ShowDiff"), executed)

        val vcsTree = Tree()
        val vcsPanel = JPanel()
        vcsPanel.putClientProperty("ToolWindow.id", "Version Control")
        vcsPanel.add(vcsTree)

        val dVcs = KeyEvent(vcsTree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_D, 'd')
        assertTrue(HelixToolWindowNavigator.dispatch(dVcs))
        assertEquals(listOf("Diff.ShowDiff", "Diff.ShowDiff"), executed)

        // 'd' in Project tool window is swallowed without invoking diff action
        val projectTree = Tree()
        val projectPanel = JPanel()
        projectPanel.putClientProperty("ToolWindow.id", "Project")
        projectPanel.add(projectTree)

        val dProject = KeyEvent(projectTree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_D, 'd')
        assertTrue(HelixToolWindowNavigator.dispatch(dProject))
        assertEquals(2, executed.size)
    }

    fun testCommitCFocusesMessageInput() {
        val commitPanel = JPanel()
        commitPanel.putClientProperty("ToolWindow.id", "Commit")
        val commitTree = Tree()
        val messageArea = javax.swing.JTextArea()
        commitPanel.add(commitTree)
        commitPanel.add(messageArea)

        HelixToolWindowState.appendCountDigit(3)
        val cEvent = KeyEvent(commitTree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_C, 'c')
        assertTrue(HelixToolWindowNavigator.dispatch(cEvent))
        assertTrue(cEvent.isConsumed)
        assertNull(HelixToolWindowState.getPendingCount())
    }

    fun testToolWindowCtrlWJumpChords() {
        val tree = Tree()
        val panel = JPanel()
        panel.putClientProperty("ToolWindow.id", "Project")
        panel.add(tree)

        val jumps = mutableListOf<Pair<FocusDirection, String>>()
        HelixWindowFocusNavigator.toolWindowJumpHook = { dir, id ->
            jumps.add(dir to id)
            true
        }

        val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()
        val ctrlW = KeyEvent(
            tree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_W,
            'w',
        )
        assertTrue(dispatcher.dispatch(ctrlW))
        assertTrue(ctrlW.isConsumed)
        assertEquals("C-w", HelixToolWindowState.getPendingSequence())

        // Release Ctrl and press 'l'
        val lPressed = KeyEvent(tree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_L, 'l')
        assertTrue(dispatcher.dispatch(lPressed))
        assertTrue(lPressed.isConsumed)
        assertEquals("", HelixToolWindowState.getPendingSequence())
        assertEquals(listOf(FocusDirection.RIGHT to "Project"), jumps)

        // Suppress subsequent KEY_TYPED 'l'
        val lTyped = KeyEvent(tree, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'l')
        assertTrue(dispatcher.dispatch(lTyped))
        assertTrue(lTyped.isConsumed)

        // Ctrl+w followed by Ctrl+k (holding Ctrl)
        assertTrue(dispatcher.dispatch(ctrlW))
        val ctrlK = KeyEvent(
            tree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_K,
            'k',
        )
        assertTrue(dispatcher.dispatch(ctrlK))
        assertEquals(listOf(FocusDirection.RIGHT to "Project", FocusDirection.UP to "Project"), jumps)
    }

    fun testToolWindowCtrlWDisabledWhenSeamlessJumpOff() {
        HelixSettings.instance.toolWindowSeamlessFocusJump = false
        val tree = Tree()
        val panel = JPanel()
        panel.putClientProperty("ToolWindow.id", "Project")
        panel.add(tree)

        val ctrlW = KeyEvent(
            tree,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_W,
            'w',
        )
        assertFalse(HelixToolWindowNavigator.dispatch(ctrlW))
        assertEquals("", HelixToolWindowState.getPendingSequence())
    }

    fun testSpatialNeighborGeometry2DSplits() {
        val left = java.awt.Rectangle(0, 0, 400, 600)
        val topRight = java.awt.Rectangle(400, 0, 400, 300)
        val bottomRight = java.awt.Rectangle(400, 300, 400, 300)
        val candidates = listOf(left, topRight, bottomRight)

        // From bottomRight: h -> left (0), k -> topRight (1), l -> null (right edge), j -> null (bottom edge)
        assertEquals(0, HelixWindowFocusNavigator.findBestNeighborIndex(bottomRight, candidates, FocusDirection.LEFT))
        assertEquals(1, HelixWindowFocusNavigator.findBestNeighborIndex(bottomRight, candidates, FocusDirection.UP))
        assertNull(HelixWindowFocusNavigator.findBestNeighborIndex(bottomRight, candidates, FocusDirection.RIGHT))
        assertNull(HelixWindowFocusNavigator.findBestNeighborIndex(bottomRight, candidates, FocusDirection.DOWN))

        // From left: h -> null (left edge), l -> topRight or bottomRight (both overlap Y; topRight is index 1)
        assertNull(HelixWindowFocusNavigator.findBestNeighborIndex(left, candidates, FocusDirection.LEFT))
        val rightNeighbor = HelixWindowFocusNavigator.findBestNeighborIndex(left, candidates, FocusDirection.RIGHT)
        assertTrue(rightNeighbor == 1 || rightNeighbor == 2)
    }

    fun testLeftAndRightToolWindowsReturnToMiddleEditor() {
        val leftCommit = SpatialPane(PaneKind.TOOL_WINDOW, java.awt.Rectangle(0, 50, 300, 850))
        val leftEditor = SpatialPane(PaneKind.EDITOR, java.awt.Rectangle(305, 50, 400, 850))
        val rightEditor = SpatialPane(PaneKind.EDITOR, java.awt.Rectangle(705, 50, 395, 850))
        val rightGradle = SpatialPane(PaneKind.TOOL_WINDOW, java.awt.Rectangle(1100, 50, 300, 850))

        // From Left Commit -> Ctrl+w l enters nearest middle editor split, never jumping across to Right Gradle
        val fromCommitCandidates = listOf(rightGradle, rightEditor, leftEditor)
        val commitRightIdx = HelixWindowFocusNavigator.findBestPaneIndex(
            current = leftCommit,
            candidates = fromCommitCandidates,
            direction = FocusDirection.RIGHT,
            currentAnchor = com.intellij.openapi.wm.ToolWindowAnchor.LEFT,
        )
        assertEquals(2, commitRightIdx)

        // From Right Gradle -> Ctrl+w h enters nearest middle editor split, never jumping across to Left Commit
        val fromGradleCandidates = listOf(leftCommit, leftEditor, rightEditor)
        val gradleLeftIdx = HelixWindowFocusNavigator.findBestPaneIndex(
            current = rightGradle,
            candidates = fromGradleCandidates,
            direction = FocusDirection.LEFT,
            currentAnchor = com.intellij.openapi.wm.ToolWindowAnchor.RIGHT,
        )
        assertEquals(2, gradleLeftIdx)
    }

    fun testToolWindowEditorPreferenceWithTallBottomToolWindowAndStackedSplits() {
        val leftTopProject = SpatialPane(PaneKind.TOOL_WINDOW, java.awt.Rectangle(0, 50, 300, 425))
        val leftBottomCommit = SpatialPane(PaneKind.TOOL_WINDOW, java.awt.Rectangle(0, 475, 300, 425))
        val shortTopEditor = SpatialPane(PaneKind.EDITOR, java.awt.Rectangle(310, 50, 800, 250))
        val tallBottomTerminal = SpatialPane(PaneKind.TOOL_WINDOW, java.awt.Rectangle(300, 300, 800, 600))
        val rightGradle = SpatialPane(PaneKind.TOOL_WINDOW, java.awt.Rectangle(1100, 50, 300, 850))

        // Stacked Left-Top Project -> Ctrl+w j moves to Left-Bottom Commit
        val projectDownCandidates = listOf(leftBottomCommit, shortTopEditor, tallBottomTerminal)
        assertEquals(
            0,
            HelixWindowFocusNavigator.findBestPaneIndex(
                current = leftTopProject,
                candidates = projectDownCandidates,
                direction = FocusDirection.DOWN,
                currentAnchor = com.intellij.openapi.wm.ToolWindowAnchor.LEFT,
            ),
        )

        // Stacked Left-Top Project -> Ctrl+w l prefers short top Editor over tall Bottom Terminal or Right Gradle
        val projectRightCandidates = listOf(rightGradle, tallBottomTerminal, shortTopEditor, leftBottomCommit)
        assertEquals(
            2,
            HelixWindowFocusNavigator.findBestPaneIndex(
                current = leftTopProject,
                candidates = projectRightCandidates,
                direction = FocusDirection.RIGHT,
                currentAnchor = com.intellij.openapi.wm.ToolWindowAnchor.LEFT,
            ),
        )

        // Left-Bottom Commit (purely below shortTopEditor in Y) -> Ctrl+w l moves into Bottom Terminal
        val commitRightCandidates = listOf(rightGradle, shortTopEditor, tallBottomTerminal)
        assertEquals(
            2,
            HelixWindowFocusNavigator.findBestPaneIndex(
                current = leftBottomCommit,
                candidates = commitRightCandidates,
                direction = FocusDirection.RIGHT,
                currentAnchor = com.intellij.openapi.wm.ToolWindowAnchor.LEFT,
            ),
        )
    }

    fun testJumpTargetEligibilityExcludesTerminalAndNonNavigableWindows() {
        val treeContainer = JPanel().apply { add(Tree()) }
        val textOnlyContainer = JPanel().apply { add(javax.swing.JTextArea()) }
        val settings = HelixSettings.instance

        // Enabled target with a visible JTree (e.g. Project / Commit) is eligible
        assertTrue(HelixWindowFocusNavigator.isToolWindowTargetEligible("Project", treeContainer, settings))
        assertTrue(HelixWindowFocusNavigator.isToolWindowTargetEligible("Commit", treeContainer, settings))

        // Enabled target with only a text component (no JTree/JTable/JList) is not eligible
        assertFalse(HelixWindowFocusNavigator.isToolWindowTargetEligible("Project", textOnlyContainer, settings))

        // Unlisted target (e.g. Gradle) is ineligible when toolWindowOtherEnabled=false, eligible with JTree when true
        assertFalse(HelixWindowFocusNavigator.isToolWindowTargetEligible("Gradle", treeContainer, settings))
        settings.toolWindowOtherEnabled = true
        assertTrue(HelixWindowFocusNavigator.isToolWindowTargetEligible("Gradle", treeContainer, settings))

        // Terminal is always excluded even when toolWindowOtherEnabled=true
        assertFalse(HelixWindowFocusNavigator.isToolWindowTargetEligible("Terminal", treeContainer, settings))
        assertFalse(HelixWindowFocusNavigator.isToolWindowTargetEligible("Terminal", textOnlyContainer, settings))
    }

    fun testSpeedSearchConfirmOnEnterAndNextPrevAfterClose() {
        com.intellij.testFramework.runInEdtAndWait {
            val root = DefaultMutableTreeNode("Root")
            root.add(DefaultMutableTreeNode("Alpha"))
            root.add(DefaultMutableTreeNode("BuildOne"))
            root.add(DefaultMutableTreeNode("Gamma"))
            root.add(DefaultMutableTreeNode("BuildTwo"))
            val tree = object : Tree(DefaultTreeModel(root)) {
                override fun isShowing(): Boolean = true
            }
            val panel = JPanel()
            panel.putClientProperty("ToolWindow.id", "Project")
            panel.add(tree)

            val supply = com.intellij.ui.TreeSpeedSearch.installOn(tree)
            tree.setSelectionRow(0)
            supply.showPopup("Build")
            assertTrue(supply.isPopupActive)
            assertEquals("Build", supply.enteredPrefix)

            val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()

            // First Enter confirms SpeedSearch: hides popup, remembers query, and consumes Enter
            val firstEnter = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_ENTER,
                '\n',
            )
            assertTrue(dispatcher.dispatch(firstEnter))
            assertTrue(firstEnter.isConsumed)
            assertFalse(supply.isPopupActive)
            assertEquals("Build", HelixToolWindowState.getLastSearchQuery("Project"))

            // Trailing KEY_TYPED '\n' is suppressed
            val firstEnterTyped = KeyEvent(
                tree,
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_UNDEFINED,
                '\n',
            )
            assertTrue(dispatcher.dispatch(firstEnterTyped))
            assertTrue(firstEnterTyped.isConsumed)

            // 'n' and 'N' continue navigating matches even after SpeedSearch popup closed
            val nPressed = KeyEvent(tree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_N, 'n')
            assertTrue(dispatcher.dispatch(nPressed))
            assertTrue(nPressed.isConsumed)
            assertEquals(4, tree.leadSelectionRow)

            val nTyped = KeyEvent(tree, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'n')
            assertTrue(dispatcher.dispatch(nTyped))
            assertTrue(nTyped.isConsumed)

            val shiftNPressed = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                KeyEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_N,
                'N',
            )
            assertTrue(dispatcher.dispatch(shiftNPressed))
            assertTrue(shiftNPressed.isConsumed)
            assertEquals(2, tree.leadSelectionRow)

            val shiftNTyped = KeyEvent(
                tree,
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                KeyEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_UNDEFINED,
                'N',
            )
            assertTrue(dispatcher.dispatch(shiftNTyped))
            assertTrue(shiftNTyped.isConsumed)

            // Second Enter passes through natively to execute/open the selected item
            val secondEnter = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_ENTER,
                '\n',
            )
            assertFalse(dispatcher.dispatch(secondEnter))
            assertFalse(secondEnter.isConsumed)
        }
    }

    fun testSpeedSearchEnterPassesThroughWhenConfirmDisabled() {
        com.intellij.testFramework.runInEdtAndWait {
            HelixSettings.instance.toolWindowSearchConfirmOnEnter = false

            val root = DefaultMutableTreeNode("Root")
            root.add(DefaultMutableTreeNode("BuildOne"))
            val tree = object : Tree(DefaultTreeModel(root)) {
                override fun isShowing(): Boolean = true
            }
            val panel = JPanel()
            panel.putClientProperty("ToolWindow.id", "Project")
            panel.add(tree)

            val supply = com.intellij.ui.TreeSpeedSearch.installOn(tree)
            supply.showPopup("Build")
            assertTrue(supply.isPopupActive)

            val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()
            val enterPressed = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_ENTER,
                '\n',
            )
            assertFalse(dispatcher.dispatch(enterPressed))
            assertFalse(enterPressed.isConsumed)
        }
    }

    fun testBackwardQuestionMarkSpeedSearchAndDirectionalNextPrev() {
        com.intellij.testFramework.runInEdtAndWait {
            val root = DefaultMutableTreeNode("Root")
            root.add(DefaultMutableTreeNode("BuildTop")) // row 1
            root.add(DefaultMutableTreeNode("Alpha")) // row 2
            root.add(DefaultMutableTreeNode("BuildMid")) // row 3
            root.add(DefaultMutableTreeNode("CursorStart")) // row 4
            root.add(DefaultMutableTreeNode("BuildBot")) // row 5
            val tree = object : Tree(DefaultTreeModel(root)) {
                override fun isShowing(): Boolean = true
            }
            val panel = JPanel()
            panel.putClientProperty("ToolWindow.id", "Project")
            panel.add(tree)

            val supply = com.intellij.ui.TreeSpeedSearch.installOn(tree)
            tree.setSelectionRow(4)
            assertEquals(4, tree.leadSelectionRow)

            val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()

            // Press '?' (Shift + /) from row 4 -> opens SpeedSearch in backward mode and suppresses KEY_TYPED '?'
            val questionPressed = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                KeyEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_SLASH,
                KeyEvent.CHAR_UNDEFINED,
            )
            assertTrue(dispatcher.dispatch(questionPressed))
            assertTrue(questionPressed.isConsumed)
            assertTrue(supply.isPopupActive)
            assertTrue(HelixToolWindowState.isLastSearchBackward("Project"))

            val questionTyped = KeyEvent(
                tree,
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                KeyEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_UNDEFINED,
                '?',
            )
            assertTrue(dispatcher.dispatch(questionTyped))
            assertTrue(questionTyped.isConsumed)

            // Simulate live SpeedSearch query update while '?' popup is open -> selects nearest match backward (row 3)
            val baseSupply = supply as com.intellij.ui.SpeedSearchBase<*>
            baseSupply.searchField!!.text = "B"
            baseSupply.findAndSelectElement("B")
            assertEquals("B", supply.enteredPrefix)
            assertEquals(3, tree.leadSelectionRow)

            // Confirm search with Enter
            val enterPressed = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_ENTER,
                '\n',
            )
            assertTrue(dispatcher.dispatch(enterPressed))
            assertFalse(supply.isPopupActive)
            assertEquals("B", HelixToolWindowState.getLastSearchQuery("Project"))

            val enterTyped = KeyEvent(
                tree,
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_UNDEFINED,
                '\n',
            )
            assertTrue(dispatcher.dispatch(enterTyped))

            // 'n' follows '?' backward direction -> row 1 (BuildTop)
            val nPressed = KeyEvent(tree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_N, 'n')
            assertTrue(dispatcher.dispatch(nPressed))
            assertEquals(1, tree.leadSelectionRow)
            assertTrue(
                dispatcher.dispatch(
                    KeyEvent(tree, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'n'),
                ),
            )

            // 'N' reverses '?' direction -> row 3 (BuildMid) then row 5 (BuildBot)
            val shiftNPressed = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                KeyEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_N,
                'N',
            )
            assertTrue(dispatcher.dispatch(shiftNPressed))
            assertEquals(3, tree.leadSelectionRow)
            assertTrue(
                dispatcher.dispatch(
                    KeyEvent(
                        tree,
                        KeyEvent.KEY_TYPED,
                        System.currentTimeMillis(),
                        KeyEvent.SHIFT_DOWN_MASK,
                        KeyEvent.VK_UNDEFINED,
                        'N',
                    ),
                ),
            )

            // Opening forward SpeedSearch with '/' resets search direction to forward
            val slashPressed = KeyEvent(
                tree,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_SLASH,
                '/',
            )
            assertTrue(dispatcher.dispatch(slashPressed))
            assertFalse(HelixToolWindowState.isLastSearchBackward("Project"))
            assertNull(HelixToolWindowState.getBackwardSearch(tree))
        }
    }
}
