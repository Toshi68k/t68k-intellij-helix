package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.action.HelixActions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

enum class HelixPromptType(val badge: String) {
    SEARCH("search"),
    RSEARCH("rsearch"),
    SELECT("select"),
    SPLIT("split")
}

class HelixPromptBar(private val editor: Editor) : JPanel(BorderLayout(JBUI.scale(8), 0)) {

    private val badgeBadge = KeycapBadge("search")
    private val textField = JBTextField()
    private val statusLabel = JBLabel()

    private var currentType: HelixPromptType = HelixPromptType.SEARCH
    private var currentCount: Int = 1
    private var baseSnapshot: List<HelixActions.HelixCaretSnapshot> = emptyList()
    private var isUpdatingPreview = false

    private class KeycapBadge(keyText: String) : JPanel(BorderLayout()) {
        private val label = JBLabel(keyText, SwingConstants.CENTER)

        init {
            isOpaque = false
            label.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f))
            label.foreground = KEYCAP_FG
            add(label, BorderLayout.CENTER)
            border = JBUI.Borders.empty(1, 8)
        }

        fun setText(text: String) {
            label.text = text
            revalidate()
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(6)
            g2.color = KEYCAP_BG
            g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.color = KEYCAP_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.dispose()
            super.paintComponent(g)
        }
    }

    init {
        isOpaque = true
        background = CARD_BG
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, CARD_BORDER),
            JBUI.Borders.empty(4, 10)
        )
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), JBUI.scale(32))

        add(badgeBadge, BorderLayout.WEST)

        textField.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(12.5f))
        textField.border = BorderFactory.createEmptyBorder()
        textField.isOpaque = false
        add(textField, BorderLayout.CENTER)

        statusLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, JBUI.scaleFontSize(11f))
        statusLabel.foreground = HINT_FG
        add(statusLabel, BorderLayout.EAST)

        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updatePreview()
            override fun removeUpdate(e: DocumentEvent?) = updatePreview()
            override fun changedUpdate(e: DocumentEvent?) = updatePreview()
        })

        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        commitAndClose()
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        cancelAndClose()
                        e.consume()
                    }
                    KeyEvent.VK_BACK_SPACE -> {
                        if (textField.text.isEmpty()) {
                            cancelAndClose()
                            e.consume()
                        }
                    }
                }
            }
        })

        textField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) {
                // If focus moves away from prompt bar and editor, keep state or allow cancel
            }
        })
    }

    fun show(type: HelixPromptType, count: Int = 1) {
        currentType = type
        currentCount = count
        baseSnapshot = HelixActions.captureCarets(editor)
        badgeBadge.setText(type.badge)

        isUpdatingPreview = true
        textField.text = ""
        statusLabel.text = ""
        isUpdatingPreview = false

        isVisible = true
        revalidate()
        repaint()

        SwingUtilities.invokeLater {
            textField.requestFocusInWindow()
        }
    }

    private fun updatePreview() {
        if (isUpdatingPreview) return
        val query = textField.text

        if (query.isEmpty()) {
            HelixActions.restoreCarets(editor, baseSnapshot)
            statusLabel.text = ""
            statusLabel.foreground = HINT_FG
            return
        }

        try {
            when (currentType) {
                HelixPromptType.SEARCH -> {
                    HelixActions.previewSearch(editor, query, backward = false, count = currentCount, baseSnapshot = baseSnapshot)
                    val matches = HelixActions.countDocumentRegexMatches(editor, query)
                    updateStatusText(matches)
                }
                HelixPromptType.RSEARCH -> {
                    HelixActions.previewSearch(editor, query, backward = true, count = currentCount, baseSnapshot = baseSnapshot)
                    val matches = HelixActions.countDocumentRegexMatches(editor, query)
                    updateStatusText(matches)
                }
                HelixPromptType.SELECT -> {
                    HelixActions.previewSelectRegex(editor, query, baseSnapshot = baseSnapshot)
                    val matches = HelixActions.countRegexMatchesInSnapshot(editor, query, baseSnapshot)
                    updateStatusText(matches)
                }
                HelixPromptType.SPLIT -> {
                    HelixActions.previewSplitRegex(editor, query, baseSnapshot = baseSnapshot)
                    val matches = HelixActions.countRegexMatchesInSnapshot(editor, query, baseSnapshot)
                    updateStatusText(matches)
                }
            }
        } catch (e: Exception) {
            statusLabel.text = "invalid regex"
            statusLabel.foreground = MATCH_WARN_FG
        }
    }

    private fun updateStatusText(matches: Int) {
        if (matches > 0) {
            statusLabel.text = "$matches match${if (matches == 1) "" else "es"}"
            statusLabel.foreground = MATCH_SUCCESS_FG
        } else {
            statusLabel.text = "0 matches"
            statusLabel.foreground = MATCH_WARN_FG
        }
    }

    fun commitAndClose() {
        val query = textField.text
        if (query.isNotEmpty()) {
            if (currentType == HelixPromptType.SEARCH) {
                HelixActions.lastSearchPattern = query
                HelixActions.lastSearchBackward = false
            } else if (currentType == HelixPromptType.RSEARCH) {
                HelixActions.lastSearchPattern = query
                HelixActions.lastSearchBackward = true
            }
        }
        hideBar()
    }

    fun cancelAndClose() {
        HelixActions.restoreCarets(editor, baseSnapshot)
        hideBar()
    }

    private fun hideBar() {
        isVisible = false
        editor.component.revalidate()
        editor.component.repaint()
        editor.contentComponent.requestFocusInWindow()
    }

    companion object {
        private val CARD_BG = JBColor(Color(0xFA, 0xFA, 0xFC), Color(0x15, 0x16, 0x22))
        private val CARD_BORDER = JBColor(Color(0xD8, 0xDC, 0xEA), Color(0x2B, 0x2E, 0x46))
        private val KEYCAP_BG = JBColor(Color(0xEE, 0xF2, 0xFC), Color(0x23, 0x26, 0x3E))
        private val KEYCAP_BORDER = JBColor(Color(0xCF, 0xD7, 0xEE), Color(0x38, 0x3D, 0x62))
        private val KEYCAP_FG = JBColor(Color(0x3B, 0x47, 0x90), Color(0xA5, 0xB4, 0xFC))
        private val MATCH_SUCCESS_FG = JBColor(Color(0x15, 0x80, 0x3D), Color(0x34, 0xD3, 0x99))
        private val MATCH_WARN_FG = JBColor(Color(0xB9, 0x1C, 0x1C), Color(0xF8, 0x71, 0x71))
        private val HINT_FG = JBColor(Color(0x8E, 0x94, 0xA8), Color(0x6A, 0x72, 0x94))

        private val PROMPT_BAR_KEY = Key.create<HelixPromptBar>("HelixPromptBar")

        fun getOrCreate(editor: Editor): HelixPromptBar {
            var bar = editor.getUserData(PROMPT_BAR_KEY)
            if (bar == null) {
                bar = HelixPromptBar(editor)
                editor.putUserData(PROMPT_BAR_KEY, bar)
                editor.component.add(bar, BorderLayout.SOUTH)
                editor.component.revalidate()
            }
            return bar
        }

        fun show(editor: Editor, type: HelixPromptType, count: Int = 1) {
            val app = ApplicationManager.getApplication()
            if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) {
                return
            }

            if (app != null && !app.isDispatchThread) {
                SwingUtilities.invokeLater { show(editor, type, count) }
                return
            }

            val bar = getOrCreate(editor)
            bar.show(type, count)
        }

        fun cancelActivePrompt(editor: Editor): Boolean {
            val bar = editor.getUserData(PROMPT_BAR_KEY)
            if (bar != null && bar.isVisible) {
                bar.cancelAndClose()
                return true
            }
            return false
        }
    }
}
