package jp.titze.intellij.helix.action

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor

object HelixActionDelegate {

    @Volatile
    var actionExecutor: ((actionId: String, editor: Editor) -> Boolean)? = null

    fun executeAction(actionId: String, editor: Editor): Boolean {
        actionExecutor?.let { return it(actionId, editor) }
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(actionId) ?: return false

        val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)
        val event = AnActionEvent.createFromAnAction(
            action,
            null,
            ActionPlaces.EDITOR_POPUP,
            dataContext,
        )

        ApplicationManager.getApplication().invokeLater {
            ActionUtil.performActionDumbAwareWithCallbacks(action, event)
        }
        return true
    }
}
