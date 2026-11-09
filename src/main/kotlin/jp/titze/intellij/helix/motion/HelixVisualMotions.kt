// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixVisualMotions {

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
                caret.moveCaretRelatively(0, effectiveDelta, isSelect, caret == editor.caretModel.primaryCaret)
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }
}
