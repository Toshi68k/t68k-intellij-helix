package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixSurroundActions
import jp.titze.intellij.helix.action.HelixTextObjectActions
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixEditorState

internal object HelixMatchKeymap {

    fun handle(ch: Char, editor: Editor, state: HelixEditorState, count: Int? = null): Boolean {
        when (ch) {
            's', 'd', 'r', 'a', 'i' -> {
                state.appendKey(ch)
                return true
            }

            'm' -> {
                state.clearPendingSequence()
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
            HelixSurroundActions.surroundAdd(editor, ch)
        }

        "md" -> {
            state.clearPendingSequence()
            HelixSurroundActions.surroundDelete(editor, ch)
        }

        "mr" -> {
            state.appendKey(ch)
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
                HelixSurroundActions.surroundReplace(editor, fromChar, ch)
            } else {
                state.clearPendingSequence()
                false
            }
        }
    }
}
