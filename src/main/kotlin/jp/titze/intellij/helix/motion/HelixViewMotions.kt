package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType

object HelixViewMotions {

    private const val DEFAULT_PAGE_SIZE = 25

    /**
     * Helper to compute lines per page in current editor viewport.
     */
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
     * z or c: Vertically center the current cursor line in viewport without moving selection.
     */
    fun alignViewCenter(editor: Editor) {
        val visibleHeight = editor.scrollingModel.visibleArea.height
        if (visibleHeight > 0) {
            val caretY = editor.visualPositionToXY(editor.caretModel.visualPosition).y
            val targetY = caretY - visibleHeight / 2 + editor.lineHeight / 2
            editor.scrollingModel.scrollVertically(targetY.coerceAtLeast(0))
        } else {
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }

    /**
     * t: Align the current cursor line to the top of the viewport.
     */
    fun alignViewTop(editor: Editor) {
        val caretY = editor.visualPositionToXY(editor.caretModel.visualPosition).y
        editor.scrollingModel.scrollVertically(caretY.coerceAtLeast(0))
    }

    /**
     * b: Align the current cursor line to the bottom of the viewport.
     */
    fun alignViewBottom(editor: Editor) {
        val visibleHeight = editor.scrollingModel.visibleArea.height
        val caretY = editor.visualPositionToXY(editor.caretModel.visualPosition).y
        val targetY = caretY - visibleHeight + editor.lineHeight
        editor.scrollingModel.scrollVertically(targetY.coerceAtLeast(0))
    }

    /**
     * m: Horizontally center the current cursor column in the viewport.
     */
    fun alignViewMiddle(editor: Editor) {
        val visibleWidth = editor.scrollingModel.visibleArea.width
        val caretX = editor.visualPositionToXY(editor.caretModel.visualPosition).x
        val targetX = caretX - visibleWidth / 2
        editor.scrollingModel.scrollHorizontally(targetX.coerceAtLeast(0))
    }

    /**
     * j / down: Scroll viewport downwards by [lines] without moving selection.
     */
    fun scrollViewDown(editor: Editor, lines: Int = 1) {
        val delta = lines.coerceAtLeast(1) * editor.lineHeight
        val newY = editor.scrollingModel.verticalScrollOffset + delta
        editor.scrollingModel.scrollVertically(newY.coerceAtLeast(0))
    }

    /**
     * k / up: Scroll viewport upwards by [lines] without moving selection.
     */
    fun scrollViewUp(editor: Editor, lines: Int = 1) {
        val delta = lines.coerceAtLeast(1) * editor.lineHeight
        val newY = editor.scrollingModel.verticalScrollOffset - delta
        editor.scrollingModel.scrollVertically(newY.coerceAtLeast(0))
    }

    /**
     * d / C-d: Scroll viewport down by half a page.
     */
    fun scrollHalfPageDown(editor: Editor, count: Int = 1) {
        val half = (getPageSize(editor) / 2).coerceAtLeast(1)
        scrollViewDown(editor, half * count.coerceAtLeast(1))
    }

    /**
     * u / C-u: Scroll viewport up by half a page.
     */
    fun scrollHalfPageUp(editor: Editor, count: Int = 1) {
        val half = (getPageSize(editor) / 2).coerceAtLeast(1)
        scrollViewUp(editor, half * count.coerceAtLeast(1))
    }

    /**
     * f / C-f: Scroll viewport down by a full page.
     */
    fun scrollPageDown(editor: Editor, count: Int = 1) {
        val full = getPageSize(editor) * count.coerceAtLeast(1)
        scrollViewDown(editor, full)
    }

    /**
     * F / PageUp: Scroll viewport up by a full page.
     */
    fun scrollPageUp(editor: Editor, count: Int = 1) {
        val full = getPageSize(editor) * count.coerceAtLeast(1)
        scrollViewUp(editor, full)
    }
}
