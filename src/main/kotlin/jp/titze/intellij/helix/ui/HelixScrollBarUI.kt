// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.ui

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.plaf.basic.BasicScrollBarUI

/**
 * Custom scrollbar UI matching the modern Helix floating card design.
 * Provides a 100% transparent track so the card background ([HelixTheme.CARD_BG])
 * shows through seamlessly, eliminating the stark white track clash on macOS / Light IDE themes.
 * Renders a sleek, semi-transparent rounded pill thumb with proper edge insets to prevent
 * clipping rounded card borders.
 */
class HelixScrollBarUI : BasicScrollBarUI() {

    override fun configureScrollBarColors() {
        thumbColor = Color(0, 0, 0, 0)
        trackColor = Color(0, 0, 0, 0)
    }

    override fun createDecreaseButton(orientation: Int): JButton = createZeroButton()
    override fun createIncreaseButton(orientation: Int): JButton = createZeroButton()

    private fun createZeroButton(): JButton = JButton().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        maximumSize = Dimension(0, 0)
        isOpaque = false
        isContentAreaFilled = false
        border = null
    }

    override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {
        // Transparent track: do not paint an opaque background
    }

    override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle) {
        if (thumbBounds.isEmpty || !scrollbar.isEnabled) return

        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val isDark = HelixTheme.isDark
            val alpha = when {
                isDragging -> if (isDark) 160 else 180
                isThumbRollover -> if (isDark) 130 else 140
                else -> if (isDark) 75 else 90
            }
            g2.color = if (isDark) Color(255, 255, 255, alpha) else Color(0, 0, 0, alpha)

            val isVertical = scrollbar.orientation == JScrollBar.VERTICAL
            if (isVertical) {
                val pillWidth = JBUI.scale(5)
                val rightMargin = JBUI.scale(3)
                val x = thumbBounds.x + thumbBounds.width - pillWidth - rightMargin
                val y = thumbBounds.y + JBUI.scale(2)
                val height = (thumbBounds.height - JBUI.scale(4)).coerceAtLeast(JBUI.scale(16))
                g2.fillRoundRect(x, y, pillWidth, height, pillWidth, pillWidth)
            } else {
                val pillHeight = JBUI.scale(5)
                val bottomMargin = JBUI.scale(3)
                val x = thumbBounds.x + JBUI.scale(2)
                val y = thumbBounds.y + thumbBounds.height - pillHeight - bottomMargin
                val width = (thumbBounds.width - JBUI.scale(4)).coerceAtLeast(JBUI.scale(16))
                g2.fillRoundRect(x, y, width, pillHeight, pillHeight, pillHeight)
            }
        } finally {
            g2.dispose()
        }
    }

    override fun getPreferredSize(c: JComponent): Dimension {
        val isVertical = scrollbar.orientation == JScrollBar.VERTICAL
        return if (isVertical) {
            Dimension(JBUI.scale(10), super.getPreferredSize(c).height)
        } else {
            Dimension(super.getPreferredSize(c).width, JBUI.scale(10))
        }
    }

    companion object {
        fun apply(scrollPane: JBScrollPane, list: com.intellij.ui.components.JBList<*>? = null) {
            scrollPane.isOpaque = false
            scrollPane.viewport.isOpaque = false
            scrollPane.verticalScrollBar?.apply {
                isOpaque = false
                setUI(HelixScrollBarUI())
            }
            scrollPane.horizontalScrollBar?.apply {
                isOpaque = false
                setUI(HelixScrollBarUI())
            }
            if (list != null) {
                HelixListUI.install(list)
            }
        }
    }
}
