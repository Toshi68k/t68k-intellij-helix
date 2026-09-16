package jp.titze.intellij.helix.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Timer

object HelixVisualFeedback {

    const val FLASH_DURATION_MS = 150

    private val activeHighlighters = ConcurrentHashMap<Editor, Pair<Timer, List<RangeHighlighter>>>()

    fun flashYank(editor: Editor, ranges: List<TextRange>) {
        if (ranges.isEmpty() || editor.isDisposed) return

        clearFlash(editor)

        val markupModel = editor.markupModel
        val attributes = TextAttributes().apply {
            backgroundColor = HelixTheme.YANK_FLASH_BG
        }

        val highlighters = mutableListOf<RangeHighlighter>()
        for (range in ranges) {
            if (range.startOffset < range.endOffset && range.endOffset <= editor.document.textLength) {
                val highlighter = markupModel.addRangeHighlighter(
                    range.startOffset,
                    range.endOffset,
                    HighlighterLayer.SELECTION + 100,
                    attributes,
                    HighlighterTargetArea.EXACT_RANGE,
                )
                highlighter.customRenderer = CustomHighlighterRenderer { ed, hl, g ->
                    paintFlashRange(ed, hl.startOffset, hl.endOffset, g)
                }
                highlighters.add(highlighter)
            }
        }

        if (highlighters.isEmpty()) return

        editor.contentComponent.repaint()

        val timer = Timer(FLASH_DURATION_MS) {
            clearFlash(editor)
        }.apply {
            isRepeats = false
            start()
        }

        activeHighlighters[editor] = timer to highlighters
    }

    private fun paintFlashRange(editor: Editor, startOffset: Int, endOffset: Int, g: Graphics) {
        val doc = editor.document
        if (doc.textLength == 0 || startOffset >= endOffset) return

        val clampedStart = startOffset.coerceIn(0, doc.textLength)
        val clampedEnd = endOffset.coerceIn(0, doc.textLength)
        if (clampedStart >= clampedEnd) return

        val g2d = g.create() as? Graphics2D ?: return
        try {
            g2d.color = HelixTheme.YANK_FLASH_BG
            val startLine = doc.getLineNumber(clampedStart)
            val endLine = doc.getLineNumber(clampedEnd)
            val lineHeight = editor.lineHeight
            val charWidth = (lineHeight / 2).coerceAtLeast(6)

            for (line in startLine..endLine) {
                val lineStart = doc.getLineStartOffset(line)
                val lineEnd = doc.getLineEndOffset(line)

                val segStart = clampedStart.coerceAtLeast(lineStart)
                val segEnd = clampedEnd.coerceAtMost(lineEnd)

                val pStart = editor.visualPositionToXY(editor.offsetToVisualPosition(segStart))
                val pEnd = if (segEnd > segStart) {
                    editor.visualPositionToXY(editor.offsetToVisualPosition(segEnd))
                } else {
                    java.awt.Point(pStart.x + charWidth / 2, pStart.y)
                }

                val width = (pEnd.x - pStart.x).coerceAtLeast(charWidth / 2)
                g2d.fillRect(pStart.x, pStart.y, width, lineHeight)
            }
        } finally {
            g2d.dispose()
        }
    }

    fun hasActiveFlash(editor: Editor): Boolean = activeHighlighters.containsKey(editor)

    fun getActiveHighlighters(editor: Editor): List<RangeHighlighter> =
        activeHighlighters[editor]?.second ?: emptyList()

    fun clearFlash(editor: Editor) {
        val (timer, highlighters) = activeHighlighters.remove(editor) ?: return
        timer.stop()
        if (!editor.isDisposed) {
            val markupModel = editor.markupModel
            for (highlighter in highlighters) {
                try {
                    markupModel.removeHighlighter(highlighter)
                } catch (_: Exception) {
                    // Document or editor might have been modified concurrently
                }
            }
            editor.contentComponent.repaint()
        }
    }

    fun clearAll() {
        for (editor in activeHighlighters.keys) {
            clearFlash(editor)
        }
    }
}
