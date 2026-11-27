// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import java.awt.Container
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Handles VCS Commit and Version Control tool window shortcuts (`Space`/`s`, `d`, `c`).
 */
object HelixCommitToolWindowActions {

    @Volatile
    internal var actionExecutor: ((actionId: String, component: JComponent) -> Boolean)? = null

    fun isCommitToolWindow(toolWindowId: String): Boolean = toolWindowId == "Commit"

    fun isCommitOrVcsToolWindow(toolWindowId: String): Boolean =
        toolWindowId == "Commit" || toolWindowId == "Version Control"

    fun toggleStageOrCheckbox(
        component: JComponent,
        toolWindowId: String,
        allowNativePassthrough: Boolean = true,
    ): Boolean {
        if (!isCommitToolWindow(toolWindowId)) return false

        if (isGitStageTree(component)) {
            val actionId = if (isSelectionStaged(component as JTree)) "Git.Stage.Reset" else "Git.Stage.Add"
            if (executeToolWindowAction(actionId, component)) {
                return true
            }
        }

        // Changelist mode uses ChangesTree$MyToggleSelectionAction via IdeKeyEventDispatcher
        if (isChangesTreeWithCheckboxes(component)) {
            if (allowNativePassthrough) {
                return false
            }
            if (executeComponentSpaceToggleAction(component)) {
                return true
            }
        }

        HelixToolWindowNavigator.dispatchSyntheticKey(component, KeyEvent.VK_SPACE, ' ')
        return true
    }

    private fun executeComponentSpaceToggleAction(component: JComponent): Boolean {
        val actionManager = ActionManager.getInstance() ?: return false
        val spaceStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)
        val actions = ActionUtil.getActions(component)
        val toggleAction = actions.firstOrNull { isSpaceToggleAction(it, spaceStroke) } ?: return false
        val app = ApplicationManager.getApplication()
        if (app != null) {
            app.invokeLater {
                actionManager.tryToExecute(
                    toggleAction,
                    null,
                    component,
                    ActionPlaces.TOOLWINDOW_CONTENT,
                    true,
                )
            }
        } else {
            actionManager.tryToExecute(
                toggleAction,
                null,
                component,
                ActionPlaces.TOOLWINDOW_CONTENT,
                true,
            )
        }
        return true
    }

    private fun isSpaceToggleAction(action: AnAction, spaceStroke: KeyStroke): Boolean {
        if (action.javaClass.simpleName.contains("ToggleSelection", ignoreCase = true)) {
            return true
        }
        return action.shortcutSet.shortcuts.any { shortcut ->
            shortcut is KeyboardShortcut &&
                shortcut.firstKeyStroke == spaceStroke &&
                shortcut.secondKeyStroke == null
        }
    }

    fun openDiffPreview(component: JComponent, toolWindowId: String): Boolean {
        if (!isCommitOrVcsToolWindow(toolWindowId)) return false

        val diffActions = listOf("Diff.ShowDiff", "Compare.SameVersion", "Frontend.ChangesView.ShowDiff")
        for (actionId in diffActions) {
            if (executeToolWindowAction(actionId, component)) {
                return true
            }
        }
        return true
    }

    fun focusCommitMessage(component: JComponent, toolWindowId: String): Boolean {
        if (!isCommitToolWindow(toolWindowId)) return false

        val commitRoot = findCommitToolWindowRoot(component)
        val commitMessagePanel = commitRoot?.let { findCommitMessagePanel(it) }
        if (commitMessagePanel != null) {
            HelixToolWindowState.reset()
            if (!invokeRequestFocusInMessage(commitMessagePanel)) {
                focusTargetComponent(commitMessagePanel)
            }
            return true
        }

        val textInput = commitRoot?.let { findCommitMessageInput(it) }
        if (textInput != null) {
            HelixToolWindowState.reset()
            focusTargetComponent(textInput)
            return true
        }

        actionExecutor?.invoke("Checkin.FocusCommitMessage", component)
        return true
    }

    internal fun executeToolWindowAction(actionId: String, component: JComponent): Boolean {
        actionExecutor?.let { return it(actionId, component) }

        val actionManager = ActionManager.getInstance() ?: return false
        val action = actionManager.getAction(actionId) ?: return false

        val app = ApplicationManager.getApplication()
        if (app != null) {
            app.invokeLater {
                actionManager.tryToExecute(
                    action,
                    null,
                    component,
                    ActionPlaces.TOOLWINDOW_CONTENT,
                    true,
                )
            }
        } else {
            actionManager.tryToExecute(
                action,
                null,
                component,
                ActionPlaces.TOOLWINDOW_CONTENT,
                true,
            )
        }
        return true
    }

    internal fun isGitStageTree(component: JComponent): Boolean {
        if (component !is JTree) return false
        if (component.javaClass.name.contains("GitStage")) return true
        return isShowCheckboxesValue(component) == false
    }

    internal fun isChangesTreeWithCheckboxes(component: JComponent): Boolean {
        if (component !is JTree) return false
        return isShowCheckboxesValue(component) == true
    }

    private fun isShowCheckboxesValue(tree: JTree): Boolean? = try {
        val method = tree.javaClass.getMethod("isShowCheckboxes")
        method.invoke(tree) as? Boolean
    } catch (_: Throwable) {
        null
    }

    internal fun isSelectionStaged(tree: JTree): Boolean {
        val path = tree.selectionPath ?: return false
        for (segment in path.path) {
            val userObj = (segment as? DefaultMutableTreeNode)?.userObject ?: segment
            if (isStagedNodeObject(userObj)) return true
            if (isUnstagedNodeObject(userObj)) return false
        }
        return false
    }

    private fun isStagedNodeObject(userObj: Any?): Boolean {
        if (userObj == null) return false
        val kindName = extractKindName(userObj)
        if (kindName != null) {
            return kindName.equals("STAGED", ignoreCase = true)
        }
        val text = userObj.toString().trim()
        return text.equals("STAGED", ignoreCase = true) || text.equals("Staged", ignoreCase = true)
    }

    private fun isUnstagedNodeObject(userObj: Any?): Boolean {
        if (userObj == null) return false
        val kindName = extractKindName(userObj)
        if (kindName != null) {
            return kindName.equals("UNSTAGED", ignoreCase = true) ||
                kindName.equals("UNTRACKED", ignoreCase = true)
        }
        val text = userObj.toString().trim()
        return text.equals("UNSTAGED", ignoreCase = true) ||
            text.equals("UNTRACKED", ignoreCase = true)
    }

    private fun extractKindName(obj: Any): String? = try {
        val method = obj.javaClass.getMethod("getKind")
        method.invoke(obj)?.toString()
    } catch (_: Throwable) {
        null
    }

    private fun findCommitToolWindowRoot(component: JComponent): Container? {
        val project = HelixToolWindowGuard.findProjectForComponent(component)
        if (project != null && !project.isDisposed) {
            val tw = ToolWindowManager.getInstance(project).getToolWindow("Commit")
            val twComp = try {
                tw?.component
            } catch (_: Throwable) {
                null
            }
            if (twComp != null) return twComp
        }

        var current: Component? = component
        while (current != null) {
            if (current is JComponent) {
                val id = current.getClientProperty("ToolWindow.id") as? String
                    ?: current.getClientProperty("toolWindowId") as? String
                if (id == "Commit") return current
            }
            if (current.javaClass.name.contains("InternalDecorator") && current is Container) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun findCommitMessagePanel(root: Container): JComponent? {
        val queue = ArrayDeque<Component>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current is JComponent && current.javaClass.name.endsWith("CommitMessage")) {
                return current
            }
            if (current is Container) {
                for (i in 0 until current.componentCount) {
                    queue.addLast(current.getComponent(i))
                }
            }
        }
        return null
    }

    private fun findCommitMessageInput(root: Container): JComponent? {
        val queue = ArrayDeque<Component>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (isEligibleTextInput(current)) {
                return current as JComponent
            }
            if (current is Container) {
                for (i in 0 until current.componentCount) {
                    queue.addLast(current.getComponent(i))
                }
            }
        }
        return null
    }

    private fun isEligibleTextInput(component: Component): Boolean {
        if (component !is JComponent) return false
        if (!component.isFocusable || !component.isEnabled) return false
        val isEditor = component.javaClass.name.contains("EditorComponent")
        val isText = component is JTextComponent
        if (!isEditor && !isText) return false
        return !isInsideSearchField(component)
    }

    private fun isInsideSearchField(component: Component): Boolean {
        var parent: Component? = component.parent
        while (parent != null) {
            if (parent.javaClass.name.contains("SearchTextField")) return true
            parent = parent.parent
        }
        return false
    }

    private fun invokeRequestFocusInMessage(commitMessagePanel: JComponent): Boolean = try {
        val method = commitMessagePanel.javaClass.getMethod("requestFocusInMessage")
        method.invoke(commitMessagePanel)
        true
    } catch (_: Throwable) {
        false
    }

    private fun focusTargetComponent(target: JComponent) {
        val app = ApplicationManager.getApplication()
        val project = HelixToolWindowGuard.findProjectForComponent(target)
        if (app != null && project != null && !project.isDisposed) {
            IdeFocusManager.getInstance(project).requestFocus(target, true)
        } else {
            target.requestFocusInWindow()
        }
    }
}
