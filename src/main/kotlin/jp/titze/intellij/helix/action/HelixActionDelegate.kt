// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import java.awt.Component
import java.awt.event.InputEvent
import javax.swing.JComponent

object HelixActionDelegate {

    @Volatile
    var actionExecutor: ((actionId: String, editor: Editor) -> Boolean)? = null

    fun executeAction(actionId: String, editor: Editor, inputEvent: InputEvent? = null): Boolean {
        actionExecutor?.let { return it(actionId, editor) }
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(actionId) ?: return false

        actionManager.tryToExecute(
            action,
            inputEvent,
            editor.contentComponent,
            ActionPlaces.EDITOR_POPUP,
            true,
        )
        return true
    }

    /**
     * Executes an action registered directly on a Swing ancestor of [editor] (such as
     * `NextDifferenceAction` / `PrevDifferenceAction` in IntelliJ diff viewers, where the global
     * `NextDiff` / `PreviousDiff` IDs are `EmptyAction` placeholders).
     */
    fun executeComponentAction(
        actionId: String,
        editor: Editor,
        place: String = ActionPlaces.KEYBOARD_SHORTCUT,
    ): Boolean {
        actionExecutor?.let { return it(actionId, editor) }
        val actionManager = ActionManager.getInstance()
        val templateAction = actionManager.getAction(actionId) ?: return false
        val matched = findComponentAction(editor.contentComponent, templateAction) ?: return false

        actionManager.tryToExecute(
            matched,
            null,
            editor.contentComponent,
            place,
            true,
        )
        return true
    }

    private fun findComponentAction(startComponent: Component?, templateAction: AnAction): AnAction? {
        var current: Component? = startComponent
        while (current != null) {
            if (current is JComponent) {
                val match = ActionUtil.getActions(current).firstOrNull { action ->
                    matchesComponentAction(action, templateAction)
                }
                if (match != null) return match
            }
            current = current.parent
        }
        return null
    }

    private fun matchesComponentAction(action: AnAction, templateAction: AnAction): Boolean {
        if (action === templateAction || action.javaClass == templateAction.javaClass) return false
        val actionPresentation = action.templatePresentation
        val templatePresentation = templateAction.templatePresentation
        val templateText = templatePresentation.text
        return !templateText.isNullOrEmpty() &&
            actionPresentation.text == templateText &&
            actionPresentation.icon == templatePresentation.icon
    }
}
