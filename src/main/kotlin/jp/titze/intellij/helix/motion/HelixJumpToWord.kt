package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.Key
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import javax.swing.JComponent
import javax.swing.SwingUtilities

object HelixJumpToWord {

    private const val ALPHABET = "jfkdlsaurieowpqnvmcxz"
    private const val BADGE_ARC = 4
    private const val BADGE_PADDING_X = 4

    private val SESSION_KEY = Key.create<JumpSession>("HelixJumpToWordSession")

    data class JumpTarget(val offset: Int, val endOffset: Int, val label: String)

    class JumpSession(
        val editor: Editor,
        val targets: List<JumpTarget>,
        val overlay: JumpOverlay,
        val typedPrefix: StringBuilder = StringBuilder(),
    )

    fun isActive(editor: Editor): Boolean = editor.getUserData(SESSION_KEY) != null

    fun start(editor: Editor): Boolean {
        cancel(editor)

        val wordRanges = findVisibleWordRanges(editor)
        if (wordRanges.isEmpty()) return false

        val labels = generateLabels(wordRanges.size)
        val targets = wordRanges.zip(labels) { (start, end), label ->
            JumpTarget(start, end, label)
        }

        val overlay = JumpOverlay(editor)
        val session = JumpSession(
            editor = editor,
            targets = targets,
            overlay = overlay,
        )
        editor.putUserData(SESSION_KEY, session)
        editor.contentComponent.add(overlay)
        editor.contentComponent.setComponentZOrder(overlay, 0)
        overlay.updateBounds()
        overlay.repaint()
        return true
    }

    fun handleKey(charTyped: Char, editor: Editor): Boolean {
        val session = editor.getUserData(SESSION_KEY) ?: return false

        session.typedPrefix.append(charTyped)
        val prefix = session.typedPrefix.toString()
        val matches = session.targets.filter { it.label.startsWith(prefix) }

        if (matches.isEmpty()) {
            cancel(editor)
            return true
        }

        if (matches.size == 1 && matches.first().label == prefix) {
            val target = matches.first()
            cancel(editor)
            executeJump(editor, target)
            return true
        }

        session.overlay.repaint()
        return true
    }

    fun handleBackspace(editor: Editor): Boolean {
        val session = editor.getUserData(SESSION_KEY) ?: return false

        if (session.typedPrefix.isEmpty()) {
            cancel(editor)
            return true
        }

        session.typedPrefix.setLength(session.typedPrefix.length - 1)
        session.overlay.repaint()
        return true
    }

    fun cancel(editor: Editor): Boolean {
        val session = editor.getUserData(SESSION_KEY) ?: return false
        editor.putUserData(SESSION_KEY, null)
        editor.contentComponent.remove(session.overlay)
        editor.contentComponent.repaint()
        return true
    }

    private fun executeJump(editor: Editor, target: JumpTarget) {
        HelixKeyHandler.recordJump(editor)
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        val caret = editor.caretModel.primaryCaret

        if (isSelect) {
            val anchor = if (caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            val start = minOf(anchor, target.offset)
            val end = maxOf(anchor, target.endOffset)
            val caretOffset = if (target.offset >= anchor) end else start
            caret.moveToOffset(caretOffset)
            caret.setSelection(start, end)
        } else {
            caret.moveToOffset(target.endOffset)
            caret.setSelection(target.offset, target.endOffset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    internal fun generateLabels(count: Int): List<String> {
        if (count <= 0) return emptyList()
        if (count <= ALPHABET.length) {
            return ALPHABET.take(count).map { it.toString() }
        }

        val labels = ArrayList<String>(count)
        for (c1 in ALPHABET) {
            for (c2 in ALPHABET) {
                labels.add("$c1$c2")
                if (labels.size == count) return labels
            }
        }
        return labels
    }

    private fun findVisibleWordRanges(editor: Editor): List<Pair<Int, Int>> {
        val document = editor.document
        val text = document.charsSequence
        val textLen = text.length
        if (textLen == 0) return emptyList()

        val visibleArea = editor.scrollingModel.visibleArea
        val (startLine, endLine) = if (visibleArea.height > 0) {
            val startY = visibleArea.y
            val endY = visibleArea.y + visibleArea.height
            val startL = editor.xyToLogicalPosition(Point(0, startY)).line
            val endL = editor.xyToLogicalPosition(Point(0, endY)).line
            Pair(
                startL.coerceIn(0, document.lineCount - 1),
                endL.coerceIn(0, document.lineCount - 1),
            )
        } else {
            // Headless / unit test fallback: scan document lines
            Pair(0, document.lineCount - 1)
        }

        val wordRegex = Regex("""\b\w+""")
        val result = mutableListOf<Pair<Int, Int>>()

        for (line in startLine..endLine) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            if (!isLineVisible(editor, lineStart, lineEnd)) continue

            val lineText = text.subSequence(lineStart, lineEnd)
            for (match in wordRegex.findAll(lineText)) {
                val wordOffset = lineStart + match.range.first
                val wordEndOffset = lineStart + match.range.last + 1
                if (isWordVisible(editor, wordOffset, visibleArea.width, visibleArea.x)) {
                    result.add(Pair(wordOffset, wordEndOffset))
                }
            }
        }
        return result
    }

    private fun isLineVisible(editor: Editor, lineStart: Int, lineEnd: Int): Boolean {
        if (lineEnd <= lineStart) return false
        val folding = editor.foldingModel
        return !(folding.isOffsetCollapsed(lineStart) && folding.isOffsetCollapsed(lineEnd - 1))
    }

    private fun isWordVisible(editor: Editor, offset: Int, visibleWidth: Int, visibleX: Int): Boolean {
        if (editor.foldingModel.isOffsetCollapsed(offset)) return false
        if (visibleWidth <= 0) return true
        val p = editor.offsetToPoint2D(offset)
        return p.x >= visibleX && p.x <= visibleX + visibleWidth
    }

    class JumpOverlay(private val editor: Editor) : JComponent() {

        init {
            isOpaque = false
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    cancel(editor)
                    val parent = parent ?: return
                    val pt = SwingUtilities.convertPoint(this@JumpOverlay, e.point, parent)
                    parent.dispatchEvent(
                        MouseEvent(
                            parent,
                            e.id,
                            e.`when`,
                            e.modifiersEx,
                            pt.x,
                            pt.y,
                            e.clickCount,
                            e.isPopupTrigger,
                            e.button,
                        ),
                    )
                }
            })
        }

        fun updateBounds() {
            val content = editor.contentComponent
            val w = maxOf(content.width, editor.scrollingModel.visibleArea.width, 1000)
            val h = maxOf(content.height, editor.scrollingModel.visibleArea.height, 1000)
            bounds = Rectangle(0, 0, w, h)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            updateBounds()

            val session = editor.getUserData(SESSION_KEY) ?: return
            val prefix = session.typedPrefix.toString()
            val targets = if (prefix.isEmpty()) {
                session.targets
            } else {
                session.targets.filter { it.label.startsWith(prefix) }
            }
            if (targets.isEmpty()) return

            val g2d = g.create() as? Graphics2D ?: return
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                val font = editor.colorsScheme.getFont(EditorFontType.BOLD)
                g2d.font = font
                val fm = g2d.fontMetrics

                val visibleArea = editor.scrollingModel.visibleArea
                val lineHeight = editor.lineHeight
                val badgeHeight = (lineHeight - 2).coerceAtLeast(fm.height)

                // High contrast badge colors:
                // Solid bright amber with dark border, bold black text
                val bg = Color(0xFA, 0xCC, 0x15) // vibrant yellow/amber (solid, no bleed)
                val border = Color(0xB4, 0x53, 0x09)
                val activeFg = Color(0x18, 0x18, 0x1B) // bold near-black
                val prefixFg = Color(0xA1, 0x62, 0x07) // dimmed amber for typed prefix

                for (target in targets) {
                    val p = editor.offsetToPoint2D(target.offset)
                    if (!isBadgeInVisibleArea(p, lineHeight, visibleArea)) continue

                    val x = p.x.toInt()
                    val y = p.y.toInt() + 1
                    val textWidth = fm.stringWidth(target.label)
                    val badgeWidth = textWidth + BADGE_PADDING_X * 2

                    // Solid opaque background to completely cover the underlying code characters!
                    g2d.color = bg
                    g2d.fillRoundRect(x, y, badgeWidth, badgeHeight, BADGE_ARC, BADGE_ARC)
                    g2d.color = border
                    g2d.drawRoundRect(x, y, badgeWidth - 1, badgeHeight - 1, BADGE_ARC, BADGE_ARC)

                    val textY = y + (badgeHeight - fm.height) / 2 + fm.ascent
                    val textX = x + BADGE_PADDING_X

                    if (prefix.isNotEmpty() && target.label.startsWith(prefix)) {
                        val prefixWidth = fm.stringWidth(prefix)
                        g2d.color = prefixFg
                        g2d.drawString(prefix, textX, textY)
                        val remaining = target.label.substring(prefix.length)
                        g2d.color = activeFg
                        g2d.drawString(remaining, textX + prefixWidth, textY)
                    } else {
                        g2d.color = activeFg
                        g2d.drawString(target.label, textX, textY)
                    }
                }
            } finally {
                g2d.dispose()
            }
        }

        private fun isBadgeInVisibleArea(p: Point2D, lineHeight: Int, area: Rectangle): Boolean {
            if (area.height <= 0) return true
            if (p.y + lineHeight < area.y || p.y > area.y + area.height) return false
            return !(p.x + 50 < area.x || p.x > area.x + area.width)
        }
    }
}
