package jp.titze.intellij.helix.ui

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings

object HelixSearchManager {

    fun startSearch(editor: Editor, backward: Boolean = false, count: Int = 1) {
        val mode = HelixSettings.instance.searchUiMode
        if (mode == HelixSearchUiMode.POPUP) {
            HelixSearchPopup.show(editor, backward = backward, count = count)
        } else {
            val promptType = if (backward) HelixPromptType.RSEARCH else HelixPromptType.SEARCH
            HelixPromptBar.show(editor, promptType, count = count)
        }
    }

    fun startSelect(editor: Editor, isSplit: Boolean = false) {
        val mode = HelixSettings.instance.searchUiMode
        if (mode == HelixSearchUiMode.POPUP) {
            HelixSelectRegexPopup.show(editor, isSplit = isSplit)
        } else {
            val promptType = if (isSplit) HelixPromptType.SPLIT else HelixPromptType.SELECT
            HelixPromptBar.show(editor, promptType, count = 1)
        }
    }
}
