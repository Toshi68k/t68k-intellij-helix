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
        val doc = editor.document
        val allCaretsWholeLines = editor.caretModel.allCarets.all { isCaretSelectingWholeLines(doc, it) }

        editor.caretModel.runForEachCaret { caret ->
            if (caret.hasSelection()) {
                var text = caret.selectedText ?: ""
                if (allCaretsWholeLines && !text.endsWith("\n")) {
                    text += "\n"
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
                isLinewise = allCaretsWholeLines,
                pieces = pieces,
                register = register,
            )
        }
    }

    fun paste(editor: Editor, after: Boolean = true, register: Char? = null) {
        val project = editor.project
        val entry = HelixRegisterManager.get(register, editor) ?: return
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val caretsAscending = editor.caretModel.allCarets.sortedBy {
                if (it.hasSelection()) it.selectionStart else it.offset
            }
            val usePieces = entry.pieces.isNotEmpty() && entry.pieces.size == caretsAscending.size

            if (usePieces) {
                val caretPieces = caretsAscending.mapIndexed { idx, caret ->
                    val cleanPiece = entry.pieces[idx].replace("\r\n", "\n").replace("\r", "\n")
                    caret to cleanPiece
                }
                val descendingPairs = caretPieces.sortedByDescending { (caret, _) ->
                    if (caret.hasSelection()) caret.selectionStart else caret.offset
                }
                for ((caret, piece) in descendingPairs) {
                    val isPieceLinewise = piece.endsWith("\n") || piece.contains("\n")
                    if (isPieceLinewise) {
                        pasteLinewise(doc, caret, piece, after)
                    } else {
                        pasteCharacterwise(doc, caret, piece, after)
                    }
                }
            } else {
                val rawText = entry.text
                val textToPaste = rawText.replace("\r\n", "\n").replace("\r", "\n")
                val isLinewise = entry.isLinewise || textToPaste.endsWith("\n") || textToPaste.contains("\n")
                val carets = editor.caretModel.allCarets.sortedByDescending {
                    if (it.hasSelection()) it.selectionStart else it.offset
                }
                for (caret in carets) {
                    if (isLinewise) {
                        pasteLinewise(doc, caret, textToPaste, after)
                    } else {
                        pasteCharacterwise(doc, caret, textToPaste, after)
                    }
                }
            }
        }
    }

    fun replaceWithRegister(editor: Editor, register: Char? = null) {
        val project = editor.project
        val entry = HelixRegisterManager.get(register, editor) ?: return
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val caretsAscending = editor.caretModel.allCarets.sortedBy {
                if (it.hasSelection()) it.selectionStart else it.offset
            }
            val usePieces = entry.pieces.isNotEmpty() && entry.pieces.size == caretsAscending.size

            if (usePieces) {
                val caretPieces = caretsAscending.mapIndexed { idx, caret ->
                    val cleanPiece = entry.pieces[idx].replace("\r\n", "\n").replace("\r", "\n")
                    caret to cleanPiece
                }
                val descendingPairs = caretPieces.sortedByDescending { (caret, _) ->
                    if (caret.hasSelection()) caret.selectionStart else caret.offset
                }
                for ((caret, piece) in descendingPairs) {
                    replaceCaretText(doc, caret, piece)
                }
            } else {
                val textToPaste = entry.text
                val carets = editor.caretModel.allCarets.sortedByDescending {
                    if (it.hasSelection()) it.selectionStart else it.offset
                }
                for (caret in carets) {
                    replaceCaretText(doc, caret, textToPaste)
                }
            }
        }
    }

    private fun replaceCaretText(doc: Document, caret: Caret, text: String) {
        if (caret.hasSelection()) {
            val start = caret.selectionStart
            val end = caret.selectionEnd
            doc.replaceString(start, end, text)
            caret.setSelection(start, start + text.length)
            caret.moveToOffset(start)
        } else {
            val offset = caret.offset
            if (offset < doc.textLength) {
                doc.replaceString(offset, offset + 1, text)
                caret.setSelection(offset, offset + text.length)
                caret.moveToOffset(offset)
            } else {
                doc.insertString(offset, text)
                caret.setSelection(offset, offset + text.length)
                caret.moveToOffset(offset)
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
