package jp.titze.intellij.helix.motion

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixSurroundActions
import jp.titze.intellij.helix.jumplist.HelixJumpListService
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

    private fun isLineEnding(c: Char): Boolean = c == '\n' || c == '\r'
    private fun isHorizontalWhitespace(c: Char): Boolean = c.isWhitespace() && !isLineEnding(c)

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
     * x: Select line, or extend selection by one or more lines
     */
    fun selectLine(editor: Editor, count: Int = 1) {
        val doc = editor.document
        if (doc.textLength == 0) return

        runForEachCaret(editor) { caret ->
            repeat(count.coerceAtLeast(1)) {
                val hasSel = caret.hasSelection()
                val selStart = caret.selectionStart
                val selEnd = caret.selectionEnd

                val startLine = doc.getLineNumber(selStart)
                val endLine =
                    doc.getLineNumber(if (hasSel && selEnd > selStart) (selEnd - 1).coerceAtLeast(0) else selStart)

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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    fun getPageSize(editor: Editor): Int {
        val visibleHeight = editor.scrollingModel.visibleArea.height
        val lineHeight = editor.lineHeight
        return if (visibleHeight > 0 && lineHeight > 0) {
            (visibleHeight / lineHeight).coerceAtLeast(1)
        } else {
            25
        }
    }

    /**
     * Ctrl-f / PageDown: Move page down
     */
    fun pageDown(editor: Editor, count: Int = 1) {
        val lines = getPageSize(editor) * count.coerceAtLeast(1)
        moveDown(editor, lines)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    /**
     * Ctrl-b / PageUp: Move page up
     */
    fun pageUp(editor: Editor, count: Int = 1) {
        val lines = getPageSize(editor) * count.coerceAtLeast(1)
        moveUp(editor, lines)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    /**
     * Ctrl-d: Move half page down
     */
    fun halfPageDown(editor: Editor, count: Int = 1) {
        val half = (getPageSize(editor) / 2).coerceAtLeast(1)
        val lines = half * count.coerceAtLeast(1)
        moveDown(editor, lines)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    /**
     * Ctrl-u: Move half page up
     */
    fun halfPageUp(editor: Editor, count: Int = 1) {
        val half = (getPageSize(editor) / 2).coerceAtLeast(1)
        val lines = half * count.coerceAtLeast(1)
        moveUp(editor, lines)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
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

    /**
     * ;: Collapse selection to single cursor
     */
    fun collapseSelection(editor: Editor) {
        runForEachCaret(editor) { caret ->
            val offset = caret.offset
            caret.removeSelection()
            caret.moveToOffset(offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * Alt+;: Flip selection anchor and cursor
     */
    fun flipSelection(editor: Editor) {
        runForEachCaret(editor) { caret ->
            if (caret.hasSelection()) {
                val anchor = caret.leadSelectionOffset
                val cursor = caret.offset
                caret.moveToOffset(anchor)
                caret.setSelection(cursor, anchor)
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * ,: Keep only primary caret
     */
    fun keepOnlyPrimaryCaret(editor: Editor) {
        editor.caretModel.removeSecondaryCarets()
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * C: Copy selection to next line (supports count)
     */
    fun copySelectionOnNextLine(editor: Editor, count: Int = 1) {
        copySelectionLineDelta(editor, delta = 1, count = count)
    }

    /**
     * Alt+C: Copy selection to previous line (supports count)
     */
    fun copySelectionOnPrevLine(editor: Editor, count: Int = 1) {
        copySelectionLineDelta(editor, delta = -1, count = count)
    }

    private fun copySelectionLineDelta(editor: Editor, delta: Int, count: Int) {
        val doc = editor.document
        if (doc.lineCount == 0) return
        val steps = count.coerceAtLeast(1)

        var lastAddedCaret: Caret? = null

        repeat(steps) {
            val currentCarets = editor.caretModel.allCarets.toList()
            val newCaretTargets = mutableListOf<Triple<Int, Int, Boolean>>() // targetHead, targetAnchor, hasSelection

            for (caret in currentCarets) {
                val headLogical = editor.offsetToLogicalPosition(caret.offset)
                val hasSel = caret.hasSelection()
                val anchorLogical =
                    if (hasSel) editor.offsetToLogicalPosition(caret.leadSelectionOffset) else headLogical

                val targetHeadLine = headLogical.line + delta
                val targetAnchorLine = anchorLogical.line + delta

                if (targetHeadLine in 0 until doc.lineCount && targetAnchorLine in 0 until doc.lineCount) {
                    val headLineStart = doc.getLineStartOffset(targetHeadLine)
                    val headLineEnd = doc.getLineEndOffset(targetHeadLine)
                    val targetHeadOffset = (headLineStart + headLogical.column).coerceIn(headLineStart, headLineEnd)

                    val anchorLineStart = doc.getLineStartOffset(targetAnchorLine)
                    val anchorLineEnd = doc.getLineEndOffset(targetAnchorLine)
                    val targetAnchorOffset =
                        (anchorLineStart + anchorLogical.column).coerceIn(anchorLineStart, anchorLineEnd)

                    newCaretTargets.add(Triple(targetHeadOffset, targetAnchorOffset, hasSel))
                }
            }

            for ((targetHeadOffset, targetAnchorOffset, hasSel) in newCaretTargets) {
                val alreadyExists = editor.caretModel.allCarets.any {
                    it.offset == targetHeadOffset &&
                            (!hasSel || (it.hasSelection() && it.leadSelectionOffset == targetAnchorOffset))
                }
                if (!alreadyExists) {
                    val newCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(targetHeadOffset), false)
                    if (newCaret != null) {
                        newCaret.moveToOffset(targetHeadOffset)
                        if (hasSel) {
                            newCaret.setSelection(targetAnchorOffset, targetHeadOffset)
                        } else {
                            newCaret.removeSelection()
                        }
                        lastAddedCaret = newCaret
                    }
                }
            }
        }

        if (lastAddedCaret != null && editor.caretModel.allCarets.contains(lastAddedCaret)) {
            val carets = editor.caretModel.allCarets
            val primaryTarget = if (delta > 0) {
                carets.maxByOrNull { it.offset }
            } else {
                carets.minByOrNull { it.offset }
            }
            if (primaryTarget != null && primaryTarget != editor.caretModel.primaryCaret) {
                makeCaretPrimary(editor, primaryTarget)
            }
        }

        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun makeCaretPrimary(editor: Editor, targetCaret: Caret) {
        if (targetCaret == editor.caretModel.primaryCaret) return
        val offset = targetCaret.offset
        val hasSel = targetCaret.hasSelection()
        val anchor = if (hasSel) targetCaret.leadSelectionOffset else offset

        editor.caretModel.removeCaret(targetCaret)
        val newPrimary = editor.caretModel.addCaret(editor.offsetToVisualPosition(offset), true)
        if (newPrimary != null) {
            newPrimary.moveToOffset(offset)
            if (hasSel) {
                newPrimary.setSelection(anchor, offset)
            } else {
                newPrimary.removeSelection()
            }
        }
    }

    /**
     * Alt+,: Remove primary selection
     */
    fun removePrimarySelection(editor: Editor) {
        if (editor.caretModel.caretCount > 1) {
            editor.caretModel.removeCaret(editor.caretModel.primaryCaret)
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
    }

    /**
     * ): Rotate main selection forward
     * (: Rotate main selection backward
     */
    fun rotateSelections(editor: Editor, forward: Boolean) {
        val carets = editor.caretModel.allCarets.sortedBy { it.offset }
        if (carets.size <= 1) return

        val primary = editor.caretModel.primaryCaret
        val currentIndex = carets.indexOf(primary)
        val targetIndex = if (forward) {
            if (currentIndex == -1) 0 else (currentIndex + 1) % carets.size
        } else {
            if (currentIndex == -1) carets.size - 1 else (currentIndex - 1 + carets.size) % carets.size
        }

        val targetCaret = carets[targetIndex]
        makeCaretPrimary(editor, targetCaret)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /**
     * Alt+s: Split selection on newlines
     */
    fun splitSelectionOnNewlines(editor: Editor) {
        val doc = editor.document
        val carets = editor.caretModel.allCarets.toList()
        val subselections = mutableListOf<Triple<Int, Int, Boolean>>() // anchor, head, hasSel

        for (caret in carets) {
            if (!caret.hasSelection()) {
                subselections.add(Triple(caret.offset, caret.offset, false))
                continue
            }
            val start = caret.selectionStart
            val end = caret.selectionEnd
            val isReverse = caret.offset < caret.leadSelectionOffset
            val startLine = doc.getLineNumber(start)
            val endLine = doc.getLineNumber(end)

            if (startLine == endLine) {
                val anchor = if (isReverse) end else start
                val head = if (isReverse) start else end
                subselections.add(Triple(anchor, head, true))
            } else {
                for (line in startLine..endLine) {
                    val lineStart = doc.getLineStartOffset(line)
                    val lineEnd = doc.getLineEndOffset(line)
                    val subStart = if (line == startLine) maxOf(start, lineStart) else lineStart
                    val subEnd = if (line == endLine) minOf(end, lineEnd) else lineEnd

                    // Skip zero-width subselection at endLine if it only touched col 0 due to preceding newline
                    if (line == endLine && subStart == subEnd && end == lineStart) {
                        continue
                    }

                    val anchor = if (isReverse) subEnd else subStart
                    val head = if (isReverse) subStart else subEnd
                    val hasSelection = anchor != head
                    subselections.add(Triple(anchor, head, hasSelection))
                }
            }
        }

        if (subselections.isEmpty()) return

        // Deduplicate subselections by (head, anchor)
        val distinct = subselections.distinctBy { it.second to it.first }.sortedBy { it.second }

        // Set the carets
        editor.caretModel.removeSecondaryCarets()
        val primary = editor.caretModel.primaryCaret
        val first = distinct.first()
        primary.moveToOffset(first.second)
        if (first.third) {
            primary.setSelection(first.first, first.second)
        } else {
            primary.removeSelection()
        }

        for (item in distinct.drop(1)) {
            val newCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(item.second), false)
            if (newCaret != null) {
                newCaret.moveToOffset(item.second)
                if (item.third) {
                    newCaret.setSelection(item.first, item.second)
                } else {
                    newCaret.removeSelection()
                }
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun applyMotion(caret: Caret, anchor: Int, targetOffset: Int, isSelect: Boolean) {
        caret.moveToOffset(targetOffset)
        if (isSelect) {
            caret.setSelection(anchor, targetOffset)
        } else {
            caret.removeSelection()
        }
    }

    private fun applySelectingMotion(caret: Caret, anchor: Int, targetOffset: Int) {
        caret.moveToOffset(targetOffset)
        if (anchor != targetOffset) {
            caret.setSelection(anchor, targetOffset)
        } else {
            caret.removeSelection()
        }
    }

    /**
     * f, t, F, T character-finding motions:
     * - t: find till next char (exclusive)
     * - f: find to next char (inclusive)
     * - T: find till prev char (exclusive)
     * - F: find to prev char (inclusive)
     */
    fun findCharMotion(editor: Editor, motion: Char, targetChar: Char, count: Int = 1): Boolean {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = text.length
        if (textLen == 0) return false

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        var anyMoved = false

        runForEachCaret(editor) { caret ->
            val curOffset = caret.offset
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else curOffset
            val targetOffset = findTargetOffset(text, textLen, curOffset, motion, targetChar, count)

            if (targetOffset != null) {
                caret.moveToOffset(targetOffset)
                caret.setSelection(anchor, targetOffset)
                anyMoved = true
            }
        }

        return anyMoved
    }

    private fun findTargetOffset(
        text: CharSequence,
        textLen: Int,
        curOffset: Int,
        motion: Char,
        targetChar: Char,
        count: Int
    ): Int? {
        var remainingCount = count.coerceAtLeast(1)

        when (motion) {
            't', 'f' -> {
                var idx = curOffset + 1
                while (idx < textLen) {
                    if (text[idx] == targetChar) {
                        remainingCount--
                        if (remainingCount == 0) {
                            return if (motion == 't') {
                                idx
                            } else {
                                (idx + 1).coerceAtMost(textLen)
                            }
                        }
                    }
                    idx++
                }
                return null
            }

            'T', 'F' -> {
                var idx = curOffset - 1
                while (idx >= 0) {
                    if (text[idx] == targetChar) {
                        remainingCount--
                        if (remainingCount == 0) {
                            return if (motion == 'T') {
                                idx + 1
                            } else {
                                idx
                            }
                        }
                    }
                    idx--
                }
                return null
            }

            else -> return null
        }
    }

    // -------------------------------------------------------------------------
    // Unimpaired ([ and ]) Motions
    // -------------------------------------------------------------------------

    /**
     * [p / ]p: Move to previous/next paragraph boundary (empty line)
     */
    fun moveParagraph(editor: Editor, forward: Boolean, count: Int = 1) {
        val doc = editor.document
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            var curLine = doc.getLineNumber(caret.offset)
            val lineCount = doc.lineCount

            repeat(count.coerceAtLeast(1)) {
                if (forward) {
                    if (curLine < lineCount - 1) {
                        if (!isLineBlank(doc, curLine)) {
                            while (curLine < lineCount - 1 && !isLineBlank(doc, curLine)) {
                                curLine++
                            }
                        } else {
                            while (curLine < lineCount - 1 && isLineBlank(doc, curLine)) {
                                curLine++
                            }
                            while (curLine < lineCount - 1 && !isLineBlank(doc, curLine)) {
                                curLine++
                            }
                        }
                    }
                } else {
                    if (curLine > 0) {
                        if (!isLineBlank(doc, curLine)) {
                            while (curLine > 0 && !isLineBlank(doc, curLine)) {
                                curLine--
                            }
                        } else {
                            while (curLine > 0 && isLineBlank(doc, curLine)) {
                                curLine--
                            }
                            while (curLine > 0 && !isLineBlank(doc, curLine)) {
                                curLine--
                            }
                        }
                    }
                }
            }

            val targetOffset = doc.getLineStartOffset(curLine.coerceIn(0, lineCount - 1))
            applyMotion(caret, anchor, targetOffset, isSelect)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun isLineBlank(doc: Document, line: Int): Boolean {
        if (line < 0 || line >= doc.lineCount) return true
        val start = doc.getLineStartOffset(line)
        val end = doc.getLineEndOffset(line)
        val chars = doc.charsSequence
        for (i in start until end) {
            if (!chars[i].isWhitespace()) return false
        }
        return true
    }

    /**
     * [Space / ]Space: Add empty line above/below without entering insert mode
     */
    fun addNewline(editor: Editor, below: Boolean, count: Int = 1) {
        val project = editor.project
        val doc = editor.document
        val totalCount = count.coerceAtLeast(1)

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.offset }
            val addedText = "\n".repeat(totalCount)
            for (caret in carets) {
                val line = doc.getLineNumber(caret.offset)
                if (below) {
                    val lineEnd = doc.getLineEndOffset(line)
                    doc.insertString(lineEnd, "\n" + "\n".repeat(totalCount - 1))
                } else {
                    val lineStart = doc.getLineStartOffset(line)
                    doc.insertString(lineStart, addedText)
                }
            }
        }
    }

    /**
     * [c / ]c: Move to previous/next comment (selects comment block)
     */
    fun moveComment(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        var ranges = getMatchingRangesFromPsi(editor) { elem ->
            elem is PsiComment || elem.javaClass.simpleName.contains("Comment", ignoreCase = true)
        }

        if (ranges.isEmpty()) {
            ranges = findLineCommentRanges(editor.document)
        }

        return navigateToRange(editor, ranges, forward, count)
    }

    /**
     * [f / ]f: Move to previous/next function or method (selects function block)
     */
    fun moveFunction(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        val ranges = getMatchingRangesFromPsi(editor) { elem ->
            val name = elem.javaClass.simpleName
            val isFuncCandidate = name.contains("Method", ignoreCase = true) ||
                    name.contains("Function", ignoreCase = true) ||
                    name.contains("Constructor", ignoreCase = true)
            isFuncCandidate &&
                    !name.contains("Literal", ignoreCase = true) &&
                    !name.contains("Lambda", ignoreCase = true) &&
                    !name.contains("Body", ignoreCase = true) &&
                    !name.contains("Call", ignoreCase = true) &&
                    !name.contains("Expr", ignoreCase = true) &&
                    !name.contains("Reference", ignoreCase = true) &&
                    !name.contains("List", ignoreCase = true) &&
                    !name.contains("Parameter", ignoreCase = true) &&
                    !name.contains("Type", ignoreCase = true) &&
                    !name.contains("Argument", ignoreCase = true) &&
                    !name.contains("Block", ignoreCase = true) &&
                    !name.contains("Statement", ignoreCase = true)
        }

        val moved = navigateToRange(editor, ranges, forward, count)
        if (!moved) {
            val action = if (forward) "MethodDown" else "MethodUp"
            repeat(count.coerceAtLeast(1)) {
                HelixActionDelegate.executeAction(action, editor)
            }
            return true
        }
        return true
    }

    /**
     * [t / ]t: Move to previous/next class or type (selects class block)
     */
    fun moveType(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        var ranges = getMatchingRangesFromPsi(editor) { elem ->
            val name = elem.javaClass.simpleName
            val isTypeCandidate = (name.contains("Class", ignoreCase = true) ||
                    name.contains("Interface", ignoreCase = true) ||
                    name.contains("Struct", ignoreCase = true) ||
                    name.contains("Trait", ignoreCase = true) ||
                    name.contains("Enum", ignoreCase = true) ||
                    name.contains("Record", ignoreCase = true) ||
                    name.contains("Object", ignoreCase = true) ||
                    name.contains("TypeAlias", ignoreCase = true))

            isTypeCandidate &&
                    !name.contains("Literal", ignoreCase = true) &&
                    !name.contains("Destruct", ignoreCase = true) &&
                    !name.contains("Component", ignoreCase = true) &&
                    !name.contains("Body", ignoreCase = true) &&
                    !name.contains("Initializer", ignoreCase = true) &&
                    !name.contains("List", ignoreCase = true) &&
                    !name.contains("Header", ignoreCase = true) &&
                    !name.contains("Access", ignoreCase = true) &&
                    !name.contains("Expr", ignoreCase = true) &&
                    !name.contains("Reference", ignoreCase = true) &&
                    !name.contains("TypeElement", ignoreCase = true) &&
                    !name.contains("Parameter", ignoreCase = true) &&
                    !name.contains("Constant", ignoreCase = true) &&
                    !name.contains("Entry", ignoreCase = true) &&
                    !name.contains("Argument", ignoreCase = true) &&
                    !name.contains("Statement", ignoreCase = true) &&
                    !name.contains("Block", ignoreCase = true)
        }

        if (ranges.isEmpty()) {
            ranges = findClassRangesRegex(editor.document)
        }

        return navigateToRange(editor, ranges, forward, count)
    }

    /**
     * [a / ]a: Move to previous/next parameter (selects parameter block)
     */
    fun moveParameter(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        val ranges = getMatchingRangesFromPsi(editor) { elem ->
            val name = elem.javaClass.simpleName
            name.contains("Parameter", ignoreCase = true) &&
                    !name.contains("List", ignoreCase = true) &&
                    !name.contains("Type", ignoreCase = true)
        }

        return navigateToRange(editor, ranges, forward, count)
    }

    /**
     * [T / ]T: Move to previous/next test method (selects test block)
     */
    fun moveTest(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        val ranges = getMatchingRangesFromPsi(editor) { elem ->
            val name = elem.javaClass.simpleName
            val isMethodOrFunc =
                (name.contains("Method", ignoreCase = true) || name.contains("Function", ignoreCase = true)) &&
                        !name.contains("Body", ignoreCase = true) &&
                        !name.contains("Call", ignoreCase = true) &&
                        !name.contains("Expr", ignoreCase = true) &&
                        !name.contains("List", ignoreCase = true)
            if (isMethodOrFunc) {
                val text = elem.text
                text.contains("@Test") ||
                        text.contains("fun test", ignoreCase = true) ||
                        text.contains("def test", ignoreCase = true) ||
                        text.contains("void test", ignoreCase = true)
            } else false
        }

        val moved = navigateToRange(editor, ranges, forward, count)
        if (!moved) {
            HelixActionDelegate.executeAction("GotoTest", editor)
        }
        return true
    }

    /**
     * [d / ]d / [D / ]D: Diagnostic error navigation
     */
    fun moveDiagnostic(editor: Editor, forward: Boolean, count: Int = 1, toEnd: Boolean = false): Boolean {
        if (toEnd) {
            if (forward) {
                editor.caretModel.primaryCaret.moveToOffset(editor.document.textLength)
                HelixActionDelegate.executeAction("GotoPreviousError", editor)
            } else {
                editor.caretModel.primaryCaret.moveToOffset(0)
                HelixActionDelegate.executeAction("GotoNextError", editor)
            }
        } else {
            val action = if (forward) "GotoNextError" else "GotoPreviousError"
            repeat(count.coerceAtLeast(1)) {
                HelixActionDelegate.executeAction(action, editor)
            }
        }
        return true
    }

    /**
     * [g / ]g / [G / ]G: VCS change marker navigation
     */
    fun moveChange(editor: Editor, forward: Boolean, count: Int = 1, toEnd: Boolean = false): Boolean {
        if (toEnd) {
            if (forward) {
                if (!HelixActionDelegate.executeAction("VcsShowLastChangeMarker", editor)) {
                    editor.caretModel.primaryCaret.moveToOffset(editor.document.textLength)
                    HelixActionDelegate.executeAction("VcsShowPrevChangeMarker", editor)
                }
            } else {
                if (!HelixActionDelegate.executeAction("VcsShowFirstChangeMarker", editor)) {
                    editor.caretModel.primaryCaret.moveToOffset(0)
                    HelixActionDelegate.executeAction("VcsShowNextChangeMarker", editor)
                }
            }
        } else {
            val action = if (forward) "VcsShowNextChangeMarker" else "VcsShowPrevChangeMarker"
            repeat(count.coerceAtLeast(1)) {
                HelixActionDelegate.executeAction(action, editor)
            }
        }
        return true
    }

    private fun getMatchingRangesFromPsi(
        editor: Editor,
        predicate: (PsiElement) -> Boolean
    ): List<TextRange> {
        val project = editor.project ?: return emptyList()
        val doc = editor.document
        return try {
            runReadAction {
                val psiFile =
                    PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction emptyList()
                collectMatchingRanges(psiFile, predicate)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun collectMatchingRanges(psiFile: PsiFile, predicate: (PsiElement) -> Boolean): List<TextRange> {
        val result = mutableListOf<TextRange>()
        fun dfs(element: PsiElement) {
            var child = element.firstChild
            while (child != null) {
                if (predicate(child)) {
                    val range = child.textRange
                    if (range != null && range.length > 0) {
                        result.add(range)
                    }
                }
                dfs(child)
                child = child.nextSibling
            }
        }
        dfs(psiFile)
        return result.distinctBy { it.startOffset to it.endOffset }.sortedBy { it.startOffset }
    }

    private fun findLineCommentRanges(doc: Document): List<TextRange> {
        val prefixes = listOf("//", "/*", "#", "--", "<!--")
        val ranges = mutableListOf<TextRange>()
        for (line in 0 until doc.lineCount) {
            val start = doc.getLineStartOffset(line)
            val end = doc.getLineEndOffset(line)
            val text = doc.charsSequence.subSequence(start, end).toString()
            for (prefix in prefixes) {
                val idx = text.indexOf(prefix)
                if (idx >= 0) {
                    ranges.add(TextRange(start + idx, end))
                    break
                }
            }
        }
        return ranges
    }

    private fun findClassRangesRegex(doc: Document): List<TextRange> {
        val regex = Regex(
            """^\s*(?:(?:public|private|protected|internal|abstract|final|open|data|sealed|value)\s+)*(?:class|interface|struct|trait|enum(?:\s+class)?|record|object|typealias)\s+([A-Za-z0-9_]+)""",
            RegexOption.MULTILINE
        )
        val text = doc.charsSequence
        return regex.findAll(text).map { match ->
            val start = match.range.first
            val line = doc.getLineNumber(start)
            val lineEnd = doc.getLineEndOffset(line)
            TextRange(start, lineEnd)
        }.toList()
    }

    private fun navigateToRange(
        editor: Editor,
        ranges: List<TextRange>,
        forward: Boolean,
        count: Int
    ): Boolean {
        if (ranges.isEmpty()) return false
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        var anyMoved = false

        runForEachCaret(editor) { caret ->
            val curOffset = caret.offset

            val targetRange = if (forward) {
                val refOffset = if (caret.hasSelection()) maxOf(curOffset, caret.selectionEnd) else curOffset
                val candidates = ranges.filter { it.startOffset >= refOffset }
                val idx = (count - 1).coerceAtMost(candidates.size - 1)
                if (candidates.isNotEmpty()) candidates[idx] else null
            } else {
                val refOffset = if (caret.hasSelection()) minOf(curOffset, caret.selectionStart) else curOffset
                val candidates = ranges.filter { it.startOffset < refOffset }.sortedByDescending { it.startOffset }
                val idx = (count - 1).coerceAtMost(candidates.size - 1)
                if (candidates.isNotEmpty()) candidates[idx] else null
            }

            if (targetRange != null) {
                if (isSelect) {
                    val anchor = if (caret.hasSelection()) caret.leadSelectionOffset else curOffset
                    val targetOffset = if (forward) targetRange.endOffset else targetRange.startOffset
                    caret.moveToOffset(targetOffset)
                    caret.setSelection(anchor, targetOffset)
                } else {
                    caret.moveToOffset(targetRange.startOffset)
                    caret.setSelection(targetRange.startOffset, targetRange.endOffset)
                }
                anyMoved = true
            }
        }

        if (anyMoved) {
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
        return anyMoved
    }

    /**
     * mm: Move to matching bracket (paren, brace, square bracket, angle bracket)
     */
    fun matchBrackets(editor: Editor, count: Int = 1): Boolean {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = text.length
        if (textLen == 0) return false

        editor.project?.let { HelixJumpListService.getInstance(it).recordCurrent(editor) }

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        val action = ActionManager.getInstance().getAction("EditorMatchBrace") as? EditorAction
        val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)

        var anyMoved = false

        repeat(count) {
            runForEachCaret(editor) { caret ->
                val curOffset = caret.offset
                val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else curOffset

                var targetOffset: Int? = null

                if (action != null) {
                    val beforeOffset = caret.offset
                    try {
                        runReadAction {
                            action.handler.execute(editor, caret, dataContext)
                        }
                    } catch (_: Throwable) {
                    }
                    if (caret.offset != beforeOffset) {
                        targetOffset = caret.offset
                    }
                }

                if (targetOffset == null) {
                    targetOffset = findMatchingBracketFallback(text, textLen, curOffset)
                }

                if (targetOffset != null && targetOffset != curOffset) {
                    applyMotion(caret, anchor, targetOffset, isSelect)
                    anyMoved = true
                } else if (isSelect && targetOffset != null) {
                    caret.setSelection(anchor, targetOffset)
                    anyMoved = true
                }
            }
        }

        if (anyMoved) {
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
        return anyMoved
    }

    private fun findMatchingBracketFallback(text: CharSequence, textLen: Int, offset: Int): Int? {
        val charAtOffset = if (offset in 0 until textLen) text[offset] else null
        val match1 = findPairMatch(text, offset, charAtOffset)
        if (match1 != null) return match1

        if (offset > 0) {
            val charBefore = text[offset - 1]
            val match2 = findPairMatch(text, offset - 1, charBefore)
            if (match2 != null) return match2
        }

        return null
    }

    private fun findPairMatch(text: CharSequence, index: Int, ch: Char?): Int? {
        return when (ch) {
            '(' -> HelixSurroundActions.findMatchingClose(text, index, '(', ')')
            '[' -> HelixSurroundActions.findMatchingClose(text, index, '[', ']')
            '{' -> HelixSurroundActions.findMatchingClose(text, index, '{', '}')
            '<' -> HelixSurroundActions.findMatchingClose(text, index, '<', '>')
            ')' -> HelixSurroundActions.findMatchingOpen(text, index, '(', ')')
            ']' -> HelixSurroundActions.findMatchingOpen(text, index, '[', ']')
            '}' -> HelixSurroundActions.findMatchingOpen(text, index, '{', '}')
            '>' -> HelixSurroundActions.findMatchingOpen(text, index, '<', '>')
            else -> null
        }
    }
}

