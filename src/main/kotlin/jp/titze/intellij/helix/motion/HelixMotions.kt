package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixMotions {

    private enum class CharType {
        WORD,
        PUNCTUATION,
        WHITESPACE,
    }

    private fun getCharType(c: Char): CharType = when {
        c.isWhitespace() -> CharType.WHITESPACE
        c.isLetterOrDigit() || c == '_' -> CharType.WORD
        else -> CharType.PUNCTUATION
    }

    private fun runForEachCaret(editor: Editor, action: (Caret) -> Unit) {
        HelixMotionUtils.runForEachCaret(editor, action)
    }

    private fun isLineEnding(c: Char): Boolean = HelixMotionUtils.isLineEnding(c)
    private fun isHorizontalWhitespace(c: Char): Boolean = HelixMotionUtils.isHorizontalWhitespace(c)

    /**
     * w: Move forward to start of next word (selects word in Normal mode, extends in Select mode)
     */
    fun moveNextWordStart(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            var anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
                if (offset < textLen) {
                    if (isLineEnding(text[offset])) {
                        // Starting at a line break: advance across line breaks
                        while (offset < textLen && isLineEnding(text[offset])) {
                            offset++
                        }
                        if (!isSelect) {
                            anchor = offset
                        }
                        // If line starts with indentation, select that indentation
                        if (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                                offset++
                            }
                        } else if (offset < textLen && !isLineEnding(text[offset])) {
                            val initialType = getCharType(text[offset])
                            while (offset < textLen && getCharType(text[offset]) == initialType) {
                                offset++
                            }
                            while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                                offset++
                            }
                        }
                    } else if (isHorizontalWhitespace(text[offset])) {
                        // Starting on horizontal whitespace: skip whitespace up to word or line break
                        while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            offset++
                        }
                    } else {
                        // Starting on word or punctuation
                        val initialType = getCharType(text[offset])
                        while (offset < textLen && getCharType(text[offset]) == initialType) {
                            offset++
                        }
                        // Skip trailing horizontal whitespace on the same line, stopping before line break
                        while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            offset++
                        }
                    }
                }
            }

            offset = offset.coerceIn(0, textLen)
            applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * b: Move backward to start of previous word (selects word in Normal mode, extends in Select mode)
     */
    fun movePrevWordStart(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
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
            }

            offset = offset.coerceIn(0, textLen)
            applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * e: Move forward to end of current/next word (selects in Normal mode, extends in Select mode)
     */
    fun moveWordEnd(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
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
            }

            offset = offset.coerceIn(0, textLen)
            applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * x: Select current line (including newline). If already selected, extend selection by next line.
     */
    fun selectLine(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val lineCount = doc.lineCount
        if (doc.textLength == 0 || lineCount == 0) return

        runForEachCaret(editor) { caret ->
            val hasSelection = caret.hasSelection()
            val steps = count.coerceAtLeast(1)

            if (!hasSelection) {
                val line = doc.getLineNumber(caret.offset)
                val lineStart = doc.getLineStartOffset(line)
                val targetLine = (line + steps - 1).coerceAtMost(lineCount - 1)
                val lineEnd = HelixMotionUtils.getLineEndWithNewline(doc, targetLine)

                caret.moveToOffset(lineEnd)
                caret.setSelection(lineStart, lineEnd)
            } else {
                val selStart = caret.selectionStart
                val selEnd = caret.selectionEnd
                val anchor = caret.leadSelectionOffset
                val isForward = anchor == selStart

                val curEndLine = doc.getLineNumber(selEnd.coerceAtLeast(1) - 1)
                val newEndLine = (curEndLine + steps).coerceAtMost(lineCount - 1)
                val newEnd = HelixMotionUtils.getLineEndWithNewline(doc, newEndLine)

                if (isForward) {
                    caret.moveToOffset(newEnd)
                    caret.setSelection(anchor, newEnd)
                } else {
                    caret.moveToOffset(anchor)
                    caret.setSelection(newEnd, anchor)
                }
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * %: Select entire buffer
     */
    fun selectAll(editor: Editor) {
        val doc = editor.document
        val textLen = doc.textLength
        if (textLen == 0) return

        editor.caretModel.removeSecondaryCarets()
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(textLen)
        caret.setSelection(0, textLen)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * h: Move left
     */
    fun moveLeft(editor: Editor, count: Int = 1) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val target = (caret.offset - count).coerceAtLeast(0)
            applyMotion(caret, anchor, target, isSelect)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * l: Move right
     */
    fun moveRight(editor: Editor, count: Int = 1) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val maxOffset = editor.document.textLength
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val target = (caret.offset + count).coerceAtMost(maxOffset)
            applyMotion(caret, anchor, target, isSelect)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * k: Move up
     */
    fun moveUp(editor: Editor, count: Int = 1) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val doc = editor.document
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val line = doc.getLineNumber(caret.offset)
            val col = caret.offset - doc.getLineStartOffset(line)
            val targetLine = (line - count).coerceAtLeast(0)
            val targetStart = doc.getLineStartOffset(targetLine)
            val targetEnd = doc.getLineEndOffset(targetLine)
            val target = (targetStart + col).coerceAtMost(targetEnd)
            applyMotion(caret, anchor, target, isSelect)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * j: Move down
     */
    fun moveDown(editor: Editor, count: Int = 1) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val doc = editor.document
        val lineCount = doc.lineCount
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val line = doc.getLineNumber(caret.offset)
            val col = caret.offset - doc.getLineStartOffset(line)
            val targetLine = (line + count).coerceAtMost((lineCount - 1).coerceAtLeast(0))
            val targetStart = doc.getLineStartOffset(targetLine)
            val targetEnd = doc.getLineEndOffset(targetLine)
            val target = (targetStart + col).coerceAtMost(targetEnd)
            applyMotion(caret, anchor, target, isSelect)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * gh: Move to line start (actual first character)
     */
    fun moveLineStart(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val doc = editor.document
        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val line = doc.getLineNumber(caret.offset)
            val lineStart = doc.getLineStartOffset(line)
            applyMotion(caret, anchor, lineStart, isSelect)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * gs: Move to first non-whitespace character of the line
     */
    fun moveLineFirstNonWhitespace(editor: Editor) {
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * gg: Move to top of document, or to line number if count is specified (1-based)
     */
    fun moveFileStart(editor: Editor, count: Int? = null) {
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val doc = editor.document
        val lineCount = doc.lineCount
        if (doc.textLength == 0 || lineCount == 0) return

        val targetOffset = if (count != null && count > 0) {
            val targetLine = (count - 1).coerceIn(0, (lineCount - 1).coerceAtLeast(0))
            doc.getLineStartOffset(targetLine)
        } else {
            0
        }

        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            applyMotion(caret, anchor, targetOffset, isSelect)
        }

        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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

        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun applyMotion(caret: Caret, anchor: Int, targetOffset: Int, isSelect: Boolean) {
        HelixMotionUtils.applyMotion(caret, anchor, targetOffset, isSelect)
    }

    private fun applySelectingMotion(caret: Caret, anchor: Int, targetOffset: Int) {
        HelixMotionUtils.applySelectingMotion(caret, anchor, targetOffset)
    }

    // -------------------------------------------------------------------------
    // Forwarding delegates for backward compatibility
    // -------------------------------------------------------------------------

    fun collapseSelection(editor: Editor) = HelixSelectionMotions.collapseSelection(editor)
    fun flipSelection(editor: Editor) = HelixSelectionMotions.flipSelection(editor)
    fun keepOnlyPrimaryCaret(editor: Editor) = HelixSelectionMotions.keepOnlyPrimaryCaret(editor)
    fun copySelectionOnNextLine(editor: Editor, count: Int = 1) =
        HelixSelectionMotions.copySelectionOnNextLine(editor, count)
    fun copySelectionOnPrevLine(editor: Editor, count: Int = 1) =
        HelixSelectionMotions.copySelectionOnPrevLine(editor, count)
    fun removePrimarySelection(editor: Editor) = HelixSelectionMotions.removePrimarySelection(editor)
    fun rotateSelections(editor: Editor, forward: Boolean) = HelixSelectionMotions.rotateSelections(editor, forward)
    fun splitSelectionOnNewlines(editor: Editor) = HelixSelectionMotions.splitSelectionOnNewlines(editor)

    fun getPageSize(editor: Editor): Int = HelixPageMotions.getPageSize(editor)
    fun pageDown(editor: Editor, count: Int = 1) = HelixPageMotions.pageDown(editor, count)
    fun pageUp(editor: Editor, count: Int = 1) = HelixPageMotions.pageUp(editor, count)
    fun halfPageDown(editor: Editor, count: Int = 1) = HelixPageMotions.halfPageDown(editor, count)
    fun halfPageUp(editor: Editor, count: Int = 1) = HelixPageMotions.halfPageUp(editor, count)

    fun findCharMotion(editor: Editor, motion: Char, targetChar: Char, count: Int = 1): Boolean =
        HelixFindCharMotions.findCharMotion(editor, motion, targetChar, count)

    fun moveParagraph(editor: Editor, forward: Boolean, count: Int = 1) =
        HelixJumpMotions.moveParagraph(editor, forward, count)
    fun addNewline(editor: Editor, below: Boolean, count: Int = 1) = HelixJumpMotions.addNewline(editor, below, count)
    fun moveComment(editor: Editor, forward: Boolean, count: Int = 1): Boolean =
        HelixJumpMotions.moveComment(editor, forward, count)
    fun moveFunction(editor: Editor, forward: Boolean, count: Int = 1): Boolean =
        HelixJumpMotions.moveFunction(editor, forward, count)
    fun moveType(editor: Editor, forward: Boolean, count: Int = 1): Boolean =
        HelixJumpMotions.moveType(editor, forward, count)
    fun moveParameter(editor: Editor, forward: Boolean, count: Int = 1): Boolean =
        HelixJumpMotions.moveParameter(editor, forward, count)
    fun moveTest(editor: Editor, forward: Boolean, count: Int = 1): Boolean =
        HelixJumpMotions.moveTest(editor, forward, count)
    fun moveDiagnostic(editor: Editor, forward: Boolean, count: Int = 1, toEnd: Boolean = false): Boolean =
        HelixJumpMotions.moveDiagnostic(editor, forward, count, toEnd)
    fun moveChange(editor: Editor, forward: Boolean, count: Int = 1, toEnd: Boolean = false): Boolean =
        HelixJumpMotions.moveChange(editor, forward, count, toEnd)
    fun matchBrackets(editor: Editor, count: Int = 1): Boolean = HelixJumpMotions.matchBrackets(editor, count)
}
