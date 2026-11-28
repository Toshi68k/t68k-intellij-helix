// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixSurroundActions
import jp.titze.intellij.helix.action.HelixTextObjectActions
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixEditorState

internal object HelixMatchKeymap {

    fun handle(ch: Char, editor: Editor, state: HelixEditorState, count: Int? = null): Boolean {
        when (ch) {
            's', 'd', 'r' -> {
                if (!HelixEditorEligibility.ensureWritable(editor)) {
                    state.clearPendingSequence()
                    return true
                }
                state.appendKey(ch)
                return true
            }

            'a', 'i' -> {
                state.appendKey(ch)
                return true
            }

            'm' -> {
                state.clearPendingSequence()
                jp.titze.intellij.helix.motion.HelixMotionHistory.recordMatchBracket()
                return HelixMotions.matchBrackets(editor, count ?: 1)
            }

            else -> {
                state.clearPendingSequence()
                return false
            }
        }
    }

    fun handleSubmenu(prefix: String, ch: Char, editor: Editor, state: HelixEditorState): Boolean = when (prefix) {
        "ms" -> {
            state.clearPendingSequence()
            if (HelixEditorEligibility.ensureWritable(editor)) {
                HelixSurroundActions.surroundAdd(editor, ch)
            } else {
                true
            }
        }

        "md" -> {
            state.clearPendingSequence()
            if (HelixEditorEligibility.ensureWritable(editor)) {
                HelixSurroundActions.surroundDelete(editor, ch)
            } else {
                true
            }
        }

        "mr" -> {
            if (HelixEditorEligibility.ensureWritable(editor)) {
                state.appendKey(ch)
            } else {
                state.clearPendingSequence()
            }
            true
        }

        "ma" -> {
            state.clearPendingSequence()
            HelixTextObjectActions.selectTextObject(editor, inside = false, objectChar = ch)
        }

        "mi" -> {
            state.clearPendingSequence()
            HelixTextObjectActions.selectTextObject(editor, inside = true, objectChar = ch)
        }

        else -> {
            if (prefix.startsWith("mr") && prefix.length == 3) {
                val fromChar = prefix[2]
                state.clearPendingSequence()
                if (HelixEditorEligibility.ensureWritable(editor)) {
                    HelixSurroundActions.surroundReplace(editor, fromChar, ch)
                } else {
                    true
                }
            } else {
                state.clearPendingSequence()
                false
            }
        }
    }
}
