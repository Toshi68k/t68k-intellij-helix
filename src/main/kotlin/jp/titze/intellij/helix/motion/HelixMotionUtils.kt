package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor

internal object HelixMotionUtils {

    fun runForEachCaret(editor: Editor, action: (Caret) -> Unit) {
        editor.caretModel.runForEachCaret(action)
    }

    fun applyMotion(caret: Caret, anchor: Int, targetOffset: Int, isSelect: Boolean) {
        caret.moveToOffset(targetOffset)
        if (isSelect) {
            caret.setSelection(anchor, targetOffset)
        } else {
            caret.removeSelection()
        }
    }

    fun applySelectingMotion(caret: Caret, anchor: Int, targetOffset: Int) {
        caret.moveToOffset(targetOffset)
        if (anchor != targetOffset) {
            caret.setSelection(anchor, targetOffset)
        } else {
            caret.removeSelection()
        }
    }

    fun getLineEndWithNewline(doc: Document, line: Int): Int = if (line + 1 < doc.lineCount) {
        doc.getLineStartOffset(line + 1)
    } else {
        doc.getLineEndOffset(line)
    }

    fun isLineEnding(c: Char): Boolean = c == '\n' || c == '\r'

    fun isHorizontalWhitespace(c: Char): Boolean = c.isWhitespace() && !isLineEnding(c)
}
