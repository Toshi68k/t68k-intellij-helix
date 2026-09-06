package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixFindCharMotions {

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

        HelixMotionUtils.runForEachCaret(editor) { caret ->
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
        count: Int,
    ): Int? {
        val steps = count.coerceAtLeast(1)
        return when (motion) {
            't', 'f' -> findForwardTargetOffset(text, textLen, curOffset, motion == 't', targetChar, steps)
            'T', 'F' -> findBackwardTargetOffset(text, curOffset, motion == 'T', targetChar, steps)
            else -> null
        }
    }

    private fun findForwardTargetOffset(
        text: CharSequence,
        textLen: Int,
        curOffset: Int,
        till: Boolean,
        targetChar: Char,
        count: Int,
    ): Int? {
        var remainingCount = count
        var idx = curOffset + 1
        while (idx < textLen) {
            if (text[idx] == targetChar) {
                remainingCount--
                if (remainingCount == 0) {
                    return if (till) idx else (idx + 1).coerceAtMost(textLen)
                }
            }
            idx++
        }
        return null
    }

    private fun findBackwardTargetOffset(
        text: CharSequence,
        curOffset: Int,
        till: Boolean,
        targetChar: Char,
        count: Int,
    ): Int? {
        var remainingCount = count
        var idx = curOffset - 1
        while (idx >= 0) {
            if (text[idx] == targetChar) {
                remainingCount--
                if (remainingCount == 0) {
                    return if (till) idx + 1 else idx
                }
            }
            idx--
        }
        return null
    }
}
