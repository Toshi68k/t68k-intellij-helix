package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
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

            val doc = editor.document
            for (caret in carets) {
                if (caret.hasSelection()) {
                    var text = caret.selectedText ?: ""
                    val startLine = doc.getLineNumber(caret.selectionStart)
                    val endLine = doc.getLineNumber((caret.selectionEnd - 1).coerceAtLeast(0))
                    val isWholeLines = caret.selectionStart == doc.getLineStartOffset(startLine) &&
                        caret.selectionEnd == (
                            if (endLine + 1 < doc.lineCount) {
                                doc.getLineStartOffset(
                                    endLine + 1,
                                )
                            } else {
                                doc.getLineEndOffset(endLine)
                            }
                            )
                    if (isWholeLines && !text.endsWith("\n")) {
                        text += "\n"
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
        val doc = editor.document

        editor.caretModel.runForEachCaret { caret ->
            if (caret.hasSelection()) {
                var text = caret.selectedText ?: ""
                val startLine = doc.getLineNumber(caret.selectionStart)
                val endLine = doc.getLineNumber((caret.selectionEnd - 1).coerceAtLeast(0))
                val isWholeLines = caret.selectionStart == doc.getLineStartOffset(startLine) &&
                    caret.selectionEnd == (
                        if (endLine + 1 < doc.lineCount) {
                            doc.getLineStartOffset(
                                endLine + 1,
                            )
                        } else {
                            doc.getLineEndOffset(endLine)
                        }
                        )
                if (isWholeLines && !text.endsWith("\n")) {
                    text += "\n"
                }
                pieces.add(text)
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
        val rawClipboard = getClipboard() ?: return
        val textToPaste = rawClipboard.replace("\r\n", "\n").replace("\r", "\n")
        val isLinewise = textToPaste.endsWith("\n") || textToPaste.contains("\n")
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.offset }
            for (caret in carets) {
                if (isLinewise) {
                    val hasSel = caret.hasSelection()
                    val selStart = caret.selectionStart
                    val selEnd = caret.selectionEnd

                    val curLine = if (hasSel && caret.offset in selStart..selEnd) {
                        if (after) {
                            doc.getLineNumber((selEnd - 1).coerceAtLeast(0))
                        } else {
                            doc.getLineNumber(selStart)
                        }
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
                } else {
                    val offset = if (after) {
                        if (caret.hasSelection()) {
                            (caret.selectionEnd).coerceAtMost(doc.textLength)
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

    fun replaceChar(editor: Editor, replacement: Char, count: Int = 1) {
        val project = editor.project
        val doc = editor.document
        if (doc.textLength == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }

            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    val selectedText = doc.charsSequence.subSequence(start, end)
                    val sb = StringBuilder(selectedText.length)
                    val isReplacementNewline = replacement == '\n' || replacement == '\r'
                    for (i in 0 until selectedText.length) {
                        val c = selectedText[i]
                        val isCharNewline = c == '\n' || c == '\r'
                        if (isCharNewline && !isReplacementNewline) {
                            sb.append(c)
                        } else {
                            sb.append(replacement)
                        }
                    }
                    val replacedString = sb.toString()
                    doc.replaceString(start, end, replacedString)
                    caret.setSelection(start, start + replacedString.length)
                    caret.moveToOffset(start)
                } else {
                    val offset = caret.offset
                    if (offset < doc.textLength) {
                        val lineNum = doc.getLineNumber(offset)
                        val lineEnd = doc.getLineEndOffset(lineNum)
                        val replaceCount = if (doc.charsSequence[offset] == '\n' || doc.charsSequence[offset] == '\r') {
                            1
                        } else {
                            count.coerceAtLeast(1).coerceAtMost((lineEnd - offset).coerceAtLeast(1))
                        }
                        val sb = StringBuilder(replaceCount)
                        repeat(replaceCount) {
                            sb.append(replacement)
                        }
                        val replacedString = sb.toString()
                        val replaceEnd = (offset + replaceCount).coerceAtMost(doc.textLength)
                        doc.replaceString(offset, replaceEnd, replacedString)
                        if (isSelect) {
                            caret.setSelection(offset, offset + replacedString.length)
                        } else {
                            caret.removeSelection()
                        }
                        caret.moveToOffset(offset)
                    }
                }
            }
        }
    }

    fun joinLines(editor: Editor, count: Int = 1) {
        val project = editor.project
        val doc = editor.document
        if (doc.lineCount <= 1) return

        val state = HelixStateManager.getOrCreate(editor)

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.selectionStart }
            val processedLines = mutableSetOf<Int>()

            for (caret in carets) {
                val selStart = caret.selectionStart
                val selEnd = caret.selectionEnd
                val startLine = doc.getLineNumber(selStart)
                val endLine = if (selEnd > selStart) {
                    doc.getLineNumber((selEnd - 1).coerceAtLeast(0))
                } else {
                    startLine
                }

                val targetEndLine = if (startLine < endLine) {
                    endLine
                } else {
                    (startLine + count.coerceAtLeast(1)).coerceAtMost(doc.lineCount - 1)
                }

                if (targetEndLine <= startLine || !processedLines.add(startLine)) {
                    continue
                }

                val replaceStart = doc.getLineStartOffset(startLine)
                val replaceEnd = doc.getLineEndOffset(targetEndLine)

                val sb = StringBuilder()
                var joinOffsetInResult = -1

                for (lineIdx in startLine..targetEndLine) {
                    val lineStart = doc.getLineStartOffset(lineIdx)
                    val lineEnd = doc.getLineEndOffset(lineIdx)
                    val lineText = doc.getText(TextRange(lineStart, lineEnd))

                    if (lineIdx == startLine) {
                        val trimmedTrailing = lineText.trimEnd(' ', '\t', '\r')
                        sb.append(trimmedTrailing)
                        if (trimmedTrailing.isNotEmpty()) {
                            joinOffsetInResult = sb.length
                        }
                    } else {
                        val trimmed = lineText.trim(' ', '\t', '\r')
                        if (trimmed.isNotEmpty()) {
                            if (sb.isNotEmpty()) {
                                if (joinOffsetInResult == -1) {
                                    joinOffsetInResult = sb.length
                                }
                                sb.append(' ')
                            }
                            sb.append(trimmed)
                        }
                    }
                }

                val replacement = sb.toString()
                doc.replaceString(replaceStart, replaceEnd, replacement)

                val targetOffset = if (joinOffsetInResult != -1) {
                    (replaceStart + joinOffsetInResult).coerceAtMost(replaceStart + replacement.length)
                } else {
                    replaceStart
                }

                if (state.mode == HelixMode.SELECT) {
                    caret.setSelection(replaceStart, replaceStart + replacement.length)
                    caret.moveToOffset(replaceStart + replacement.length)
                } else {
                    caret.removeSelection()
                    caret.moveToOffset(targetOffset)
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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

    // -------------------------------------------------------------------------
    // Caret snapshot & manipulation delegates (for backward compatibility)
    // -------------------------------------------------------------------------

    fun captureCarets(editor: Editor): List<HelixCaretSnapshot> = HelixCaretUtils.captureCarets(editor)

    fun restoreCarets(editor: Editor, snapshot: List<HelixCaretSnapshot>) {
        HelixCaretUtils.restoreCarets(editor, snapshot)
    }

    fun applyCarets(editor: Editor, matches: List<Pair<Int, Int>>) {
        HelixCaretUtils.applyCarets(editor, matches)
    }

    // -------------------------------------------------------------------------
    // Regex action delegates (for backward compatibility)
    // -------------------------------------------------------------------------

    fun selectRegex(editor: Editor, pattern: String): Boolean = HelixRegexActions.selectRegex(editor, pattern)

    fun countRegexMatches(editor: Editor, pattern: String): Int = HelixRegexActions.countRegexMatches(editor, pattern)

    fun splitSelection(editor: Editor, pattern: String): Boolean = HelixRegexActions.splitSelection(editor, pattern)

    fun previewSelectRegex(editor: Editor, pattern: String, baseSnapshot: List<HelixCaretSnapshot>): Boolean =
        HelixRegexActions.previewSelectRegex(editor, pattern, baseSnapshot)

    fun previewSplitRegex(editor: Editor, pattern: String, baseSnapshot: List<HelixCaretSnapshot>): Boolean =
        HelixRegexActions.previewSplitRegex(editor, pattern, baseSnapshot)

    fun countRegexMatchesInSnapshot(editor: Editor, pattern: String, baseSnapshot: List<HelixCaretSnapshot>): Int =
        HelixRegexActions.countRegexMatchesInSnapshot(editor, pattern, baseSnapshot)

    fun countDocumentRegexMatches(editor: Editor, pattern: String): Int =
        HelixRegexActions.countDocumentRegexMatches(editor, pattern)

    // -------------------------------------------------------------------------
    // Search action delegates (for backward compatibility)
    // -------------------------------------------------------------------------

    var lastSearchPattern: String?
        get() = HelixSearchActions.lastSearchPattern
        set(value) {
            HelixSearchActions.lastSearchPattern = value
        }

    var lastSearchBackward: Boolean
        get() = HelixSearchActions.lastSearchBackward
        set(value) {
            HelixSearchActions.lastSearchBackward = value
        }

    fun search(
        editor: Editor,
        pattern: String,
        backward: Boolean = false,
        count: Int = 1,
        updateDirection: Boolean = true,
    ): Boolean = HelixSearchActions.search(editor, pattern, backward, count, updateDirection)

    fun searchNext(editor: Editor, count: Int = 1): Boolean = HelixSearchActions.searchNext(editor, count)

    fun searchPrev(editor: Editor, count: Int = 1): Boolean = HelixSearchActions.searchPrev(editor, count)

    fun searchSelection(editor: Editor): Boolean = HelixSearchActions.searchSelection(editor)

    fun previewSearch(
        editor: Editor,
        pattern: String,
        backward: Boolean = false,
        count: Int = 1,
        baseSnapshot: List<HelixCaretSnapshot>,
    ): Boolean = HelixSearchActions.previewSearch(editor, pattern, backward, count, baseSnapshot)

    private fun setClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    private fun getClipboard(): String? {
        val transferable = CopyPasteManager.getInstance().contents ?: return null
        return try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                transferable.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
