package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.editor.HelixInsertTracker
import jp.titze.intellij.helix.register.HelixRegisterManager

object HelixInsertActions {

    private enum class CharType {
        WORD,
        PUNCTUATION,
    }

    private fun getCharType(c: Char): CharType =
        if (c.isLetterOrDigit() || c == '_') CharType.WORD else CharType.PUNCTUATION

    private fun isHorizontalSpace(c: Char): Boolean = c == ' ' || c == '\t'

    fun deleteWordBackward(editor: Editor) {
        val project = editor.project ?: return
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }
            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    doc.deleteString(start, end)
                    caret.removeSelection()
                    caret.moveToOffset(start)
                } else {
                    deleteWordBackwardForCaret(doc, caret)
                }
            }
        }
    }

    private fun deleteWordBackwardForCaret(doc: Document, caret: Caret) {
        val offset = caret.offset
        val lineNum = doc.getLineNumber(offset)
        val lineStart = doc.getLineStartOffset(lineNum)
        if (offset > lineStart) {
            val deleteStart = findBackwardWordBoundary(doc.charsSequence, lineStart, offset)
            doc.deleteString(deleteStart, offset)
            caret.moveToOffset(deleteStart)
        }
    }

    private fun findBackwardWordBoundary(chars: CharSequence, lineStart: Int, offset: Int): Int {
        var i = offset - 1
        while (i >= lineStart && isHorizontalSpace(chars[i])) {
            i--
        }
        if (i < lineStart) {
            return lineStart
        }
        val type = getCharType(chars[i])
        while (i >= lineStart && !isHorizontalSpace(chars[i]) && getCharType(chars[i]) == type) {
            i--
        }
        return i + 1
    }

    fun deleteWordForward(editor: Editor) {
        val project = editor.project ?: return
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }
            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    doc.deleteString(start, end)
                    caret.removeSelection()
                    caret.moveToOffset(start)
                } else {
                    deleteWordForwardForCaret(doc, caret)
                }
            }
        }
    }

    private fun deleteWordForwardForCaret(doc: Document, caret: Caret) {
        val offset = caret.offset
        val lineNum = doc.getLineNumber(offset)
        val lineEnd = doc.getLineEndOffset(lineNum)
        if (offset < lineEnd) {
            val deleteEnd = findForwardWordBoundary(doc.charsSequence, offset, lineEnd)
            doc.deleteString(offset, deleteEnd)
        }
    }

    private fun findForwardWordBoundary(chars: CharSequence, offset: Int, lineEnd: Int): Int {
        var i = offset
        while (i < lineEnd && isHorizontalSpace(chars[i])) {
            i++
        }
        if (i >= lineEnd) {
            return lineEnd
        }
        val type = getCharType(chars[i])
        while (i < lineEnd && !isHorizontalSpace(chars[i]) && getCharType(chars[i]) == type) {
            i++
        }
        return i
    }

    fun killToLineStart(editor: Editor) {
        val project = editor.project ?: return
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }
            for (caret in carets) {
                killToLineStartForCaret(doc, caret)
            }
        }
    }

    private fun killToLineStartForCaret(doc: Document, caret: Caret) {
        val offset = if (caret.hasSelection()) caret.selectionEnd else caret.offset
        val lineNum = doc.getLineNumber(offset)
        val lineStart = doc.getLineStartOffset(lineNum)

        if (offset > lineStart) {
            val delStart = if (caret.hasSelection()) caret.selectionStart.coerceAtMost(lineStart) else lineStart
            doc.deleteString(delStart, offset)
            caret.removeSelection()
            caret.moveToOffset(delStart)
        } else if (caret.hasSelection()) {
            val start = caret.selectionStart
            val end = caret.selectionEnd
            doc.deleteString(start, end)
            caret.removeSelection()
            caret.moveToOffset(start)
        }
    }

    fun killToLineEnd(editor: Editor) {
        val project = editor.project ?: return
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }
            for (caret in carets) {
                killToLineEndForCaret(doc, caret)
            }
        }
    }

    private fun killToLineEndForCaret(doc: Document, caret: Caret) {
        if (caret.hasSelection()) {
            val start = caret.selectionStart
            val lineNum = doc.getLineNumber(caret.selectionEnd)
            val lineEnd = doc.getLineEndOffset(lineNum)
            doc.deleteString(start, lineEnd)
            caret.removeSelection()
            caret.moveToOffset(start)
        } else {
            val offset = caret.offset
            val lineNum = doc.getLineNumber(offset)
            val lineEnd = doc.getLineEndOffset(lineNum)
            if (offset < lineEnd) {
                doc.deleteString(offset, lineEnd)
            } else if (offset == lineEnd && offset < doc.textLength) {
                val isCrlf = offset + 1 < doc.textLength &&
                    doc.charsSequence[offset] == '\r' && doc.charsSequence[offset + 1] == '\n'
                val deleteCount = if (isCrlf) 2 else 1
                doc.deleteString(offset, offset + deleteCount)
            }
        }
    }

    fun insertRegister(editor: Editor, register: Char) {
        val entry = HelixRegisterManager.get(register, editor) ?: return
        val textToInsert = entry.text
        if (textToInsert.isEmpty()) return

        val project = editor.project ?: return
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }
            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    doc.replaceString(start, end, textToInsert)
                    caret.removeSelection()
                    caret.moveToOffset(start + textToInsert.length)
                } else {
                    val offset = caret.offset.coerceIn(0, doc.textLength)
                    doc.insertString(offset, textToInsert)
                    caret.moveToOffset(offset + textToInsert.length)
                }
            }
            HelixInsertTracker.recordText(textToInsert)
        }
    }
}
