package jp.titze.intellij.helix.editor

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.motion.HelixJumpToWord
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

object HelixEscapeHandler {

    fun handleEscape(editor: Editor): Boolean {
        if (HelixJumpToWord.cancel(editor)) {
            return true
        }

        val state = HelixStateManager.getOrCreate(editor)

        HelixWhichKeyPopup.hide()
        if (HelixPromptBar.cancelActivePrompt(editor)) {
            return true
        }

        if (state.pendingSequence.isNotEmpty() || state.hasCount || state.selectedRegister != null) {
            state.clearPendingSequence()
            state.clearCount()
            state.clearSelectedRegister()
            return true
        }

        when (state.mode) {
            HelixMode.INSERT -> {
                HelixActions.enterNormalMode(editor)
                HelixMotions.collapseSelection(editor)
            }

            HelixMode.SELECT -> {
                HelixActions.enterNormalMode(editor)
            }

            HelixMode.NORMAL -> {
                HelixMotions.collapseSelection(editor)
            }
        }
        return true
    }
}
