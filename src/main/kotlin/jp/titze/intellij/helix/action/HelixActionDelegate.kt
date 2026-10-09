package jp.titze.intellij.helix.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.Editor

object HelixActionDelegate {

    @Volatile
    var actionExecutor: ((actionId: String, editor: Editor) -> Boolean)? = null

    fun executeAction(actionId: String, editor: Editor): Boolean {
        actionExecutor?.let { return it(actionId, editor) }
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(actionId) ?: return false

        actionManager.tryToExecute(
            action,
            null,
            editor.contentComponent,
            ActionPlaces.EDITOR_POPUP,
            true,
        )
        return true
    }
}
