package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.action.HelixRegisterActions
import jp.titze.intellij.helix.register.HelixRegisterItem
import jp.titze.intellij.helix.register.HelixRegisterManager
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

object HelixRegistersPopup {

    private val CARD_BG get() = HelixTheme.CARD_BG
    private val CARD_BORDER get() = HelixTheme.CARD_BORDER
    private val TITLE_COLOR get() = HelixTheme.TITLE_COLOR
    private val CANCEL_COLOR get() = HelixTheme.CANCEL_COLOR

    private val KEYCAP_BG get() = HelixTheme.KEYCAP_BG
    private val KEYCAP_BORDER get() = HelixTheme.KEYCAP_BORDER
    private val KEYCAP_FG get() = HelixTheme.KEYCAP_FG

    private val ITEM_TEXT_COLOR get() = HelixTheme.ITEM_TEXT_COLOR
    private val ITEM_PATH_COLOR get() = HelixTheme.ITEM_PATH_COLOR
    private val ITEM_SNIPPET_COLOR get() = HelixTheme.ITEM_SNIPPET_COLOR
    private val HOVER_BG get() = HelixTheme.HOVER_BG
    private val INPUT_BG get() = HelixTheme.INPUT_BG

    private const val POPUP_WIDTH = 560
    private const val POPUP_HEIGHT = 380
    private const val PREVIEW_MAX_CHARS = 40
    private const val PAGE_STEP = 6

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
        val project = editor.project ?: return
        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) return
        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { show(editor) }
            return
        }

        val allItems = HelixRegisterManager.getAllRegisters(editor)
        val mainPanel = RoundedCardPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            preferredSize = Dimension(JBUI.scale(POPUP_WIDTH), JBUI.scale(POPUP_HEIGHT))
        }

        val textField = createSearchField()
        val northPanel = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            isOpaque = false
            add(createHeaderPanel(allItems.size), BorderLayout.NORTH)
            add(createInputContainer(textField), BorderLayout.CENTER)
        }
        mainPanel.add(northPanel, BorderLayout.NORTH)

        val listModel = DefaultListModel<HelixRegisterItem>().apply {
            allItems.forEach { addElement(it) }
        }
        val list = createRegisterList(listModel)
        val scrollPane = JBScrollPane(list).apply {
            border = JBUI.Borders.empty(4, 6)
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, textField)
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(false)
            .setResizable(false)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .createPopup()

        setupListeners(textField, list, listModel, allItems, popup, editor)
        popup.showInBestPositionFor(editor)
    }

    private fun createHeaderPanel(count: Int): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        val title = JBLabel("Registers ($count)").apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
            foreground = TITLE_COLOR
        }
        val hint = JBLabel("ESC to cancel • ⏎ to paste").apply {
            font = JBUI.Fonts.smallFont()
            foreground = CANCEL_COLOR
        }
        add(title, BorderLayout.WEST)
        add(hint, BorderLayout.EAST)
    }

    private fun createSearchField(): JBTextField = JBTextField().apply {
        border = BorderFactory.createEmptyBorder()
        isOpaque = false
        background = Color(0, 0, 0, 0)
        foreground = ITEM_TEXT_COLOR
        caretColor = ITEM_TEXT_COLOR
        emptyText.text = "Filter registers by name, type, or content..."
    }

    private fun createInputContainer(textField: JBTextField): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        val inputPanel = InputBoxPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 6)
            add(textField, BorderLayout.CENTER)
        }
        add(inputPanel, BorderLayout.CENTER)
    }

    private fun createRegisterList(listModel: DefaultListModel<HelixRegisterItem>): JBList<HelixRegisterItem> {
        val list = JBList(listModel)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        list.isOpaque = false
        list.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        list.cellRenderer = createCellRenderer()
        return list
    }

    private fun createCellRenderer(): ListCellRenderer<HelixRegisterItem> =
        object : ListCellRenderer<HelixRegisterItem> {
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
            private val nameLabel = JBLabel().apply {
                font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
                foreground = ITEM_TEXT_COLOR
            }
            private val tagLabel = JBLabel().apply {
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
                    add(nameLabel)
                    add(Box.createHorizontalStrut(JBUI.scale(6)))
                    add(tagLabel)
                }
                textPanel.add(westDetails, BorderLayout.WEST)
                textPanel.add(snippetLabel, BorderLayout.EAST)
                cellPanel.add(textPanel, BorderLayout.CENTER)
            }

            override fun getListCellRendererComponent(
                list: JList<out HelixRegisterItem>?,
                value: HelixRegisterItem?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                cellPanel.isSelectedRow = isSelected
                badgeHolder.removeAll()
                if (value != null) {
                    badgeHolder.add(KeycapBadge(value.register.toString()), BorderLayout.CENTER)
                    nameLabel.text = value.description
                    tagLabel.text = value.tagText ?: ""
                    val preview = value.previewText
                    snippetLabel.text = if (preview.length > PREVIEW_MAX_CHARS) {
                        preview.take(PREVIEW_MAX_CHARS) + "..."
                    } else {
                        preview
                    }
                }
                return cellPanel
            }
        }

    private fun setupListeners(
        textField: JBTextField,
        list: JBList<HelixRegisterItem>,
        listModel: DefaultListModel<HelixRegisterItem>,
        allItems: List<HelixRegisterItem>,
        popup: JBPopup,
        editor: Editor,
    ) {
        val filterAction = {
            val q = textField.text.trim()
            listModel.clear()
            val filtered = allItems.filter { it.matches(q) }
            filtered.forEach { listModel.addElement(it) }
            if (listModel.size > 0) list.selectedIndex = 0
        }

        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterAction()
            override fun removeUpdate(e: DocumentEvent?) = filterAction()
            override fun changedUpdate(e: DocumentEvent?) = filterAction()
        })

        val selectCurrent = {
            val selected = list.selectedValue
            popup.cancel()
            if (selected?.entry != null) {
                HelixRegisterActions.paste(editor, after = true, register = selected.register)
            }
        }

        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                handleKeyNavigation(e, list, listModel, popup, selectCurrent)
            }
        })

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) selectCurrent()
            }
        })
    }

    private fun handleKeyNavigation(
        e: KeyEvent,
        list: JBList<HelixRegisterItem>,
        listModel: DefaultListModel<HelixRegisterItem>,
        popup: JBPopup,
        selectCurrent: () -> Unit,
    ) {
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
                navigateList(list, listModel, 1)
                e.consume()
            }

            KeyEvent.VK_UP -> {
                navigateList(list, listModel, -1)
                e.consume()
            }

            KeyEvent.VK_PAGE_DOWN -> {
                navigateList(list, listModel, PAGE_STEP)
                e.consume()
            }

            KeyEvent.VK_PAGE_UP -> {
                navigateList(list, listModel, -PAGE_STEP)
                e.consume()
            }
        }
    }

    private fun navigateList(
        list: JBList<HelixRegisterItem>,
        listModel: DefaultListModel<HelixRegisterItem>,
        delta: Int,
    ) {
        if (listModel.size > 0) {
            val next = (list.selectedIndex + delta).coerceIn(0, listModel.size - 1)
            list.selectedIndex = next
            list.ensureIndexIsVisible(next)
        }
    }
}
