package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixRegisterActions {

    fun deleteSelection(editor: Editor, enterInsert: Boolean = false, count: Int = 1, register: Char? = null) {
        val project = editor.project
        val state = HelixStateManager.getOrCreate(editor)

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.selectionStart }
            val yankPieces = mutableListOf<String>()
            var hasWholeLines = false

            val doc = editor.document
            for (caret in carets) {
                if (caret.hasSelection()) {
                    var text = caret.selectedText ?: ""
                    val isWholeLines = isCaretSelectingWholeLines(doc, caret)
                    if (isWholeLines) {
                        hasWholeLines = true
                        if (!text.endsWith("\n")) {
                            text += "\n"
                        }
                    }
                    yankPieces.add(text)
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    editor.document.deleteString(start, end)
                    caret.removeSelection()
                    caret.moveToOffset(start)
                } else {
                    val offset = caret.offset
                    if (offset < editor.document.textLength) {
                        val endOffset = (offset + count).coerceAtMost(editor.document.textLength)
                        val text = editor.document.getText(TextRange(offset, endOffset))
                        yankPieces.add(text)
                        editor.document.deleteString(offset, endOffset)
                        caret.moveToOffset(offset)
                    }
                }
            }

            if (yankPieces.isNotEmpty()) {
                val pieces = yankPieces.reversed()
                val fullYank = pieces.joinToString("\n")
                state.yankRegister = fullYank
                HelixRegisterManager.recordDelete(
                    text = fullYank,
                    isLinewise = hasWholeLines,
                    pieces = pieces,
                    register = register,
                )
            }
        }

        if (enterInsert) {
            state.setMode(HelixMode.INSERT)
        }
    }

    fun yankSelection(editor: Editor, count: Int = 1, register: Char? = null) {
        val state = HelixStateManager.getOrCreate(editor)
        val pieces = mutableListOf<String>()
        var hasWholeLines = false
        val doc = editor.document

        editor.caretModel.runForEachCaret { caret ->
            if (caret.hasSelection()) {
                var text = caret.selectedText ?: ""
                val isWholeLines = isCaretSelectingWholeLines(doc, caret)
                if (isWholeLines) {
                    hasWholeLines = true
                    if (!text.endsWith("\n")) {
                        text += "\n"
                    }
                }
                pieces.add(text)
            } else {
                val offset = caret.offset
                if (offset < editor.document.textLength) {
                    val endOffset = (offset + count).coerceAtMost(editor.document.textLength)
                    pieces.add(editor.document.getText(TextRange(offset, endOffset)))
                }
            }
        }

        if (pieces.isNotEmpty()) {
            val yanked = pieces.joinToString("\n")
            state.yankRegister = yanked
            HelixRegisterManager.recordYank(
                text = yanked,
                isLinewise = hasWholeLines,
                pieces = pieces,
                register = register,
            )
        }
    }

    fun paste(editor: Editor, after: Boolean = true, register: Char? = null) {
        val project = editor.project
        val entry = HelixRegisterManager.get(register, editor) ?: return
        val rawText = entry.text
        val textToPaste = rawText.replace("\r\n", "\n").replace("\r", "\n")
        val isLinewise = entry.isLinewise || textToPaste.endsWith("\n") || textToPaste.contains("\n")
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.offset }
            for (caret in carets) {
                if (isLinewise) {
                    pasteLinewise(doc, caret, textToPaste, after)
                } else {
                    pasteCharacterwise(doc, caret, textToPaste, after)
                }
            }
        }
    }

    fun replaceWithRegister(editor: Editor, register: Char? = null) {
        val project = editor.project
        val entry = HelixRegisterManager.get(register, editor) ?: return
        val textToPaste = entry.text

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.offset }
            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    editor.document.replaceString(start, end, textToPaste)
                    caret.setSelection(start, start + textToPaste.length)
                    caret.moveToOffset(start)
                } else {
                    val offset = caret.offset
                    if (offset < editor.document.textLength) {
                        editor.document.replaceString(offset, offset + 1, textToPaste)
                        caret.setSelection(offset, offset + textToPaste.length)
                        caret.moveToOffset(offset)
                    } else {
                        editor.document.insertString(offset, textToPaste)
                        caret.setSelection(offset, offset + textToPaste.length)
                        caret.moveToOffset(offset)
                    }
                }
            }
        }
    }

    private fun isCaretSelectingWholeLines(doc: Document, caret: Caret): Boolean {
        val startLine = doc.getLineNumber(caret.selectionStart)
        val endLine = doc.getLineNumber((caret.selectionEnd - 1).coerceAtLeast(0))
        val targetEndOffset = if (endLine + 1 < doc.lineCount) {
            doc.getLineStartOffset(endLine + 1)
        } else {
            doc.getLineEndOffset(endLine)
        }
        return caret.selectionStart == doc.getLineStartOffset(startLine) && caret.selectionEnd == targetEndOffset
    }

    private fun pasteLinewise(doc: Document, caret: Caret, textToPaste: String, after: Boolean) {
        val hasSel = caret.hasSelection()
        val selStart = caret.selectionStart
        val selEnd = caret.selectionEnd

        val curLine = if (hasSel && caret.offset in selStart..selEnd) {
            if (after) doc.getLineNumber((selEnd - 1).coerceAtLeast(0)) else doc.getLineNumber(selStart)
        } else {
            doc.getLineNumber(caret.offset.coerceIn(0, doc.textLength))
        }

        if (after) {
            if (doc.textLength == 0) {
                val content = if (textToPaste.endsWith("\n")) textToPaste else textToPaste + "\n"
                doc.insertString(0, content)
                caret.removeSelection()
                caret.moveToOffset(0)
            } else if (curLine < doc.lineCount - 1) {
                val insertOffset = doc.getLineStartOffset(curLine + 1)
                val content = if (textToPaste.endsWith("\n")) textToPaste else textToPaste + "\n"
                doc.insertString(insertOffset, content)
                caret.removeSelection()
                caret.moveToOffset(insertOffset)
            } else {
                val lineEndOffset = doc.getLineEndOffset(curLine)
                val cleanText = textToPaste.removeSuffix("\n")
                val content = "\n" + cleanText
                doc.insertString(lineEndOffset, content)
                caret.removeSelection()
                caret.moveToOffset(lineEndOffset + 1)
            }
        } else {
            val insertOffset = doc.getLineStartOffset(curLine)
            val content = if (textToPaste.endsWith("\n")) textToPaste else textToPaste + "\n"
            doc.insertString(insertOffset, content)
            caret.removeSelection()
            caret.moveToOffset(insertOffset)
        }
    }

    private fun pasteCharacterwise(doc: Document, caret: Caret, textToPaste: String, after: Boolean) {
        val offset = if (after) {
            if (caret.hasSelection()) {
                caret.selectionEnd.coerceAtMost(doc.textLength)
            } else {
                val lineEnd = doc.getLineEndOffset(doc.getLineNumber(caret.offset))
                (caret.offset + 1).coerceAtMost(lineEnd)
            }
        } else {
            caret.selectionStart
        }
        doc.insertString(offset, textToPaste)
        val newOffset = offset + textToPaste.length
        caret.removeSelection()
        caret.moveToOffset(newOffset)
    }
}
