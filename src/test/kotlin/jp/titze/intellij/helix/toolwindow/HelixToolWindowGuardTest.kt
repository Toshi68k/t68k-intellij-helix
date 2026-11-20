// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.treeStructure.Tree
import jp.titze.intellij.helix.settings.HelixSettings
import javax.swing.JPanel
import javax.swing.JTextField

class HelixToolWindowGuardTest : BasePlatformTestCase() {

    fun testDisabledWhenMasterDisabled() {
        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = false

        val tree = Tree()
        val result = HelixToolWindowGuard.findEligibleTarget(tree, settings)
        assertNull(result)
    }

    fun testDisabledForTextFields() {
        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true

        val textField = JTextField("test")
        val result = HelixToolWindowGuard.findEligibleTarget(textField, settings)
        assertNull(result)
    }

    fun testEligibleTargetFiltering() {
        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true
        settings.toolWindowEnabledTargets = setOf("Project")
        settings.toolWindowOtherEnabled = false

        // Not a nav component (e.g. general JPanel)
        val panel = JPanel()
        assertNull(HelixToolWindowGuard.findEligibleTarget(panel, settings))
    }

    fun testResolveToolWindowIdFromAncestorClientProperty() {
        val parent = JPanel()
        parent.putClientProperty("ToolWindow.id", "Project")
        val tree = Tree()
        parent.add(tree)

        val id = HelixToolWindowGuard.resolveToolWindowId(tree)
        assertEquals("Project", id)

        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true
        settings.toolWindowEnabledTargets = setOf("Project")

        val target = HelixToolWindowGuard.findEligibleTarget(tree, settings)
        assertNotNull(target)
        assertEquals(tree, target?.first)
        assertEquals("Project", target?.second)
    }

    fun testIneligibleWhenToolWindowDisallowed() {
        val parent = JPanel()
        parent.putClientProperty("ToolWindow.id", "Services")
        val tree = Tree()
        parent.add(tree)

        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true
        settings.toolWindowEnabledTargets = setOf("Project")
        settings.toolWindowOtherEnabled = false

        val target = HelixToolWindowGuard.findEligibleTarget(tree, settings)
        assertNull(target)
    }

    fun testEligibleWhenOtherAllowed() {
        val parent = JPanel()
        parent.putClientProperty("ToolWindow.id", "Services")
        val tree = Tree()
        parent.add(tree)

        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true
        settings.toolWindowEnabledTargets = setOf("Project")
        settings.toolWindowOtherEnabled = true

        val target = HelixToolWindowGuard.findEligibleTarget(tree, settings)
        assertNotNull(target)
        assertEquals("Services", target?.second)
    }

    fun testDisabledForTextFieldInsideNavComponent() {
        val parent = JPanel()
        parent.putClientProperty("ToolWindow.id", "Project")
        val tree = Tree()
        val inlineField = JTextField("rename")
        tree.add(inlineField)
        parent.add(tree)

        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true
        settings.toolWindowEnabledTargets = setOf("Project")

        assertNull(HelixToolWindowGuard.findEligibleTarget(inlineField, settings))
    }

    private class FakeEditorComponent : JPanel()

    fun testDisabledForEditorComponentClass() {
        val parent = JPanel()
        parent.putClientProperty("ToolWindow.id", "Version Control")
        val editorComp = FakeEditorComponent()
        parent.add(editorComp)

        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true
        settings.toolWindowEnabledTargets = setOf("Version Control")

        assertNull(HelixToolWindowGuard.findEligibleTarget(editorComp, settings))
    }

    fun testNavComponentAncestorLookup() {
        val parent = JPanel()
        parent.putClientProperty("ToolWindow.id", "Project")
        val tree = Tree()
        val childPanel = JPanel()
        tree.add(childPanel)
        parent.add(tree)

        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true
        settings.toolWindowEnabledTargets = setOf("Project")

        val target = HelixToolWindowGuard.findEligibleTarget(childPanel, settings)
        assertNotNull(target)
        assertEquals(tree, target?.first)
        assertEquals("Project", target?.second)
    }

    fun testGuardedInsidePlainDialogButAllowedInFloatingToolWindow() {
        val settings = HelixSettings()
        settings.toolWindowNavigationEnabled = true
        settings.toolWindowEnabledTargets = setOf("Project")

        val plainDialog = javax.swing.JDialog()
        val dialogTree = Tree()
        plainDialog.contentPane.add(dialogTree)
        assertNull(HelixToolWindowGuard.findEligibleTarget(dialogTree, settings))
        plainDialog.dispose()

        val floatingDialog = javax.swing.JDialog()
        val twContainer = JPanel()
        twContainer.putClientProperty("ToolWindow.id", "Project")
        val floatingTree = Tree()
        twContainer.add(floatingTree)
        floatingDialog.contentPane.add(twContainer)

        val target = HelixToolWindowGuard.findEligibleTarget(floatingTree, settings)
        assertNotNull(target)
        assertEquals(floatingTree, target?.first)
        assertEquals("Project", target?.second)
        floatingDialog.dispose()
    }
}
