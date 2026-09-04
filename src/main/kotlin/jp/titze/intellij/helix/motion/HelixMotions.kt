package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixMotions {

    private enum class CharType {
        WORD, PUNCTUATION, WHITESPACE
    }

    private fun getCharType(c: Char): CharType {
        return when {
            c.isWhitespace() -> CharType.WHITESPACE
            c.isLetterOrDigit() || c == '_' -> CharType.WORD
            else -> CharType.PUNCTUATION
        }
    }

    private fun runForEachCaret(editor: Editor, action: (Caret) -> Unit) {
        editor.caretModel.runForEachCaret(action)
    }

    /**
     * w: Move forward to start of next word
     */
    fun moveNextWordStart(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        runForEachCaret(editor) { caret ->
            repeat(count) {
                val currentOffset = caret.offset
                val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else currentOffset
                var offset = currentOffset

                if (offset < textLen) {
                    val initialType = getCharType(text[offset])
                    if (initialType != CharType.WHITESPACE) {
                        // Skip current group
                        while (offset < textLen && getCharType(text[offset]) == initialType) {
                            offset++
                        }
                    }
                    // Skip whitespace
                    while (offset < textLen && text[offset].isWhitespace()) {
                        offset++
                    }
                }

                offset = offset.coerceIn(0, textLen)
                applyMotion(caret, anchor, offset, isSelect)
            }
        }
    }

    /**
     * b: Move backward to start of previous word
     */
    fun movePrevWordStart(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        runForEachCaret(editor) { caret ->
            repeat(count) {
                val currentOffset = caret.offset
                val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else currentOffset
                var offset = currentOffset

                if (offset > 0) {
                    offset--
                    // Skip whitespace backwards
                    while (offset > 0 && text[offset].isWhitespace()) {
                        offset--
                    }
                    val targetType = getCharType(text[offset])
                    while (offset > 0 && getCharType(text[offset - 1]) == targetType) {
                        offset--
                    }
                }

                offset = offset.coerceIn(0, textLen)
                applyMotion(caret, anchor, offset, isSelect)
            }
        }
    }

    /**
     * e: Move forward to end of current/next word
     */
    fun moveWordEnd(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        runForEachCaret(editor) { caret ->
            repeat(count) {
                val currentOffset = caret.offset
                val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else currentOffset
                var offset = currentOffset

                if (offset < textLen) {
                    offset++
                    // Skip whitespace
                    while (offset < textLen && text[offset].isWhitespace()) {
                        offset++
                    }
                    if (offset < textLen) {
                        val targetType = getCharType(text[offset])
                        while (offset + 1 < textLen && getCharType(text[offset + 1]) == targetType) {
                            offset++
                        }
                        // Include the character in word end
                        if (offset < textLen) offset++
                    }
                }

                offset = offset.coerceIn(0, textLen)
                applyMotion(caret, anchor, offset, isSelect)
            }
        }
    }

    /**
     * ge: Move backward to end of previous word
     */
    fun movePrevWordEnd(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        runForEachCaret(editor) { caret ->
            repeat(count) {
                val currentOffset = caret.offset
                val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else currentOffset
                var offset = currentOffset

                if (offset > 0) {
                    offset--
                    val initialType = getCharType(text[offset])
                    if (initialType != CharType.WHITESPACE) {
                        while (offset > 0 && getCharType(text[offset]) == initialType) {
                            offset--
                        }
                    }
                    while (offset > 0 && text[offset].isWhitespace()) {
                        offset--
                    }
                    if (offset < textLen) offset++
                }

                offset = offset.coerceIn(0, textLen)
                applyMotion(caret, anchor, offset, isSelect)
            }
        }
    }

    /**
     * x: Select line, or extend selection by one more line
     */
    fun selectLine(editor: Editor) {
        val doc = editor.document
        if (doc.textLength == 0) return

        runForEachCaret(editor) { caret ->
            val hasSel = caret.hasSelection()
            val selStart = caret.selectionStart
            val selEnd = caret.selectionEnd

            val startLine = doc.getLineNumber(selStart)
            val endLine = doc.getLineNumber(if (hasSel && selEnd > selStart) (selEnd - 1).coerceAtLeast(0) else selStart)

            val lineStartOffset = doc.getLineStartOffset(startLine)
            val lineEndOffset = getLineEndWithNewline(doc, endLine)

            if (hasSel && selStart == lineStartOffset && selEnd == lineEndOffset) {
                // Already selects whole line(s) -> extend down 1 line
                val nextLine = (endLine + 1).coerceAtMost(doc.lineCount - 1)
                val newEndOffset = getLineEndWithNewline(doc, nextLine)
                caret.setSelection(selStart, newEndOffset)
                caret.moveToOffset(newEndOffset)
            } else {
                // Select current line(s)
                val fullEndOffset = getLineEndWithNewline(doc, endLine)
                caret.setSelection(lineStartOffset, fullEndOffset)
                caret.moveToOffset(fullEndOffset)
            }
        }
    }

    private fun getLineEndWithNewline(doc: Document, line: Int): Int {
        return if (line + 1 < doc.lineCount) {
            doc.getLineStartOffset(line + 1)
        } else {
            doc.getLineEndOffset(line)
        }
    }

    /**
     * %: Select entire buffer
     */
    fun selectAll(editor: Editor) {
        val doc = editor.document
        val textLen = doc.textLength
        editor.caretModel.removeSecondaryCarets()
        val caret = editor.caretModel.primaryCaret
        caret.setSelection(0, textLen)
        caret.moveToOffset(textLen)
    }

    /**
     * h, j, k, l character / line motions
     */
    fun moveLeft(editor: Editor, count: Int = 1) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val newOffset = (caret.offset - count).coerceAtLeast(0)
            applyMotion(caret, anchor, newOffset, isSelect)
        }
    }

    fun moveRight(editor: Editor, count: Int = 1) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val maxLen = editor.document.textLength
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val newOffset = (caret.offset + count).coerceAtMost(maxLen)
            applyMotion(caret, anchor, newOffset, isSelect)
        }
    }

    fun moveUp(editor: Editor, count: Int = 1) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val doc = editor.document
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val curLine = doc.getLineNumber(caret.offset)
            val curCol = caret.offset - doc.getLineStartOffset(curLine)
            val targetLine = (curLine - count).coerceAtLeast(0)
            val targetLineStart = doc.getLineStartOffset(targetLine)
            val targetLineEnd = doc.getLineEndOffset(targetLine)
            val newOffset = (targetLineStart + curCol).coerceAtMost(targetLineEnd)
            applyMotion(caret, anchor, newOffset, isSelect)
        }
    }

    fun moveDown(editor: Editor, count: Int = 1) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val doc = editor.document
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val curLine = doc.getLineNumber(caret.offset)
            val curCol = caret.offset - doc.getLineStartOffset(curLine)
            val targetLine = (curLine + count).coerceAtMost(doc.lineCount - 1)
            val targetLineStart = doc.getLineStartOffset(targetLine)
            val targetLineEnd = doc.getLineEndOffset(targetLine)
            val newOffset = (targetLineStart + curCol).coerceAtMost(targetLineEnd)
            applyMotion(caret, anchor, newOffset, isSelect)
        }
    }

    /**
     * gh: Move to line start (first non-blank character)
     */
    fun moveLineStart(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val doc = editor.document
        val text = doc.charsSequence
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val line = doc.getLineNumber(caret.offset)
            val lineStart = doc.getLineStartOffset(line)
            val lineEnd = doc.getLineEndOffset(line)
            var target = lineStart
            while (target < lineEnd && text[target].isWhitespace() && text[target] != '\n') {
                target++
            }
            applyMotion(caret, anchor, target, isSelect)
        }
    }

    /**
     * gl: Move to line end
     */
    fun moveLineEnd(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val doc = editor.document
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val line = doc.getLineNumber(caret.offset)
            val lineEnd = doc.getLineEndOffset(line)
            applyMotion(caret, anchor, lineEnd, isSelect)
        }
    }

    /**
     * gg: Move to top of document
     */
    fun moveFileStart(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            applyMotion(caret, anchor, 0, isSelect)
        }
    }

    /**
     * ge (g-menu): Move to end of document
     */
    fun moveFileEnd(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val textLen = editor.document.textLength
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            applyMotion(caret, anchor, textLen, isSelect)
        }
    }

    /**
     * ;: Collapse selection to single cursor
     */
    fun collapseSelection(editor: Editor) {
        runForEachCaret(editor) { caret ->
            val offset = caret.offset
            caret.removeSelection()
            caret.moveToOffset(offset)
        }
    }

    /**
     * Alt+;: Flip selection anchor and cursor
     */
    fun flipSelection(editor: Editor) {
        runForEachCaret(editor) { caret ->
            if (caret.hasSelection()) {
                val anchor = caret.leadSelectionOffset
                val cursor = caret.offset
                caret.setSelection(cursor, anchor)
                caret.moveToOffset(anchor)
            }
        }
    }

    /**
     * ,: Keep only primary caret
     */
    fun keepOnlyPrimaryCaret(editor: Editor) {
        editor.caretModel.removeSecondaryCarets()
    }

    private fun applyMotion(caret: Caret, anchor: Int, targetOffset: Int, isSelect: Boolean) {
        caret.moveToOffset(targetOffset)
        if (isSelect) {
            caret.setSelection(anchor, targetOffset)
        } else {
            caret.removeSelection()
        }
    }
}
