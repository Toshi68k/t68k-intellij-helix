// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.speedSearch.SpeedSearchSupply
import jp.titze.intellij.helix.settings.HelixSettings
import java.awt.Component
import java.awt.Dialog
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * Checks eligibility and safety guards for tool window navigation.
 * Every check fails open (returns false / null) to ensure IntelliJ's native behavior is never blocked unintentionally.
 */
object HelixToolWindowGuard {

    /**
     * Determines whether [eventComponent] or the current focus owner is eligible for Helix navigation.
     * Returns the target component (`JTree`, `JTable`, or `JList`) and the associated `toolWindowId`,
     * or `null` if navigation should NOT be active.
     */
    fun findEligibleTarget(
        eventComponent: Component?,
        settings: HelixSettings = HelixSettings.instance,
    ): Pair<JComponent, String>? {
        if (!settings.enabled || !settings.toolWindowNavigationEnabled) {
            return null
        }

        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (isTextInputComponent(focusOwner) || isTextInputComponent(eventComponent)) {
            return null
        }

        val navComponent = resolveNavComponent(eventComponent)
            ?: resolveNavComponent(focusOwner)
            ?: return null

        val speedSearch = SpeedSearchSupply.getSupply(navComponent)
        val isSpeedSearchActive = speedSearch != null && speedSearch.isPopupActive
        if (isComponentGuarded(navComponent, isSpeedSearchActive)) {
            if (isSpeedSearchActive) {
                rememberActiveSpeedSearchPrefix(navComponent, speedSearch)
            }
            return null
        }

        val toolWindowId = resolveToolWindowId(navComponent) ?: return null

        val isAllowed = settings.toolWindowEnabledTargets.contains(toolWindowId) ||
            settings.toolWindowOtherEnabled

        return if (isAllowed) Pair(navComponent, toolWindowId) else null
    }

    /**
     * Returns the active [SpeedSearchSupply], its owning component, and the enclosing tool window ID
     * when SpeedSearch is currently open inside an eligible tool window, or `null` otherwise.
     */
    fun findActiveSpeedSearchTarget(
        eventComponent: Component?,
        settings: HelixSettings = HelixSettings.instance,
    ): Triple<JComponent, String, SpeedSearchSupply>? {
        if (!settings.enabled || !settings.toolWindowNavigationEnabled) {
            return null
        }

        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (isTextInputComponent(focusOwner) || isTextInputComponent(eventComponent)) {
            return null
        }

        val navComponent = resolveNavComponent(eventComponent)
            ?: resolveNavComponent(focusOwner)
            ?: return null

        val speedSearch = SpeedSearchSupply.getSupply(navComponent) ?: return null
        val canHandlePopup = speedSearch.isPopupActive &&
            !isEditingComponent(navComponent) &&
            !isInsideDialogOrPopup(navComponent)
        if (!canHandlePopup) {
            return null
        }

        val toolWindowId = resolveToolWindowId(navComponent) ?: return null
        val isAllowed = settings.toolWindowEnabledTargets.contains(toolWindowId) ||
            settings.toolWindowOtherEnabled

        return if (isAllowed) Triple(navComponent, toolWindowId, speedSearch) else null
    }

    private fun rememberActiveSpeedSearchPrefix(navComponent: JComponent, speedSearch: SpeedSearchSupply?) {
        val prefix = speedSearch?.enteredPrefix
        if (prefix.isNullOrEmpty()) return
        val toolWindowId = resolveToolWindowId(navComponent) ?: return
        HelixToolWindowState.setLastSearchQuery(toolWindowId, prefix)
    }

    private fun isTextInputComponent(component: Component?): Boolean {
        if (component == null) return false
        if (component is JTextComponent) return true
        return component.javaClass.name.contains("EditorComponent")
    }

    private fun resolveNavComponent(component: Component?): JComponent? = when (component) {
        null -> null
        is JTree, is JTable, is JList<*> -> component as JComponent
        else -> findNavComponentAncestor(component)
    }

    private fun isEditingComponent(navComponent: JComponent): Boolean =
        (navComponent is JTree && navComponent.isEditing) ||
            (navComponent is JTable && navComponent.isEditing)

    private fun isComponentGuarded(navComponent: JComponent, isSpeedSearchActive: Boolean): Boolean {
        if (isEditingComponent(navComponent)) return true
        if (isSpeedSearchActive) return true
        return isInsideDialogOrPopup(navComponent)
    }

    /**
     * Resolves the enclosing ToolWindow ID using Swing hierarchy inspection and public [ToolWindowManager] APIs.
     * Intentionally avoids `DataManager.getDataContext` because `IdeEventQueue` preprocessors execute
     * outside a write-intent/read action in IntelliJ 2025.1+.
     */
    fun resolveToolWindowId(navComponent: JComponent): String? {
        val ancestorId = findToolWindowIdFromAncestors(navComponent)
        if (ancestorId != null) return ancestorId

        val project = findProjectForComponent(navComponent)
        if (project != null && !project.isDisposed) {
            val twManager = ToolWindowManager.getInstance(project)

            val activeId = twManager.activeToolWindowId
            if (activeId != null) {
                val activeTw = twManager.getToolWindow(activeId)
                if (activeTw != null && isInsideToolWindow(navComponent, activeTw)) {
                    return activeId
                }
            }

            val matchingId = twManager.toolWindowIds.firstOrNull { id ->
                val tw = twManager.getToolWindow(id)
                tw != null && tw.isVisible && isInsideToolWindow(navComponent, tw)
            }
            if (matchingId != null) return matchingId

            if (activeId != null && navComponent.isFocusOwner) {
                return activeId
            }
        }

        return null
    }

    internal fun findProjectForComponent(component: Component): Project? {
        val openProjects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        if (openProjects.isEmpty()) return null
        if (openProjects.size == 1) return openProjects.first()

        val window = SwingUtilities.getWindowAncestor(component)
        if (window != null) {
            val windowManager = WindowManager.getInstance()
            val ownerProject = openProjects.firstOrNull { project ->
                val frame = windowManager.getFrame(project)
                frame == window || isOwnedBy(window, frame)
            }
            if (ownerProject != null) return ownerProject
        }
        return openProjects.firstOrNull()
    }

    private fun isOwnedBy(window: Window, frame: Window?): Boolean {
        if (frame == null) return false
        var owner: Window? = window.owner
        while (owner != null) {
            if (owner == frame) return true
            owner = owner.owner
        }
        return false
    }

    private fun findNavComponentAncestor(component: Component): JComponent? {
        var p: Component? = component.parent
        while (p != null) {
            if (isTextInputComponent(p)) return null
            if (p is JTree || p is JTable || p is JList<*>) return p as JComponent
            p = p.parent
        }
        return null
    }

    internal var isDialogAncestor: (Component) -> Boolean = { component ->
        SwingUtilities.getWindowAncestor(component) is Dialog
    }

    private fun isInsideDialogOrPopup(component: Component): Boolean {
        if (isDialogAncestor(component)) {
            val window = SwingUtilities.getWindowAncestor(component)
            val isFloatingToolWindow = (window != null && window.javaClass.name.contains("FloatingDecorator")) ||
                findToolWindowIdFromAncestors(component) != null
            if (!isFloatingToolWindow) return true
        }
        var p: Component? = component
        while (p != null) {
            val name = p.javaClass.name
            if (name.contains("Popup") && !name.contains("ToolWindow")) {
                return true
            }
            p = p.parent
        }
        return false
    }

    private fun findToolWindowIdFromAncestors(component: Component): String? {
        var current: Component? = component
        while (current != null) {
            if (current is JComponent) {
                val id = current.getClientProperty("ToolWindow.id") as? String
                    ?: current.getClientProperty("toolWindowId") as? String
                if (id != null) return id
            }
            val className = current.javaClass.name
            if (className.contains("InternalDecorator")) {
                try {
                    val method = current.javaClass.getMethod("getToolWindowId")
                    val id = method.invoke(current) as? String
                    if (!id.isNullOrEmpty()) return id
                } catch (_: Throwable) {
                }
                try {
                    val method = current.javaClass.getMethod("getToolWindow")
                    val tw = method.invoke(current) as? ToolWindow
                    if (tw != null) return tw.id
                } catch (_: Throwable) {
                }
            }
            current = current.parent
        }
        return null
    }

    private fun isInsideToolWindow(component: Component, toolWindow: ToolWindow): Boolean {
        val twComponent = try {
            toolWindow.component
        } catch (_: Throwable) {
            null
        }
        if (twComponent != null && (
                twComponent == component || SwingUtilities.isDescendingFrom(
                    component,
                    twComponent,
                )
                )
        ) {
            return true
        }

        val contentManager = try {
            toolWindow.contentManagerIfCreated ?: toolWindow.contentManager
        } catch (_: Throwable) {
            null
        }
        if (contentManager != null) {
            for (content in contentManager.contents) {
                val contentComponent = content.component
                if (contentComponent == component || SwingUtilities.isDescendingFrom(component, contentComponent)) {
                    return true
                }
            }
        }

        return false
    }
}
