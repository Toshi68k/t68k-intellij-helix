package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.jumplist.HelixJumpEntry
import jp.titze.intellij.helix.jumplist.HelixJumpListService
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
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

data class JumpListItem(val originalIndex: Int, val entry: HelixJumpEntry, val isCurrent: Boolean) {
    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return entry.virtualFile.name.lowercase().contains(q) ||
            entry.virtualFile.path.lowercase().contains(q) ||
            entry.line.toString().contains(q) ||
            entry.previewText.lowercase().contains(q)
    }
}

object HelixJumplistPopup {

    private val CARD_BG get() = HelixTheme.CARD_BG
    private val CARD_BORDER get() = HelixTheme.CARD_BORDER
    private val TITLE_COLOR get() = HelixTheme.TITLE_COLOR
    private val CANCEL_COLOR get() = HelixTheme.CANCEL_COLOR

    private val KEYCAP_BG get() = HelixTheme.KEYCAP_BG
    private val KEYCAP_CURRENT_BG get() = HelixTheme.KEYCAP_CURRENT_BG
    private val KEYCAP_BORDER get() = HelixTheme.KEYCAP_BORDER
    private val KEYCAP_FG get() = HelixTheme.KEYCAP_FG
    private val KEYCAP_CURRENT_FG get() = HelixTheme.KEYCAP_CURRENT_FG

    private val ITEM_TEXT_COLOR get() = HelixTheme.ITEM_TEXT_COLOR
    private val ITEM_PATH_COLOR get() = HelixTheme.ITEM_PATH_COLOR
    private val ITEM_SNIPPET_COLOR get() = HelixTheme.ITEM_SNIPPET_COLOR
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

    private class KeycapBadge(text: String, isCurrent: Boolean = false) : JPanel(BorderLayout()) {
        private val bg = if (isCurrent) KEYCAP_CURRENT_BG else KEYCAP_BG
        private val fg = if (isCurrent) KEYCAP_CURRENT_FG else KEYCAP_FG

        init {
            isOpaque = false
            add(
                JBLabel(text).apply {
                    font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f))
                    foreground = fg
                    border = JBUI.Borders.empty(2, 6)
                },
            )
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = bg
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(4), JBUI.scale(4))
            g2.color = KEYCAP_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(4), JBUI.scale(4))
            g2.dispose()
        }
    }

    fun show(editor: Editor) {
        val project = editor.project ?: return
        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) return
        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { show(editor) }
            return
        }

        val jumpService = HelixJumpListService.getInstance(project)
        val allEntries = jumpService.getEntries()
        val currentIndex = jumpService.getCurrentIndex()

        val mainPanel = RoundedCardPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(8)

        val northPanel = JPanel(BorderLayout(0, JBUI.scale(6)))
        northPanel.isOpaque = false

        val headerPanel = JPanel(BorderLayout())
        headerPanel.isOpaque = false
        val titleLabel = JBLabel("Jumplist (${allEntries.size})").apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
            foreground = TITLE_COLOR
        }
        val cancelHint = JBLabel("ESC to cancel").apply {
            font = JBUI.Fonts.smallFont()
            foreground = CANCEL_COLOR
        }
        headerPanel.add(titleLabel, BorderLayout.WEST)
        headerPanel.add(cancelHint, BorderLayout.EAST)
        northPanel.add(headerPanel, BorderLayout.NORTH)

        val inputContainer = JPanel(BorderLayout())
        inputContainer.isOpaque = false
        val inputPanel = InputBoxPanel(BorderLayout())
        inputPanel.border = JBUI.Borders.empty(2, 6)

        val textField = JBTextField()
        textField.border = BorderFactory.createEmptyBorder()
        textField.isOpaque = false
        textField.background = Color(0, 0, 0, 0)
        textField.foreground = ITEM_TEXT_COLOR
        textField.caretColor = ITEM_TEXT_COLOR
        textField.emptyText.text = "Filter jumps by file, line or content..."
        inputPanel.add(textField, BorderLayout.CENTER)
        inputContainer.add(inputPanel, BorderLayout.CENTER)
        northPanel.add(inputContainer, BorderLayout.CENTER)

        mainPanel.add(northPanel, BorderLayout.NORTH)

        // Build list items (reverse chronological order or chronological: in Helix, newest is at bottom or top)
        // Showing reverse chronological (newest first) or natural chronological order:
        // Let's show newest at the top or keep original indices
        val items = allEntries.mapIndexed { index, entry ->
            JumpListItem(
                originalIndex = index,
                entry = entry,
                isCurrent = index == currentIndex,
            )
        }.reversed()

        val listModel = DefaultListModel<JumpListItem>()
        items.forEach { listModel.addElement(it) }

        val list = JBList(listModel)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = items.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
        list.isOpaque = false
        list.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        list.cellRenderer = object : ListCellRenderer<JumpListItem> {
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
            private val fileLabel = JBLabel().apply {
                font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
                foreground = ITEM_TEXT_COLOR
            }
            private val pathLabel = JBLabel().apply {
                font = JBUI.Fonts.smallFont()
                foreground = ITEM_PATH_COLOR
            }
            private val snippetLabel = JBLabel().apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11.5f))
                foreground = ITEM_SNIPPET_COLOR
            }

            init {
                cellPanel.add(badgeHolder, BorderLayout.WEST)
                val westDetails = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    isOpaque = false
                    add(fileLabel)
                    add(Box.createHorizontalStrut(JBUI.scale(6)))
                    add(pathLabel)
                }
                textPanel.add(westDetails, BorderLayout.WEST)
                textPanel.add(snippetLabel, BorderLayout.EAST)
                cellPanel.add(textPanel, BorderLayout.CENTER)
            }

            override fun getListCellRendererComponent(
                list: JList<out JumpListItem>?,
                value: JumpListItem?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                cellPanel.isSelectedRow = isSelected
                badgeHolder.removeAll()
                if (value != null) {
                    val indicator = if (value.isCurrent) "> " else "  "
                    val loc = "$indicator${value.entry.line}:${value.entry.column}"
                    badgeHolder.add(KeycapBadge(loc, value.isCurrent), BorderLayout.CENTER)

                    fileLabel.text = value.entry.virtualFile.name
                    pathLabel.text = value.entry.virtualFile.parent?.name?.let { "[$it]" } ?: ""
                    snippetLabel.text = if (value.entry.previewText.length > 40) {
                        value.entry.previewText.take(40) + "..."
                    } else {
                        value.entry.previewText
                    }
                }
                return cellPanel
            }
        }

        val scrollPane = JBScrollPane(list)
        scrollPane.border = JBUI.Borders.empty(4, 6)
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
        scrollPane.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        mainPanel.preferredSize = Dimension(JBUI.scale(560), JBUI.scale(320))

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, textField)
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(false)
            .setResizable(false)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .createPopup()

        fun updateFilter() {
            val q = textField.text.trim()
            listModel.clear()
            val filtered = items.filter { it.matches(q) }
            filtered.forEach { listModel.addElement(it) }
            if (listModel.size > 0) {
                list.selectedIndex = 0
            }
        }

        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateFilter()
            override fun removeUpdate(e: DocumentEvent?) = updateFilter()
            override fun changedUpdate(e: DocumentEvent?) = updateFilter()
        })

        fun selectCurrent() {
            val selected = list.selectedValue
            popup.cancel()
            if (selected != null) {
                jumpService.jumpTo(selected.originalIndex)
            }
        }

        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        selectCurrent()
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
                    selectCurrent()
                }
            }
        })

        popup.showCenteredInCurrentWindow(project)
    }
}
