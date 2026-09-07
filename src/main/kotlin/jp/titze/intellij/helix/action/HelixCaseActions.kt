package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

enum class HelixCaseTransformation {
    SWITCH_CASE,
    LOWERCASE,
    UPPERCASE,
}

object HelixCaseActions {

    fun toggleCase(editor: Editor, count: Int = 1) {
        transformCase(editor, HelixCaseTransformation.SWITCH_CASE, count)
    }

    fun toLowerCase(editor: Editor, count: Int = 1) {
        transformCase(editor, HelixCaseTransformation.LOWERCASE, count)
    }

    fun toUpperCase(editor: Editor, count: Int = 1) {
        transformCase(editor, HelixCaseTransformation.UPPERCASE, count)
    }

    fun transformCase(editor: Editor, transformation: HelixCaseTransformation, count: Int = 1) {
        val project = editor.project
        val doc = editor.document
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }

            for (caret in carets) {
                if (caret.hasSelection()) {
                    transformSelection(caret, doc, transformation)
                } else {
                    transformUnselected(caret, doc, transformation, count, isSelect)
                }
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun transformSelection(caret: Caret, doc: Document, transformation: HelixCaseTransformation) {
        val start = caret.selectionStart
        val end = caret.selectionEnd
        val originalText = doc.getText(TextRange(start, end))
        val transformed = applyTransformation(originalText, transformation)
        val isCursorAtStart = caret.offset == start

        doc.replaceString(start, end, transformed)
        val newEnd = start + transformed.length
        caret.setSelection(start, newEnd)
        if (isCursorAtStart) {
            caret.moveToOffset(start)
        } else {
            caret.moveToOffset(newEnd)
        }
    }

    private fun transformUnselected(
        caret: Caret,
        doc: Document,
        transformation: HelixCaseTransformation,
        count: Int,
        isSelect: Boolean,
    ) {
        val offset = caret.offset
        if (offset >= doc.textLength) return

        val lineNum = doc.getLineNumber(offset)
        val lineEnd = doc.getLineEndOffset(lineNum)
        val charCount = if (doc.charsSequence[offset] == '\n' || doc.charsSequence[offset] == '\r') {
            1
        } else {
            count.coerceAtLeast(1).coerceAtMost((lineEnd - offset).coerceAtLeast(1))
        }

        val end = (offset + charCount).coerceAtMost(doc.textLength)
        val originalText = doc.getText(TextRange(offset, end))
        val transformed = applyTransformation(originalText, transformation)

        doc.replaceString(offset, end, transformed)
        if (isSelect) {
            caret.setSelection(offset, offset + transformed.length)
            caret.moveToOffset(offset + transformed.length)
        } else {
            caret.removeSelection()
            caret.moveToOffset(offset)
        }
    }

    fun applyTransformation(text: String, transformation: HelixCaseTransformation): String = when (transformation) {
        HelixCaseTransformation.SWITCH_CASE -> switchCase(text)
        HelixCaseTransformation.LOWERCASE -> text.lowercase()
        HelixCaseTransformation.UPPERCASE -> text.uppercase()
    }

    private fun switchCase(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch.isUpperCase() -> sb.append(ch.lowercase())
                ch.isLowerCase() -> sb.append(ch.uppercase())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
