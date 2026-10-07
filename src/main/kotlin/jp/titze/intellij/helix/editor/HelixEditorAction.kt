package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import jp.titze.intellij.helix.state.HelixStateManager

/**
 * Base AnAction ensuring:
 * 1. Helix is active for the target editor according to [HelixEditorEligibility.isActive].
 * 2. If [requiredMode] is specified, editor's [HelixStateManager.get] matches it (or non-insertable when null).
 * 3. Read-only state check on BGT without modifying editor user data or settings off-EDT.
 */
abstract class HelixEditorAction(private val requireInsertable: Boolean? = null) : AnAction() {

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (!HelixEditorEligibility.isActive(editor)) {
            e.presentation.isEnabled = false
            return
        }

        if (requireInsertable == null) {
            e.presentation.isEnabled = true
            return
        }

        val state = editor?.let { HelixStateManager.get(it) }
        val isInsertable = state?.mode?.isInsertable ?: false
        e.presentation.isEnabled = isInsertable == requireInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
