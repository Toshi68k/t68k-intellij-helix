package jp.titze.intellij.helix.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.ui.HelixJumplistPopup
import java.awt.BorderLayout
import java.awt.Color
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

data class HelixCommandItem(
    val name: String,
    val aliases: List<String>,
    val description: String,
    val action: (Editor) -> Unit
) {
    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        if (name.lowercase().contains(q)) return true
        if (aliases.any { it.lowercase().contains(q) }) return true
        if (description.lowercase().contains(q)) return true
        return false
    }

    val displayCommand: String
        get() = if (aliases.isNotEmpty()) ":$name (:${aliases.joinToString(", :")})" else ":$name"
}

object HelixCommandPopup {

    private val CARD_BG = JBColor(Color(0xFA, 0xFA, 0xFC), Color(0x15, 0x16, 0x22))
    private val CARD_BORDER = JBColor(Color(0xD8, 0xDC, 0xEA), Color(0x2B, 0x2E, 0x46))
    private val DIVIDER_COLOR = JBColor(Color(0xEA, 0xED, 0xF5), Color(0x23, 0x26, 0x3A))
    private val TITLE_COLOR = JBColor(Color(0x43, 0x38, 0xCA), Color(0xA5, 0xB4, 0xFC))
    private val CANCEL_COLOR = JBColor(Color(0x8A, 0x90, 0xA2), Color(0x64, 0x6C, 0x8E))
    private val KEYCAP_BG = JBColor(Color(0xEE, 0xF2, 0xFC), Color(0x23, 0x26, 0x3E))
    private val KEYCAP_BORDER = JBColor(Color(0xCF, 0xD7, 0xEE), Color(0x38, 0x3D, 0x62))
    private val KEYCAP_FG = JBColor(Color(0x3B, 0x47, 0x90), Color(0xA5, 0xB4, 0xFC))
    private val ITEM_TEXT_COLOR = JBColor(Color(0x22, 0x24, 0x30), Color(0xDF, 0xE2, 0xEE))
    private val ITEM_DESC_COLOR = JBColor(Color(0x8E, 0x94, 0xA8), Color(0x6A, 0x72, 0x94))
    private val HOVER_BG = JBColor(Color(0xF0, 0xF3, 0xFA), Color(0x20, 0x23, 0x38))
    private val INPUT_BG = JBColor(Color(0xF0, 0xF3, 0xFA), Color(0x1B, 0x1D, 0x2E))

    val COMMANDS = listOf(
        HelixCommandItem("write", listOf("w"), "Save all modified files") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
        },
        HelixCommandItem("quit", listOf("q"), "Close active editor tab") { editor ->
            HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem("write-quit", listOf("wq", "x"), "Save all and close tab") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
            HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem("write-all", listOf("wa"), "Save all modified files") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
        },
        HelixCommandItem("quit-all", listOf("qa"), "Close all editor tabs") { editor ->
            HelixActionDelegate.executeAction("CloseAllEditors", editor)
        },
        HelixCommandItem("cquit", listOf("cq"), "Close active editor tab") { editor ->
            HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem("vsplit", listOf("vsp"), "Split editor vertically") { editor ->
            HelixActionDelegate.executeAction("SplitVertically", editor)
        },
        HelixCommandItem("hsplit", listOf("sp"), "Split editor horizontally") { editor ->
            HelixActionDelegate.executeAction("SplitHorizontally", editor)
        },
        HelixCommandItem("format", emptyList(), "Format buffer using IDE code formatter") { editor ->
            HelixActionDelegate.executeAction("ReformatCode", editor)
        },
        HelixCommandItem("earlier", emptyList(), "Undo earlier changes") { editor ->
            HelixActionDelegate.executeAction($$"$Undo", editor)
        },
        HelixCommandItem("later", emptyList(), "Redo later changes") { editor ->
            HelixActionDelegate.executeAction($$"$Redo", editor)
        },
        HelixCommandItem("reload", emptyList(), "Synchronize / reload buffer from disk") { editor ->
            HelixActionDelegate.executeAction("Synchronize", editor)
        },
        HelixCommandItem("config-reload", emptyList(), "Reload Helix configuration") { _ ->
        },
        HelixCommandItem("toggle-search-ui", emptyList(), "Toggle search UI (Stock Helix / Popup)") { _ ->
            HelixSettings.instance.searchUiMode =
                if (HelixSettings.instance.searchUiMode == HelixSearchUiMode.STOCK_HELIX) {
                    HelixSearchUiMode.POPUP
                } else {
                    HelixSearchUiMode.STOCK_HELIX
                }
        },
        HelixCommandItem("set-search-ui-stock", emptyList(), "Set search UI to Stock Helix inline bar") { _ ->
            HelixSettings.instance.searchUiMode = HelixSearchUiMode.STOCK_HELIX
        },
        HelixCommandItem("set-search-ui-popup", emptyList(), "Set search UI to Popup dialog") { _ ->
            HelixSettings.instance.searchUiMode = HelixSearchUiMode.POPUP
        },
        HelixCommandItem("jumps", emptyList(), "Open jumplist picker") { editor ->
            HelixJumplistPopup.show(editor)
        }
    )

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
            add(JBLabel(text).apply {
                font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f))
                foreground = KEYCAP_FG
                border = JBUI.Borders.empty(2, 6)
            })
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
            JBUI.Borders.empty(12, 14, 10, 14)
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
                cellHasFocus: Boolean
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
            JBUI.Borders.empty(8, 14, 10, 14)
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

    fun executeCommand(cmd: String, editor: Editor) {
        val cleanCmd = cmd.trim().removePrefix(":")
        val matched = COMMANDS.firstOrNull {
            it.name.equals(cleanCmd, ignoreCase = true) || it.aliases.any { a -> a.equals(cleanCmd, ignoreCase = true) }
        }
        if (matched != null) {
            matched.action(editor)
            return
        }

        val targetLine = cleanCmd.toIntOrNull()
        if (targetLine != null && targetLine > 0) {
            val project = editor.project
            if (project != null) {
                HelixJumpListService.getInstance(project).recordCurrent(editor)
            }
            HelixMotions.moveFileStart(editor, targetLine)
            return
        }

        // Fallbacks for standard vim/helix commands
        when (cleanCmd) {
            "jumps" -> HelixJumplistPopup.show(editor)
            "w", "write" -> HelixActionDelegate.executeAction("SaveAll", editor)
            "q", "quit" -> HelixActionDelegate.executeAction("CloseContent", editor)
            "wq", "x" -> {
                HelixActionDelegate.executeAction("SaveAll", editor)
                ApplicationManager.getApplication().invokeLater {
                    HelixActionDelegate.executeAction("CloseContent", editor)
                }
            }

            "wa" -> HelixActionDelegate.executeAction("SaveAll", editor)
            "qa" -> HelixActionDelegate.executeAction("CloseAllEditors", editor)
            "vsp" -> HelixActionDelegate.executeAction("SplitVertically", editor)
            "sp" -> HelixActionDelegate.executeAction("SplitHorizontally", editor)
            "format" -> HelixActionDelegate.executeAction("ReformatCode", editor)
            "set search-ui=inline", "set search-ui=stock" -> {
                HelixSettings.instance.searchUiMode = HelixSearchUiMode.STOCK_HELIX
            }

            "set search-ui=popup" -> {
                HelixSettings.instance.searchUiMode = HelixSearchUiMode.POPUP
            }

            "toggle-search-ui", "search-ui" -> {
                val current = HelixSettings.instance.searchUiMode
                val next =
                    if (current == HelixSearchUiMode.STOCK_HELIX) HelixSearchUiMode.POPUP else HelixSearchUiMode.STOCK_HELIX
                HelixSettings.instance.searchUiMode = next
            }
        }
    }
}
