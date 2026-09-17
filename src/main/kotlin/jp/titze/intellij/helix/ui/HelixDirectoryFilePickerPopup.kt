package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
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
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

data class DirectoryFileItem(val file: VirtualFile, val relativePath: String, val fileName: String) {
    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.trim().lowercase()
        val pathLower = relativePath.lowercase()
        val nameLower = fileName.lowercase()
        if (nameLower.contains(q) || pathLower.contains(q)) return true
        val tokens = q.split(" ").filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            return tokens.all { pathLower.contains(it) }
        }
        return isFuzzyMatch(pathLower, q)
    }

    private fun isFuzzyMatch(target: String, pattern: String): Boolean {
        var patternIdx = 0
        for (i in target.indices) {
            if (target[i] == pattern[patternIdx]) {
                patternIdx++
                if (patternIdx == pattern.length) return true
            }
        }
        return false
    }
}

object HelixDirectoryFilePickerPopup {

    private val CARD_BG get() = HelixTheme.CARD_BG
    private val CARD_BORDER get() = HelixTheme.CARD_BORDER
    private val TITLE_COLOR get() = HelixTheme.TITLE_COLOR
    private val CANCEL_COLOR get() = HelixTheme.CANCEL_COLOR
    private val KEYCAP_BG get() = HelixTheme.KEYCAP_BG
    private val KEYCAP_BORDER get() = HelixTheme.KEYCAP_BORDER
    private val KEYCAP_FG get() = HelixTheme.KEYCAP_FG
    private val ITEM_TEXT_COLOR get() = HelixTheme.ITEM_TEXT_COLOR
    private val ITEM_PATH_COLOR get() = HelixTheme.ITEM_PATH_COLOR
    private val HOVER_BG get() = HelixTheme.HOVER_BG
    private val INPUT_BG get() = HelixTheme.INPUT_BG

    private const val POPUP_WIDTH = 580
    private const val POPUP_HEIGHT = 400
    private const val PAGE_STEP = 6
    private const val MAX_SCAN_FILES = 1000

    private val IGNORED_DIRS = setOf(
        ".git", ".idea", ".gradle", "build", "out", "target", "node_modules", ".svn", ".hg",
    )

    var fileOpener: ((Editor, VirtualFile) -> Unit)? = null
    var isShowing: Boolean = false
        internal set

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

    fun resolveBaseDirectory(editor: Editor): VirtualFile? {
        val docFile = FileDocumentManager.getInstance().getFile(editor.document)
        if (docFile != null) {
            return if (docFile.isDirectory) docFile else docFile.parent
        }
        val projectBase = editor.project?.basePath ?: return null
        return LocalFileSystem.getInstance().findFileByPath(projectBase)
    }

    fun collectFiles(baseDir: VirtualFile, maxFiles: Int = MAX_SCAN_FILES): List<DirectoryFileItem> {
        val items = mutableListOf<DirectoryFileItem>()

        fun visit(dir: VirtualFile) {
            val children = dir.children ?: return
            for (child in children) {
                if (child.isDirectory) {
                    val name = child.name
                    if (!name.startsWith(".") && name !in IGNORED_DIRS) {
                        visit(child)
                        if (items.size >= maxFiles) return
                    }
                } else {
                    val relPath = VfsUtilCore.getRelativePath(child, baseDir) ?: child.name
                    items.add(DirectoryFileItem(child, relPath, child.name))
                    if (items.size >= maxFiles) return
                }
            }
        }

        visit(baseDir)
        return items.sortedBy { it.relativePath.lowercase() }
    }

    fun show(editor: Editor) {
        val project = editor.project ?: return
        val baseDir = resolveBaseDirectory(editor) ?: return
        val allItems = collectFiles(baseDir)

        isShowing = true

        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) {
            return
        }
        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { show(editor) }
            return
        }

        val mainPanel = RoundedCardPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            preferredSize = Dimension(JBUI.scale(POPUP_WIDTH), JBUI.scale(POPUP_HEIGHT))
        }

        val textField = createSearchField()
        val northPanel = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            isOpaque = false
            add(createHeaderPanel(baseDir.name, allItems.size), BorderLayout.NORTH)
            add(createInputContainer(textField), BorderLayout.CENTER)
        }
        mainPanel.add(northPanel, BorderLayout.NORTH)

        val listModel = DefaultListModel<DirectoryFileItem>().apply {
            allItems.forEach { addElement(it) }
        }
        val list = createFileList(listModel)
        val scrollPane = JBScrollPane(list).apply {
            border = JBUI.Borders.empty(4, 6)
            isOpaque = false
            viewport.isOpaque = false
        }
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(createFooterPanel(), BorderLayout.SOUTH)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, textField)
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(false)
            .setResizable(false)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
                override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                    isShowing = false
                }
            })
            .createPopup()

        setupListeners(textField, list, listModel, allItems, popup, editor)
        popup.showInBestPositionFor(editor)
    }

    private fun createHeaderPanel(dirName: String, count: Int): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        val title = JBLabel("Current Dir: $dirName ($count files)").apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
            foreground = TITLE_COLOR
        }
        val hint = JBLabel("ESC to cancel • ⏎ to open").apply {
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
        emptyText.text = "Filter files in current directory..."
    }

    private fun createInputContainer(textField: JBTextField): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        val inputPanel = InputBoxPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 6)
            add(textField, BorderLayout.CENTER)
        }
        add(inputPanel, BorderLayout.CENTER)
    }

    private fun createFileList(listModel: DefaultListModel<DirectoryFileItem>): JBList<DirectoryFileItem> {
        val list = JBList(listModel)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        list.isOpaque = false
        list.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        list.cellRenderer = createCellRenderer()
        return list
    }

    private fun createCellRenderer(): ListCellRenderer<DirectoryFileItem> =
        object : ListCellRenderer<DirectoryFileItem> {
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
            private val pathLabel = JBLabel().apply {
                font = JBUI.Fonts.smallFont()
                foreground = ITEM_PATH_COLOR
            }

            init {
                cellPanel.add(badgeHolder, BorderLayout.WEST)
                val westDetails = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    isOpaque = false
                    add(nameLabel)
                }
                textPanel.add(westDetails, BorderLayout.WEST)
                textPanel.add(pathLabel, BorderLayout.EAST)
                cellPanel.add(textPanel, BorderLayout.CENTER)
            }

            override fun getListCellRendererComponent(
                list: JList<out DirectoryFileItem>?,
                value: DirectoryFileItem?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                cellPanel.isSelectedRow = isSelected
                badgeHolder.removeAll()
                if (value != null) {
                    val ext = value.file.extension ?: "file"
                    badgeHolder.add(KeycapBadge(ext), BorderLayout.CENTER)
                    nameLabel.text = value.fileName
                    pathLabel.text = value.relativePath
                }
                return cellPanel
            }
        }

    private fun createFooterPanel(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(8, 4, 4, 4)
        val instructions = JBLabel("⏎ Open • ↑/↓ Select • C-n/C-p Navigate • ESC Cancel").apply {
            font = JBUI.Fonts.smallFont()
            foreground = CANCEL_COLOR
        }
        add(instructions, BorderLayout.WEST)
    }

    private fun setupListeners(
        textField: JBTextField,
        list: JBList<DirectoryFileItem>,
        listModel: DefaultListModel<DirectoryFileItem>,
        allItems: List<DirectoryFileItem>,
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
            if (selected != null) {
                openSelectedFile(editor, selected.file)
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

    fun openSelectedFile(editor: Editor, file: VirtualFile) {
        val opener = fileOpener
        if (opener != null) {
            opener(editor, file)
        } else {
            val project = editor.project ?: return
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }

    private fun handleKeyNavigation(
        e: KeyEvent,
        list: JBList<DirectoryFileItem>,
        listModel: DefaultListModel<DirectoryFileItem>,
        popup: JBPopup,
        selectCurrent: () -> Unit,
    ) {
        if (isDownKey(e)) {
            navigateList(list, listModel, 1)
            e.consume()
            return
        }
        if (isUpKey(e)) {
            navigateList(list, listModel, -1)
            e.consume()
            return
        }
        when (e.keyCode) {
            KeyEvent.VK_ENTER -> {
                selectCurrent()
                e.consume()
            }

            KeyEvent.VK_ESCAPE -> {
                popup.cancel()
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

            KeyEvent.VK_HOME -> {
                if (listModel.size > 0) list.selectedIndex = 0
                e.consume()
            }

            KeyEvent.VK_END -> {
                if (listModel.size > 0) list.selectedIndex = listModel.size - 1
                e.consume()
            }
        }
    }

    private fun isDownKey(e: KeyEvent): Boolean = e.keyCode == KeyEvent.VK_DOWN ||
        (e.isControlDown && (e.keyCode == KeyEvent.VK_N || e.keyCode == KeyEvent.VK_J))

    private fun isUpKey(e: KeyEvent): Boolean = e.keyCode == KeyEvent.VK_UP ||
        (e.isControlDown && (e.keyCode == KeyEvent.VK_P || e.keyCode == KeyEvent.VK_K))

    private fun navigateList(
        list: JBList<DirectoryFileItem>,
        listModel: DefaultListModel<DirectoryFileItem>,
        delta: Int,
    ) {
        if (listModel.size == 0) return
        val next = (list.selectedIndex + delta).coerceIn(0, listModel.size - 1)
        list.selectedIndex = next
        list.ensureIndexIsVisible(next)
    }
}
