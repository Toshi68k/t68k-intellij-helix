package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType

object HelixPageMotions {

    private const val DEFAULT_PAGE_SIZE = 25

    fun getPageSize(editor: Editor): Int {
        val visibleHeight = editor.scrollingModel.visibleArea.height
        val lineHeight = editor.lineHeight
        return if (visibleHeight > 0 && lineHeight > 0) {
            (visibleHeight / lineHeight).coerceAtLeast(1)
        } else {
            DEFAULT_PAGE_SIZE
        }
    }

    /**
     * Ctrl-f / PageDown: Move page down
     */
    fun pageDown(editor: Editor, count: Int = 1) {
        val lines = getPageSize(editor) * count.coerceAtLeast(1)
        HelixMotions.moveDown(editor, lines)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    /**
     * Ctrl-b / PageUp: Move page up
     */
    fun pageUp(editor: Editor, count: Int = 1) {
        val lines = getPageSize(editor) * count.coerceAtLeast(1)
        HelixMotions.moveUp(editor, lines)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    /**
     * Ctrl-d: Move half page down
     */
    fun halfPageDown(editor: Editor, count: Int = 1) {
        val half = (getPageSize(editor) / 2).coerceAtLeast(1)
        val lines = half * count.coerceAtLeast(1)
        HelixMotions.moveDown(editor, lines)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    /**
     * Ctrl-u: Move half page up
     */
    fun halfPageUp(editor: Editor, count: Int = 1) {
        val half = (getPageSize(editor) / 2).coerceAtLeast(1)
        val lines = half * count.coerceAtLeast(1)
        HelixMotions.moveUp(editor, lines)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }
}
