package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.action.HelixActions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

object HelixSearchPopup {

    fun show(editor: Editor, backward: Boolean = false, count: Int = 1) {
        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) {
            return
        }

        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { show(editor, backward, count) }
            return
        }

        val mainPanel = JPanel(BorderLayout(0, 6))
        mainPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(0x03, 0xC7, 0xD3, 140), Color(0x03, 0xC7, 0xD3, 140)), 1),
            JBUI.Borders.empty(8, 10)
        )
        mainPanel.background = UIUtil.getPanelBackground()
        mainPanel.preferredSize = Dimension(380, 68)

        val promptPanel = JPanel(BorderLayout(8, 0))
        promptPanel.isOpaque = false

        val badgeText = if (backward) " rsearch: " else " search: "
        val promptBadge = JBLabel(badgeText)
        promptBadge.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(13f).toInt())
        promptBadge.foreground = JBColor(Color(0x03, 0xC7, 0xD3), Color(0x03, 0xC7, 0xD3))
        promptBadge.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(0x03, 0xC7, 0xD3, 120), Color(0x03, 0xC7, 0xD3, 120)), 1),
            JBUI.Borders.empty(1, 4)
        )
        promptPanel.add(promptBadge, BorderLayout.WEST)

        val textField = JBTextField()
        textField.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(13f).toInt())
        textField.emptyText.text = if (backward) "regex or backward search pattern" else "regex or search pattern"
        promptPanel.add(textField, BorderLayout.CENTER)
        mainPanel.add(promptPanel, BorderLayout.NORTH)

        val statusLabel = JBLabel("Enter: search | Esc: cancel")
        statusLabel.font = JBUI.Fonts.miniFont()
        statusLabel.foreground = UIUtil.getContextHelpForeground()
        statusLabel.border = JBUI.Borders.emptyTop(2)
        mainPanel.add(statusLabel, BorderLayout.SOUTH)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, textField)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(false)
            .createPopup()

        fun updateStatus() {
            val query = textField.text
            if (query.isEmpty()) {
                statusLabel.text = "Enter: search | Esc: cancel"
                statusLabel.foreground = UIUtil.getContextHelpForeground()
            } else {
                val matches = HelixActions.countDocumentRegexMatches(editor, query)
                statusLabel.text = "$matches match${if (matches == 1) "" else "es"} (Enter: search | Esc: cancel)"
                statusLabel.foreground = if (matches > 0) {
                    JBColor(Color(0x03, 0xC7, 0xD3), Color(0x03, 0xC7, 0xD3))
                } else {
                    UIUtil.getContextHelpForeground()
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
