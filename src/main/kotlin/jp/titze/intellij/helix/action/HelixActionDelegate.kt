// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.Editor
import java.awt.event.InputEvent

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
}
