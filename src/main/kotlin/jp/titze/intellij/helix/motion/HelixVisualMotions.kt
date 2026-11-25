// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.Key
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixVisualMotions {

    private data class StickyVisualCol(val col: Int, val expectedOffset: Int)
    private val stickyVisualColKey = Key.create<StickyVisualCol>("HELIX_STICKY_VISUAL_COL")

    fun moveVisualDown(editor: Editor, count: Int = 1) {
        moveVisualDelta(editor, count.coerceAtLeast(1))
    }

    fun moveVisualUp(editor: Editor, count: Int = 1) {
        moveVisualDelta(editor, -count.coerceAtLeast(1))
    }

    private fun moveVisualDelta(editor: Editor, delta: Int) {
        val doc = editor.document
        if (doc.textLength == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val maxVisualLine = editor.offsetToVisualPosition(doc.textLength).line

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val vp = caret.visualPosition
            val targetLine = (vp.line + delta).coerceIn(0, maxVisualLine)
            val effectiveDelta = targetLine - vp.line
            if (effectiveDelta != 0) {
                if (isSelect) {
                    val sticky = caret.getUserData(stickyVisualColKey)
                    val targetCol = if (sticky != null && sticky.expectedOffset == caret.offset) {
                        sticky.col
                    } else {
                        vp.column
                    }
                    val anchor = HelixMotionUtils.getHelixAnchor(caret)
                    val targetVisualPos = VisualPosition(targetLine, targetCol)
                    val targetOffset = editor.visualPositionToOffset(targetVisualPos)
                    HelixMotionUtils.applyMotion(caret, anchor, targetOffset, isSelect = true)
                    caret.putUserData(stickyVisualColKey, StickyVisualCol(targetCol, targetOffset))
                } else {
                    caret.moveCaretRelatively(0, effectiveDelta, false, caret == editor.caretModel.primaryCaret)
                    caret.removeSelection()
                }
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }
}
