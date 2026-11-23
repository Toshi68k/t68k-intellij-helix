// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.harpoon.HelixHarpoonService
import jp.titze.intellij.helix.state.HelixEditorState
import jp.titze.intellij.helix.ui.HelixHarpoonPopup
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

internal object HelixHarpoonKeymap {

    fun handle(ch: Char, editor: Editor, state: HelixEditorState): Boolean {
        val project = editor.project ?: return false
        val service = HelixHarpoonService.getInstance(project)

        return when (ch) {
            in '1'..'5' -> {
                state.clearPendingSequence()
                service.jumpToSlot(ch - '1', editor)
            }

            'a' -> {
                state.clearPendingSequence()
                service.pinCurrent(editor) != null
            }

            's', 'm' -> {
                state.setPendingSequence(" H s")
                HelixWhichKeyPopup.show(editor, " H s")
                true
            }

            'd' -> {
                state.setPendingSequence(" H d")
                HelixWhichKeyPopup.show(editor, " H d")
                true
            }

            'H', ' ' -> {
                state.clearPendingSequence()
                HelixHarpoonPopup.show(editor)
                true
            }

            else -> {
                state.clearPendingSequence()
                false
            }
        }
    }

    fun handleSubmenu(prefix: String, ch: Char, editor: Editor, state: HelixEditorState): Boolean {
        val project = editor.project ?: return false
        val service = HelixHarpoonService.getInstance(project)

        return when (prefix) {
            " H s", " H m" -> {
                state.clearPendingSequence()
                if (ch in '1'..'5') {
                    service.setSlot(ch - '1', editor)
                } else {
                    false
                }
            }

            " H d" -> {
                state.clearPendingSequence()
                if (ch in '1'..'5') {
                    service.clearSlot(ch - '1')
                } else {
                    false
                }
            }

            else -> {
                state.clearPendingSequence()
                false
            }
        }
    }
}
