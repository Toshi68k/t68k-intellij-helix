package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
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
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

object HelixSearchPopup {

    private val CARD_BG = JBColor(Color(0xFA, 0xFA, 0xFC), Color(0x15, 0x16, 0x22))
    private val CARD_BORDER = JBColor(Color(0xD8, 0xDC, 0xEA), Color(0x2B, 0x2E, 0x46))
    private val DIVIDER_COLOR = JBColor(Color(0xEA, 0xED, 0xF5), Color(0x23, 0x26, 0x3A))
    private val TITLE_COLOR = JBColor(Color(0x43, 0x38, 0xCA), Color(0xA5, 0xB4, 0xFC))
    private val CANCEL_COLOR = JBColor(Color(0x8A, 0x90, 0xA2), Color(0x64, 0x6C, 0x8E))

    private val KEYCAP_BG = JBColor(Color(0xEE, 0xF2, 0xFC), Color(0x23, 0x26, 0x3E))
    private val KEYCAP_BORDER = JBColor(Color(0xCF, 0xD7, 0xEE), Color(0x38, 0x3D, 0x62))
    private val KEYCAP_FG = JBColor(Color(0x3B, 0x47, 0x90), Color(0xA5, 0xB4, 0xFC))

    private val INPUT_BG = JBColor(Color(0xF0, 0xF3, 0xFA), Color(0x1B, 0x1D, 0x2E))
    private val INPUT_BORDER = JBColor(Color(0xD0, 0xD7, 0xE8), Color(0x32, 0x37, 0x54))
    private val INPUT_FOCUS_BORDER = JBColor(Color(0x63, 0x66, 0xF1), Color(0x81, 0x8C, 0xF8))

    private val HINT_FG = JBColor(Color(0x8E, 0x94, 0xA8), Color(0x6A, 0x72, 0x94))
    private val MATCH_SUCCESS_FG = JBColor(Color(0x15, 0x80, 0x3D), Color(0x34, 0xD3, 0x99))
    private val MATCH_WARN_FG = JBColor(Color(0xB9, 0x1C, 0x1C), Color(0xF8, 0x71, 0x71))

    private class RoundedCardPanel(private val cornerRadius: Int = 12) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(cornerRadius)
            g2.color = CARD_BG
            g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.color = CARD_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.dispose()
            super.paintComponent(g)
        }
    }

    private class KeycapBadge(
        keyText: String,
        private val minWidth: Int = 22,
        private val height: Int = 22,
        fontSize: Float = 11.5f
    ) : JPanel(BorderLayout()) {
        private val label = JBLabel(keyText, SwingConstants.CENTER)

        init {
            isOpaque = false
            label.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(fontSize))
            label.foreground = KEYCAP_FG
            add(label, BorderLayout.CENTER)
            border = JBUI.Borders.empty(0, 5, 2, 5)
        }

        override fun getPreferredSize(): Dimension {
            val pref = super.getPreferredSize()
            val w = maxOf(pref.width, JBUI.scale(minWidth))
            val h = maxOf(pref.height, JBUI.scale(height))
            return Dimension(w, h)
        }

        override fun getMinimumSize(): Dimension = preferredSize
        override fun getMaximumSize(): Dimension = preferredSize

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

    private class InputBoxPanel : JPanel(BorderLayout(JBUI.scale(8), 0)) {
        var isFocused: Boolean = false

        init {
            isOpaque = false
            border = JBUI.Borders.empty(5, 8)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(8)
            g2.color = INPUT_BG
            g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.color = if (isFocused) INPUT_FOCUS_BORDER else INPUT_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.dispose()
            super.paintComponent(g)
        }
    }

    private class DividerPanel : JPanel() {
        init {
            isOpaque = true
            background = DIVIDER_COLOR
            preferredSize = Dimension(Short.MAX_VALUE.toInt(), 1)
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 1)
        }
    }

    fun show(editor: Editor, backward: Boolean = false, count: Int = 1) {
        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) {
            return
        }

        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { show(editor, backward, count) }
            return
        }

        val cardPanel = RoundedCardPanel(cornerRadius = 12)
        cardPanel.border = JBUI.Borders.empty(10, 14, 10, 14)
        cardPanel.preferredSize = Dimension(JBUI.scale(460), JBUI.scale(136))

        // 1. Header Row
        val headerRow = JPanel(BorderLayout())
        headerRow.isOpaque = false

        val titleText = if (backward) "SEARCH BACKWARD" else "SEARCH FORWARD"
        val titleLabel = JBLabel(titleText)
        titleLabel.font = Font(Font.SANS_SERIF, Font.BOLD, JBUI.scaleFontSize(10.5f))
        titleLabel.foreground = TITLE_COLOR

        val cancelLabel = JBLabel("ESC TO CANCEL")
        cancelLabel.font = Font(Font.SANS_SERIF, Font.BOLD, JBUI.scaleFontSize(9.5f))
        cancelLabel.foreground = CANCEL_COLOR

        headerRow.add(titleLabel, BorderLayout.WEST)
        headerRow.add(cancelLabel, BorderLayout.EAST)
        headerRow.border = JBUI.Borders.empty(2, 2, 8, 2)

        // 2. Input Box
        val inputBox = InputBoxPanel()
        val badge = KeycapBadge(if (backward) "?" else "/", minWidth = 24, height = 24, fontSize = 12f)
        val badgeWrapper = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(badge)
        }
        inputBox.add(badgeWrapper, BorderLayout.WEST)

        val textField = JBTextField()
        textField.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(13f))
        textField.emptyText.text = if (backward) "regex or backward search pattern" else "regex or search pattern"
        textField.border = BorderFactory.createEmptyBorder()
        textField.isOpaque = false
        inputBox.add(textField, BorderLayout.CENTER)

        textField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                inputBox.isFocused = true
                inputBox.repaint()
            }
            override fun focusLost(e: FocusEvent?) {
                inputBox.isFocused = false
                inputBox.repaint()
            }
        })

        // 3. Footer Row
        val footerRow = JPanel(BorderLayout())
        footerRow.isOpaque = false
        footerRow.border = JBUI.Borders.empty(6, 2, 2, 2)

        val statusLabel = JBLabel("Type pattern to search")
        statusLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, JBUI.scaleFontSize(11f))
        statusLabel.foreground = HINT_FG
        footerRow.add(statusLabel, BorderLayout.WEST)

        val shortcutsHint = JPanel()
        shortcutsHint.isOpaque = false
        shortcutsHint.layout = BoxLayout(shortcutsHint, BoxLayout.X_AXIS)

        fun createKeyHint(key: String, desc: String): JPanel {
            val p = JPanel()
            p.isOpaque = false
            p.layout = BoxLayout(p, BoxLayout.X_AXIS)
            val kb = KeycapBadge(key, minWidth = 20, height = 20, fontSize = 10.5f)
            val d = JBLabel(" $desc")
            d.font = Font(Font.SANS_SERIF, Font.PLAIN, JBUI.scaleFontSize(10.5f))
            d.foreground = HINT_FG
            p.add(kb)
            p.add(d)
            return p
        }

        shortcutsHint.add(createKeyHint("Enter", "Search"))
        shortcutsHint.add(Box.createHorizontalStrut(JBUI.scale(10)))
        shortcutsHint.add(createKeyHint("Esc", "Cancel"))
        footerRow.add(shortcutsHint, BorderLayout.EAST)

        // Assemble Content Panel
        val contentPanel = JPanel()
        contentPanel.isOpaque = false
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.add(headerRow)
        contentPanel.add(DividerPanel())
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
        contentPanel.add(inputBox)
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
        contentPanel.add(DividerPanel())
        contentPanel.add(footerRow)

        cardPanel.add(contentPanel, BorderLayout.CENTER)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(cardPanel, textField)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(false)
            .setShowBorder(false)
            .setShowShadow(true)
            .createPopup()

        fun updateStatus() {
            val query = textField.text
            if (query.isEmpty()) {
                statusLabel.text = "Type pattern to search"
                statusLabel.foreground = HINT_FG
            } else {
                try {
                    val matches = HelixActions.countDocumentRegexMatches(editor, query)
                    if (matches > 0) {
                        statusLabel.text = "$matches match${if (matches == 1) "" else "es"} found"
                        statusLabel.foreground = MATCH_SUCCESS_FG
                    } else {
                        statusLabel.text = "0 matches found"
                        statusLabel.foreground = MATCH_WARN_FG
                    }
                } catch (e: Exception) {
                    statusLabel.text = "Invalid regex"
                    statusLabel.foreground = MATCH_WARN_FG
                }
            }
        }

        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateStatus()
            override fun removeUpdate(e: DocumentEvent?) = updateStatus()
            override fun changedUpdate(e: DocumentEvent?) = updateStatus()
        })

        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val pattern = textField.text
                        popup.cancel()
                        if (pattern.isNotEmpty()) {
                            HelixActions.search(editor, pattern, backward = backward, count = count)
                        }
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        popup.cancel()
                        e.consume()
                    }
                }
            }
        })

        val project = editor.project
        if (project != null) {
            popup.showCenteredInCurrentWindow(project)
        } else {
            popup.showInBestPositionFor(editor)
        }
    }
}
