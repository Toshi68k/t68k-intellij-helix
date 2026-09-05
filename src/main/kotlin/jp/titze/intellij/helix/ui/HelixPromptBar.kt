package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
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
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

enum class HelixPromptType(val badge: String) {
    SEARCH(" search: "),
    RSEARCH(" rsearch: "),
    SELECT(" select: "),
    SPLIT(" split: ")
}

class HelixPromptBar(private val editor: Editor) : JPanel(BorderLayout(8, 0)) {

    private val badgeLabel = JBLabel()
    private val textField = JBTextField()
    private val statusLabel = JBLabel()

    private var currentType: HelixPromptType = HelixPromptType.SEARCH
    private var currentCount: Int = 1
    private var baseSnapshot: List<HelixActions.HelixCaretSnapshot> = emptyList()
    private var isUpdatingPreview = false

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor(Color(0x03, 0xC7, 0xD3, 140), Color(0x03, 0xC7, 0xD3, 140))),
            JBUI.Borders.empty(3, 8)
        )
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), JBUI.scale(28))

        badgeLabel.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(12f))
        badgeLabel.foreground = JBColor(Color(0x03, 0xC7, 0xD3), Color(0x03, 0xC7, 0xD3))
        add(badgeLabel, BorderLayout.WEST)

        textField.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(12f))
        textField.border = BorderFactory.createEmptyBorder()
        textField.isOpaque = false
        add(textField, BorderLayout.CENTER)

        statusLabel.font = JBUI.Fonts.smallFont()
        statusLabel.foreground = UIUtil.getContextHelpForeground()
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
        badgeLabel.text = type.badge

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
            statusLabel.foreground = UIUtil.getContextHelpForeground()
            return
        }

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
    }

    private fun updateStatusText(matches: Int) {
        if (matches > 0) {
            statusLabel.text = "$matches match${if (matches == 1) "" else "es"}"
            statusLabel.foreground = JBColor(Color(0x03, 0xC7, 0xD3), Color(0x03, 0xC7, 0xD3))
        } else {
            statusLabel.text = "no match"
            statusLabel.foreground = JBColor(Color(0xE0, 0x6C, 0x75), Color(0xE0, 0x6C, 0x75))
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
