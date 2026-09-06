package jp.titze.intellij.helix.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.ui.HelixTheme
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

object HelixCommandPopup {

    val COMMANDS get() = HelixCommands.COMMANDS

    private val CARD_BG get() = HelixTheme.CARD_BG
    private val CARD_BORDER get() = HelixTheme.CARD_BORDER
    private val DIVIDER_COLOR get() = HelixTheme.DIVIDER_COLOR
    private val TITLE_COLOR get() = HelixTheme.TITLE_COLOR
    private val CANCEL_COLOR get() = HelixTheme.CANCEL_COLOR
    private val KEYCAP_BG get() = HelixTheme.KEYCAP_BG
    private val KEYCAP_BORDER get() = HelixTheme.KEYCAP_BORDER
    private val KEYCAP_FG get() = HelixTheme.KEYCAP_FG
    private val ITEM_TEXT_COLOR get() = HelixTheme.ITEM_TEXT_COLOR
    private val ITEM_DESC_COLOR get() = HelixTheme.ITEM_DESC_COLOR
    private val HOVER_BG get() = HelixTheme.HOVER_BG
    private val INPUT_BG get() = HelixTheme.INPUT_BG

    private class RoundedCardPanel(layout: java.awt.LayoutManager) : JPanel(layout) {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = CARD_BG
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(12), JBUI.scale(12))
            g2.color = CARD_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(12), JBUI.scale(12))
            g2.dispose()
        }
    }

    private class InputBoxPanel(layout: java.awt.LayoutManager) : JPanel(layout) {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = INPUT_BG
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
            g2.dispose()
        }
    }

    private class KeycapBadge(text: String) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            add(
                JBLabel(text).apply {
                    font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f))
                    foreground = KEYCAP_FG
                    border = JBUI.Borders.empty(2, 6)
                },
            )
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = KEYCAP_BG
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(4), JBUI.scale(4))
            g2.color = KEYCAP_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(4), JBUI.scale(4))
            g2.dispose()
        }
    }

    fun show(editor: Editor) {
        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) return
        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { show(editor) }
            return
        }

        val mainPanel = RoundedCardPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(JBUI.scale(520), JBUI.scale(300))

        val northPanel = JPanel(BorderLayout())
        northPanel.isOpaque = false

        val headerPanel = JPanel(BorderLayout(JBUI.scale(12), 0))
        headerPanel.isOpaque = false
        headerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER_COLOR),
            JBUI.Borders.empty(12, 14, 10, 14),
        )

        val titleLabel = JBLabel("COMMAND PALETTE")
        titleLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(11.5f).toFloat())
        titleLabel.foreground = TITLE_COLOR
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val cancelLabel = JBLabel("ESC TO CANCEL")
        cancelLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(9.5f).toFloat())
        cancelLabel.foreground = CANCEL_COLOR
        headerPanel.add(cancelLabel, BorderLayout.EAST)
        northPanel.add(headerPanel, BorderLayout.NORTH)

        val inputContainer = JPanel(BorderLayout())
        inputContainer.isOpaque = false
        inputContainer.border = JBUI.Borders.empty(8, 12, 6, 12)

        val inputPanel = InputBoxPanel(BorderLayout(JBUI.scale(8), 0))
        inputPanel.border = JBUI.Borders.empty(4, 6)

        val promptBadge = KeycapBadge(":")
        inputPanel.add(promptBadge, BorderLayout.WEST)

        val textField = JBTextField()
        textField.isOpaque = false
        textField.border = JBUI.Borders.empty(0, 4)
        textField.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(13f))
        textField.foreground = ITEM_TEXT_COLOR
        textField.caretColor = TITLE_COLOR
        textField.emptyText.text = "type command (w, q, wq, vsp, sp, format, ...)"
        inputPanel.add(textField, BorderLayout.CENTER)
        inputContainer.add(inputPanel, BorderLayout.CENTER)
        northPanel.add(inputContainer, BorderLayout.CENTER)

        mainPanel.add(northPanel, BorderLayout.NORTH)

        val listModel = DefaultListModel<HelixCommandItem>()
        COMMANDS.forEach { listModel.addElement(it) }

        val list = JBList(listModel)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        list.isOpaque = false
        list.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        list.cellRenderer = object : ListCellRenderer<HelixCommandItem> {
            private val cellPanel = object : JPanel(BorderLayout(JBUI.scale(10), 0)) {
                var isSelectedRow = false

                init {
                    isOpaque = false
                    border = JBUI.Borders.empty(4, 8)
                }

                override fun paintComponent(g: Graphics) {
                    if (isSelectedRow) {
                        val g2 = g.create() as Graphics2D
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2.color = HOVER_BG
                        g2.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
                        g2.dispose()
                    }
                    super.paintComponent(g)
                }
            }

            private val badgeHolder = JPanel(BorderLayout()).apply { isOpaque = false }
            private val textPanel = JPanel(BorderLayout(JBUI.scale(6), 0)).apply { isOpaque = false }
            private val descLabel = JBLabel().apply {
                font = JBUI.Fonts.label().deriveFont(Font.PLAIN, JBUI.scaleFontSize(12.5f).toFloat())
                foreground = ITEM_TEXT_COLOR
            }
            private val aliasLabel = JBLabel().apply {
                font = JBUI.Fonts.smallFont()
                foreground = ITEM_DESC_COLOR
            }

            init {
                cellPanel.add(badgeHolder, BorderLayout.WEST)
                textPanel.add(descLabel, BorderLayout.WEST)
                textPanel.add(aliasLabel, BorderLayout.EAST)
                cellPanel.add(textPanel, BorderLayout.CENTER)
            }

            override fun getListCellRendererComponent(
                list: JList<out HelixCommandItem>?,
                value: HelixCommandItem?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                cellPanel.isSelectedRow = isSelected
                badgeHolder.removeAll()
                val cmdName = value?.name ?: ""
                badgeHolder.add(KeycapBadge(":$cmdName"), BorderLayout.CENTER)

                descLabel.text = value?.description ?: ""
                aliasLabel.text = if (value != null && value.aliases.isNotEmpty()) {
                    "alias: :${value.aliases.joinToString(", :")}"
                } else {
                    ""
                }
                cellPanel.accessibleContext.accessibleName =
                    value?.let { "${it.displayCommand}: ${it.description}" } ?: ""
                return cellPanel
            }
        }

        val scrollPane = JBScrollPane(list)
        scrollPane.border = JBUI.Borders.empty(0, 6)
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
        scrollPane.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val footerPanel = JPanel(BorderLayout())
        footerPanel.isOpaque = false
        footerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER_COLOR),
            JBUI.Borders.empty(8, 14, 10, 14),
        )
        val footerLabel = JBLabel("Enter: run | Tab: complete | ↑/↓: select | Esc: cancel")
        footerLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(9.5f).toFloat())
        footerLabel.foreground = CANCEL_COLOR
        footerPanel.add(footerLabel, BorderLayout.WEST)
        mainPanel.add(footerPanel, BorderLayout.SOUTH)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, textField)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(false)
            .setShowBorder(false)
            .setShowShadow(true)
            .createPopup()

        fun updateFilter() {
            val query = textField.text.trim().removePrefix(":")
            listModel.clear()
            for (cmd in COMMANDS) {
                if (cmd.matches(query)) {
                    listModel.addElement(cmd)
                }
            }
            if (!listModel.isEmpty) {
                list.selectedIndex = 0
            }
        }

        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateFilter()
            override fun removeUpdate(e: DocumentEvent?) = updateFilter()
            override fun changedUpdate(e: DocumentEvent?) = updateFilter()
        })

        fun runChosenCommand() {
            val query = textField.text.trim().removePrefix(":")
            popup.cancel()
            val selected = list.selectedValue
            if (selected != null && (query.isEmpty() || selected.matches(query))) {
                selected.action(editor)
            } else if (query.isNotEmpty()) {
                executeCommand(query, editor)
            }
        }

        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        runChosenCommand()
                        e.consume()
                    }

                    KeyEvent.VK_ESCAPE -> {
                        popup.cancel()
                        e.consume()
                    }

                    KeyEvent.VK_DOWN -> {
                        if (listModel.size > 0) {
                            val next = (list.selectedIndex + 1).coerceAtMost(listModel.size - 1)
                            list.selectedIndex = next
                            list.ensureIndexIsVisible(next)
                        }
                        e.consume()
                    }

                    KeyEvent.VK_UP -> {
                        if (listModel.size > 0) {
                            val prev = (list.selectedIndex - 1).coerceAtLeast(0)
                            list.selectedIndex = prev
                            list.ensureIndexIsVisible(prev)
                        }
                        e.consume()
                    }

                    KeyEvent.VK_TAB -> {
                        val selected = list.selectedValue
                        if (selected != null) {
                            textField.text = selected.name
                        }
                        e.consume()
                    }

                    KeyEvent.VK_N -> {
                        if (e.isControlDown && listModel.size > 0) {
                            val next = (list.selectedIndex + 1).coerceAtMost(listModel.size - 1)
                            list.selectedIndex = next
                            list.ensureIndexIsVisible(next)
                            e.consume()
                        }
                    }

                    KeyEvent.VK_P -> {
                        if (e.isControlDown && listModel.size > 0) {
                            val prev = (list.selectedIndex - 1).coerceAtLeast(0)
                            list.selectedIndex = prev
                            list.ensureIndexIsVisible(prev)
                            e.consume()
                        }
                    }
                }
            }
        })

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount >= 1 && list.selectedValue != null) {
                    popup.cancel()
                    list.selectedValue.action(editor)
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

    fun executeCommand(cmd: String, editor: Editor) = HelixCommands.execute(cmd, editor)
}
