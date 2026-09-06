package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType

object HelixSelectionMotions {

    /**
     * ;: Collapse selection to single cursor
     */
    fun collapseSelection(editor: Editor) {
        HelixMotionUtils.runForEachCaret(editor) { caret ->
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
        HelixMotionUtils.runForEachCaret(editor) { caret ->
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
                val alreadyExists = editor.caretModel.allCarets.any { c ->
                    c.offset == targetHeadOffset &&
                        (!hasSel || (c.hasSelection() && c.leadSelectionOffset == targetAnchorOffset))
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
}
