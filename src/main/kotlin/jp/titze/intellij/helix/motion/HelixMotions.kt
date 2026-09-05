package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
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
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
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
     * ge: Move backward to end of previous word (selects in Normal mode, extends in Select mode)
     */
    fun movePrevWordEnd(editor: Editor, count: Int = 1) {
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
                val anchorLogical = if (hasSel) editor.offsetToLogicalPosition(caret.leadSelectionOffset) else headLogical

                val targetHeadLine = headLogical.line + delta
                val targetAnchorLine = anchorLogical.line + delta

                if (targetHeadLine in 0 until doc.lineCount && targetAnchorLine in 0 until doc.lineCount) {
                    val headLineStart = doc.getLineStartOffset(targetHeadLine)
                    val headLineEnd = doc.getLineEndOffset(targetHeadLine)
                    val targetHeadOffset = (headLineStart + headLogical.column).coerceIn(headLineStart, headLineEnd)

                    val anchorLineStart = doc.getLineStartOffset(targetAnchorLine)
                    val anchorLineEnd = doc.getLineEndOffset(targetAnchorLine)
                    val targetAnchorOffset = (anchorLineStart + anchorLogical.column).coerceIn(anchorLineStart, anchorLineEnd)

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

        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
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
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
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
        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
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
                    if (line == endLine && subStart == subEnd && end == lineStart && endLine > startLine) {
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
        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
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
}
