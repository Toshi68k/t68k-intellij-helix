// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor

internal object HelixMotionUtils {

    fun runForEachCaret(editor: Editor, action: (Caret) -> Unit) {
        editor.caretModel.runForEachCaret(action)
    }

    fun getHelixAnchor(caret: Caret): Int {
        if (!caret.hasSelection()) return caret.offset
        return if (caret.offset <= caret.selectionStart) {
            caret.selectionEnd
        } else {
            caret.selectionStart
        }
    }

    fun setInclusiveSelection(caret: Caret, anchor: Int, head: Int) {
        val docLen = caret.editor.document.textLength
        if (docLen == 0) {
            caret.moveToOffset(0)
            caret.removeSelection()
            return
        }
        val clampedHead = head.coerceIn(0, docLen)
        val clampedAnchor = anchor.coerceIn(0, docLen)
        val hadSelection = caret.hasSelection()
        caret.moveToOffset(clampedHead)
        if (clampedHead >= clampedAnchor) {
            val endOffset = if (clampedHead < docLen) clampedHead + 1 else docLen
            caret.setSelection(clampedAnchor, endOffset)
        } else {
            val effectiveAnchor = if (!hadSelection && clampedAnchor < docLen) {
                clampedAnchor + 1
            } else {
                clampedAnchor
            }
            caret.setSelection(effectiveAnchor, clampedHead)
        }
    }

    fun applyMotion(caret: Caret, anchor: Int, targetOffset: Int, isSelect: Boolean) {
        if (isSelect) {
            setInclusiveSelection(caret, anchor, targetOffset)
        } else {
            caret.moveToOffset(targetOffset)
            caret.removeSelection()
        }
    }

    fun applySelectingMotion(caret: Caret, anchor: Int, targetOffset: Int, isInclusive: Boolean = false) {
        if (isInclusive) {
            setInclusiveSelection(caret, anchor, targetOffset)
        } else {
            caret.moveToOffset(targetOffset)
            if (anchor != targetOffset) {
                caret.setSelection(anchor, targetOffset)
            } else {
                caret.removeSelection()
            }
        }
    }

    fun isPairBracket(ch: Char): Boolean = when (ch) {
        '(', ')', '[', ']', '{', '}', '<', '>' -> true
        else -> false
    }

    fun applyBracketMotion(
        caret: Caret,
        fromBracket: Int,
        toBracket: Int,
        textLen: Int,
        isSelect: Boolean,
        hadSelection: Boolean,
        prevSelectionStart: Int,
        prevSelectionEnd: Int,
    ) {
        if (!isSelect) {
            caret.moveToOffset(toBracket)
            caret.removeSelection()
            return
        }

        val start = if (hadSelection) {
            minOf(prevSelectionStart, fromBracket, toBracket)
        } else {
            minOf(fromBracket, toBracket)
        }

        val end = if (hadSelection) {
            maxOf(prevSelectionEnd, fromBracket + 1, toBracket + 1).coerceAtMost(textLen)
        } else {
            (maxOf(fromBracket, toBracket) + 1).coerceAtMost(textLen)
        }

        if (toBracket >= fromBracket) {
            caret.moveToOffset(end)
            caret.setSelection(start, end)
        } else {
            caret.moveToOffset(start)
            caret.setSelection(end, start)
        }
    }

    fun getLineEndWithNewline(doc: Document, line: Int): Int = if (line + 1 < doc.lineCount) {
        doc.getLineStartOffset(line + 1)
    } else {
        doc.getLineEndOffset(line)
    }

    fun isLineEnding(c: Char): Boolean = c == '\n' || c == '\r'

    fun isHorizontalWhitespace(c: Char): Boolean = c.isWhitespace() && !isLineEnding(c)
}
