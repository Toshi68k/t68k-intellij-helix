// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.editor

import com.intellij.find.EditorSearchSession
import com.intellij.find.SearchReplaceComponent
import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixReadAction
import jp.titze.intellij.helix.motion.HelixJumpToWord
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.toolwindow.HelixToolWindowSwitcher
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

object HelixEscapeHandler {

    fun handleEscape(editor: Editor): Boolean = HelixReadAction.runWriteIntent {
        if (HelixJumpToWord.cancel(editor)) {
            return@runWriteIntent true
        }

        val state = HelixStateManager.getOrCreate(editor)

        if (HelixWhichKeyPopup.cancelActive()) {
            HelixToolWindowSwitcher.clearActiveTargets()
            return@runWriteIntent true
        }
        if (HelixPromptBar.cancelActivePrompt(editor)) {
            return@runWriteIntent true
        }

        if (state.pendingSequence.isNotEmpty() || state.hasCount || state.selectedRegister != null) {
            if (state.pendingSequence == HelixToolWindowSwitcher.EDITOR_PREFIX) {
                HelixToolWindowSwitcher.clearActiveTargets()
            }
            state.clearPendingSequence()
            state.clearCount()
            state.clearSelectedRegister()
            return@runWriteIntent true
        }

        when (state.mode) {
            HelixMode.INSERT -> {
                HelixActions.enterNormalMode(editor)
                HelixMotions.collapseSelection(editor)
            }

            HelixMode.SELECT -> {
                HelixActions.enterNormalMode(editor)
            }

            HelixMode.NORMAL -> handleNormalEscape(editor)
        }
        true
    }

    private fun handleNormalEscape(editor: Editor) {
        if (editor.caretModel.caretCount == 1 && closeActiveEditorSearch(editor)) {
            HelixMotions.collapseSelection(editor)
            return
        }
        val hasSelection = editor.caretModel.allCarets.any { it.hasSelection() }
        if (hasSelection) {
            HelixMotions.collapseSelection(editor)
        } else if (editor.caretModel.caretCount > 1) {
            HelixMotions.keepOnlyPrimaryCaret(editor)
        } else {
            HelixMotions.collapseSelection(editor)
        }
    }

    private fun closeActiveEditorSearch(editor: Editor): Boolean {
        val hostEditor = HelixStateManager.resolveHostEditor(editor)
        val headerSearch = (hostEditor.headerComponent as? SearchReplaceComponent)?.searchSession
        val session = EditorSearchSession.get(hostEditor) ?: headerSearch ?: return false
        session.close()
        return true
    }
}
