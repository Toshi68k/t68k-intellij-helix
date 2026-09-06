package jp.titze.intellij.helix.action

import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType

data class HelixCaretSnapshot(val offset: Int, val selectionStart: Int, val selectionEnd: Int)

object HelixCaretUtils {

    fun captureCarets(editor: Editor): List<HelixCaretSnapshot> = editor.caretModel.allCarets.map {
        HelixCaretSnapshot(
            offset = it.offset,
            selectionStart = if (it.hasSelection()) it.selectionStart else it.offset,
            selectionEnd = if (it.hasSelection()) it.selectionEnd else it.offset,
        )
    }

    fun restoreCarets(editor: Editor, snapshot: List<HelixCaretSnapshot>) {
        if (snapshot.isEmpty()) return
        val caretStates = snapshot.map {
            CaretState(
                editor.offsetToLogicalPosition(it.offset),
                editor.offsetToLogicalPosition(it.selectionStart),
                editor.offsetToLogicalPosition(it.selectionEnd),
            )
        }
        val success = try {
            editor.caretModel.setCaretsAndSelections(caretStates)
            true
        } catch (e: Exception) {
            false
        }
        if (!success || editor.caretModel.caretCount != snapshot.size) {
            editor.caretModel.removeSecondaryCarets()
            val primary = editor.caretModel.primaryCaret
            val first = snapshot.first()
            primary.moveToOffset(first.offset)
            primary.setSelection(first.selectionStart, first.selectionEnd)
            for (item in snapshot.drop(1)) {
                val caret = editor.caretModel.addCaret(editor.offsetToVisualPosition(item.offset))
                if (caret != null) {
                    caret.moveToOffset(item.offset)
                    caret.setSelection(item.selectionStart, item.selectionEnd)
                }
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    fun applyCarets(editor: Editor, matches: List<Pair<Int, Int>>) {
        if (matches.isEmpty()) return
        val caretStates = matches.map { (start, end) ->
            CaretState(
                editor.offsetToLogicalPosition(end),
                editor.offsetToLogicalPosition(start),
                editor.offsetToLogicalPosition(end),
            )
        }
        val success = try {
            editor.caretModel.setCaretsAndSelections(caretStates)
            true
        } catch (e: Exception) {
            false
        }
        if (!success || editor.caretModel.caretCount != matches.size) {
            editor.caretModel.removeSecondaryCarets()
            val primary = editor.caretModel.primaryCaret
            val first = matches.first()
            primary.moveToOffset(first.second)
            primary.setSelection(first.first, first.second)
            for (match in matches.drop(1)) {
                val caret = editor.caretModel.addCaret(editor.offsetToVisualPosition(match.second))
                if (caret != null) {
                    caret.moveToOffset(match.second)
                    caret.setSelection(match.first, match.second)
                }
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }
}
