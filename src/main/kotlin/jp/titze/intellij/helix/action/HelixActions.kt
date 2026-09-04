package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

object HelixActions {

    fun deleteSelection(editor: Editor, enterInsert: Boolean = false) {
        val project = editor.project
        val state = HelixStateManager.getOrCreate(editor)

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.selectionStart }
            val yankPieces = mutableListOf<String>()

            for (caret in carets) {
                if (caret.hasSelection()) {
                    val text = caret.selectedText ?: ""
                    yankPieces.add(text)
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    editor.document.deleteString(start, end)
                    caret.removeSelection()
                    caret.moveToOffset(start)
                } else {
                    val offset = caret.offset
                    if (offset < editor.document.textLength) {
                        val text = editor.document.getText(TextRange(offset, offset + 1))
                        yankPieces.add(text)
                        editor.document.deleteString(offset, offset + 1)
                        caret.moveToOffset(offset)
                    }
                }
            }

            if (yankPieces.isNotEmpty()) {
                val fullYank = yankPieces.reversed().joinToString("\n")
                state.yankRegister = fullYank
                setClipboard(fullYank)
            }
        }

        if (enterInsert) {
            state.setMode(HelixMode.INSERT)
        }
    }

    fun yankSelection(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        val pieces = mutableListOf<String>()

        editor.caretModel.runForEachCaret { caret ->
            if (caret.hasSelection()) {
                pieces.add(caret.selectedText ?: "")
            } else {
                val offset = caret.offset
                if (offset < editor.document.textLength) {
                    pieces.add(editor.document.getText(TextRange(offset, offset + 1)))
                }
            }
        }

        if (pieces.isNotEmpty()) {
            val yanked = pieces.joinToString("\n")
            state.yankRegister = yanked
            setClipboard(yanked)
        }
    }

    fun paste(editor: Editor, after: Boolean = true) {
        val project = editor.project
        val textToPaste = getClipboard() ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.offset }
            for (caret in carets) {
                val offset = if (after) {
                    (caret.selectionEnd).coerceAtMost(editor.document.textLength)
                } else {
                    caret.selectionStart
                }
                editor.document.insertString(offset, textToPaste)
                val newOffset = offset + textToPaste.length
                caret.removeSelection()
                caret.moveToOffset(newOffset)
            }
        }
    }

    fun replaceWithClipboard(editor: Editor) {
        val project = editor.project
        val textToPaste = getClipboard() ?: return

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

    fun enterInsert(editor: Editor) {
        editor.caretModel.runForEachCaret { it.removeSelection() }
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)
    }

    fun enterInsertAppend(editor: Editor) {
        val maxLen = editor.document.textLength
        editor.caretModel.runForEachCaret { caret ->
            val newOffset = if (caret.hasSelection()) {
                caret.selectionEnd
            } else {
                (caret.offset + 1).coerceAtMost(maxLen)
            }
            caret.removeSelection()
            caret.moveToOffset(newOffset)
        }
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)
    }

    fun enterInsertLineStart(editor: Editor) {
        val doc = editor.document
        val text = doc.charsSequence
        editor.caretModel.runForEachCaret { caret ->
            val line = doc.getLineNumber(caret.offset)
            val lineStart = doc.getLineStartOffset(line)
            val lineEnd = doc.getLineEndOffset(line)
            var target = lineStart
            while (target < lineEnd && text[target].isWhitespace() && text[target] != '\n') {
                target++
            }
            caret.removeSelection()
            caret.moveToOffset(target)
        }
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)
    }

    fun enterInsertLineEnd(editor: Editor) {
        val doc = editor.document
        editor.caretModel.runForEachCaret { caret ->
            val line = doc.getLineNumber(caret.offset)
            val lineEnd = doc.getLineEndOffset(line)
            caret.removeSelection()
            caret.moveToOffset(lineEnd)
        }
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)
    }

    fun toggleSelectMode(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode == HelixMode.SELECT) {
            state.setMode(HelixMode.NORMAL)
            editor.caretModel.runForEachCaret { it.removeSelection() }
        } else {
            state.setMode(HelixMode.SELECT)
        }
    }

    fun enterNormalMode(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.NORMAL)
        state.clearPendingSequence()
    }

    private fun setClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    private fun getClipboard(): String? {
        val transferable = CopyPasteManager.getInstance().contents ?: return null
        return try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                transferable.getTransferData(DataFlavor.stringFlavor) as? String
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
