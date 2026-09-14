package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.settings.WhichKeyHintMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities

object HelixWhichKeyPopup {
    private var activePopup: JBPopup? = null
    private var currentPrefix: String? = null
    private var onToggleHintAction: (() -> Unit)? = null

    fun toggleHintMode(): Boolean {
        val action = onToggleHintAction ?: return false
        action.invoke()
        return true
    }

    // Theme-adaptive colors matching the modern keycap design
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

    fun isShowing(prefix: String? = null): Boolean {
        val popup = activePopup ?: return false
        if (popup.isDisposed || !popup.isVisible) return false
        return prefix == null || currentPrefix == prefix
    }

    fun hide() {
        val app = ApplicationManager.getApplication()
        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { hide() }
            return
        }

        currentPrefix = null
        onToggleHintAction = null
        val popup = activePopup
        activePopup = null
        popup?.cancel()
    }

    fun show(editor: Editor, prefix: String) {
        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) {
            return
        }

        val settings = HelixSettings.instance
        if (!settings.enableWhichKeyPopups) {
            return
        }

        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { show(editor, prefix) }
            return
        }

        if (isShowing(prefix)) {
            return
        }

        hide()
        currentPrefix = prefix

        val (title, items) = HelixWhichKeyMenus.getMenu(prefix) ?: return

        val component = editor.component
        val visibleRect = component.visibleRect
        val viewWidth = if (visibleRect.width > 0) visibleRect.width else component.width
        val viewHeight = if (visibleRect.height > 0) visibleRect.height else component.height

        val maxAvailableHeight = if (viewHeight > 0) viewHeight - JBUI.scale(40) else 600
        val panel = createWhichKeyPanel(title, items, editor, maxAvailableHeight)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(false)
            .setShowBorder(false)
            .setShowShadow(true)
            .createPopup()

        activePopup = popup

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (activePopup === popup) {
                    activePopup = null
                    currentPrefix = null
                    onToggleHintAction = null
                }
                val state = HelixStateManager.getOrCreate(editor)
                if (state.pendingSequence == "Z") {
                    state.clearPendingSequence()
                    state.clearCount()
                }
            }
        })

        if (component.isShowing && viewWidth > 0 && viewHeight > 0) {
            val prefSize = panel.preferredSize
            val pos = calculatePopupPosition(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                prefWidth = prefSize.width,
                prefHeight = prefSize.height,
                visibleX = visibleRect.x,
                visibleY = visibleRect.y,
            )
            popup.show(RelativePoint(component, pos))
        } else {
            val project = editor.project
            if (project != null) {
                popup.showCenteredInCurrentWindow(project)
            } else {
                popup.showInBestPositionFor(editor)
            }
        }
    }

    internal fun calculatePopupPosition(
        viewWidth: Int,
        viewHeight: Int,
        prefWidth: Int,
        prefHeight: Int,
        visibleX: Int,
        visibleY: Int,
        marginX: Int = 20,
        marginY: Int = 20,
    ): Point {
        val scaledMarginX = JBUI.scale(marginX)
        val scaledMarginY = JBUI.scale(marginY)
        val targetX = visibleX + (viewWidth - prefWidth - scaledMarginX).coerceAtLeast(0)
        val targetY = visibleY + (viewHeight - prefHeight - scaledMarginY).coerceAtLeast(0)
        return Point(targetX, targetY)
    }

    internal fun createWhichKeyPanel(
        title: String,
        items: List<WhichKeyItem>,
        editor: Editor,
        maxHeight: Int = 600,
    ): JPanel {
        val settings = HelixSettings.instance
        val maxCols = settings.whichKeyColumnLayout.maxColumns
        val numColumns = when {
            maxCols >= 3 && items.size > 18 -> 3
            items.size > 8 -> 2
            else -> 1
        }
        val cardMinWidth = when (numColumns) {
            3 -> JBUI.scale(760)
            2 -> JBUI.scale(520)
            else -> JBUI.scale(280)
        }
        val mainPanel = RoundedCardPanel(BorderLayout(), minWidth = cardMinWidth)
        mainPanel.isFocusable = true
        mainPanel.focusTraversalKeysEnabled = false

        var activeHintMode = settings.whichKeyHintMode

        val headerPanel = JPanel(BorderLayout(JBUI.scale(12), 0))
        headerPanel.isOpaque = false
        headerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER_COLOR),
            JBUI.Borders.empty(8, 12, 6, 12),
        )

        val titleLabel = JBLabel(title)
        titleLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(11.5f).toFloat())
        titleLabel.foreground = TITLE_COLOR
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val rightHeaderPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
            isOpaque = false
        }

        fun hintBadgeText(mode: WhichKeyHintMode) = if (mode == WhichKeyHintMode.HELIX_COMMAND) {
            "TAB: HELIX"
        } else {
            "TAB: INTELLIJ"
        }

        val toggleModeLabel = JBLabel(hintBadgeText(activeHintMode)).apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(9.5f).toFloat())
            foreground = TITLE_COLOR
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Click or press Tab to switch between Helix command names and IntelliJ action IDs"
        }

        val cancelLabel = JBLabel("ESC TO CANCEL").apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(9.5f).toFloat())
            foreground = CANCEL_COLOR
        }

        rightHeaderPanel.add(toggleModeLabel)
        rightHeaderPanel.add(cancelLabel)
        headerPanel.add(rightHeaderPanel, BorderLayout.EAST)

        mainPanel.add(headerPanel, BorderLayout.NORTH)

        val allRows = mutableListOf<WhichKeyRow>()

        fun toggleHintMode() {
            activeHintMode = if (activeHintMode == WhichKeyHintMode.HELIX_COMMAND) {
                WhichKeyHintMode.INTELLIJ_ACTION
            } else {
                WhichKeyHintMode.HELIX_COMMAND
            }
            toggleModeLabel.text = hintBadgeText(activeHintMode)
            allRows.forEach { it.updateHintMode(activeHintMode) }
            mainPanel.revalidate()
            mainPanel.repaint()
        }

        onToggleHintAction = { toggleHintMode() }

        mainPanel.registerKeyboardAction(
            { toggleHintMode() },
            javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW,
        )

        toggleModeLabel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                toggleHintMode()
            }
        })

        fun createRow(item: WhichKeyItem): JPanel {
            val row = WhichKeyRow(item, activeHintMode) {
                val state = HelixStateManager.getOrCreate(editor)
                val inStickyView = state.pendingSequence == "Z"
                if (!inStickyView) {
                    hide()
                }
                val triggerChar = if (item.key.equals("Space", ignoreCase = true)) ' ' else item.key[0]
                val handled = HelixKeyHandler.handleKey(triggerChar, editor)
                if (inStickyView && (!handled || state.pendingSequence != "Z")) {
                    hide()
                }
            }
            allRows.add(row)
            return row
        }

        val itemsPanel = JPanel()
        itemsPanel.isOpaque = false
        itemsPanel.border = JBUI.Borders.empty(4, 6, 6, 6)

        val rowsPerCol = (items.size + numColumns - 1) / numColumns
        if (numColumns == 1) {
            itemsPanel.layout = javax.swing.BoxLayout(itemsPanel, javax.swing.BoxLayout.Y_AXIS)
            for (item in items) {
                itemsPanel.add(createRow(item))
                itemsPanel.add(javax.swing.Box.createVerticalStrut(JBUI.scale(1)))
            }
        } else {
            itemsPanel.layout = java.awt.GridLayout(1, numColumns, JBUI.scale(10), 0)
            for (col in 0 until numColumns) {
                val colPanel = JPanel().apply {
                    layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                    isOpaque = false
                }
                val start = col * rowsPerCol
                val end = minOf(start + rowsPerCol, items.size)
                if (start < items.size) {
                    for (item in items.subList(start, end)) {
                        colPanel.add(createRow(item))
                        colPanel.add(javax.swing.Box.createVerticalStrut(JBUI.scale(1)))
                    }
                }
                itemsPanel.add(colPanel)
            }
        }

        val scrollPane = com.intellij.ui.components.JBScrollPane(itemsPanel)
        scrollPane.border = JBUI.Borders.empty()
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
        scrollPane.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.verticalScrollBar.unitIncrement = JBUI.scale(16)
        scrollPane.verticalScrollBar.blockIncrement = JBUI.scale(80)

        val prefItemsHeight = itemsPanel.preferredSize.height
        val headerHeight = headerPanel.preferredSize.height
        val totalPreferredHeight = prefItemsHeight + headerHeight + JBUI.scale(10)

        val isScrollable = totalPreferredHeight > maxHeight
        if (isScrollable) {
            val boundedScrollHeight = maxHeight - headerHeight - JBUI.scale(10)
            scrollPane.preferredSize = java.awt.Dimension(
                itemsPanel.preferredSize.width,
                boundedScrollHeight.coerceAtLeast(JBUI.scale(150)),
            )

            val footerPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER_COLOR),
                    JBUI.Borders.empty(6, 14, 6, 14),
                )
                val hint = JBLabel("Tab: toggle hint | ↑/↓: scroll | PgUp/PgDn: page").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = CANCEL_COLOR
                }
                add(hint, BorderLayout.WEST)
            }
            mainPanel.add(footerPanel, BorderLayout.SOUTH)
        }

        mainPanel.add(scrollPane, BorderLayout.CENTER)

        mainPanel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    val state = HelixStateManager.getOrCreate(editor)
                    state.clearPendingSequence()
                    state.clearCount()
                    hide()
                    e.consume()
                    return
                }

                if (e.keyCode == KeyEvent.VK_TAB) {
                    toggleHintMode()
                    e.consume()
                    return
                }

                handleNavigationKeys(e, scrollPane)
                if (e.isConsumed) return

                if (e.isControlDown && currentPrefix == "C-w") {
                    handleWindowCtrlChords(e, editor)
                }
            }

            override fun keyTyped(e: KeyEvent) {
                val ch = e.keyChar
                if (ch != KeyEvent.CHAR_UNDEFINED && ch != '\u001B' && ch != '\t') {
                    val state = HelixStateManager.getOrCreate(editor)
                    val inStickyView = state.pendingSequence == "Z"
                    if (!inStickyView) {
                        hide()
                    }
                    val handled = HelixKeyHandler.handleKey(ch, editor)
                    if (inStickyView && (!handled || state.pendingSequence != "Z")) {
                        hide()
                    }
                    e.consume()
                }
            }
        })

        return mainPanel
    }

    private fun handleNavigationKeys(e: KeyEvent, scrollPane: com.intellij.ui.components.JBScrollPane) {
        val scrollBar = scrollPane.verticalScrollBar
        when (e.keyCode) {
            KeyEvent.VK_DOWN -> {
                scrollBar.value = (scrollBar.value + scrollBar.unitIncrement * 2)
                    .coerceAtMost(scrollBar.maximum - scrollBar.visibleAmount)
                e.consume()
            }

            KeyEvent.VK_UP -> {
                scrollBar.value = (scrollBar.value - scrollBar.unitIncrement * 2).coerceAtLeast(0)
                e.consume()
            }

            KeyEvent.VK_PAGE_DOWN -> {
                scrollBar.value = (scrollBar.value + scrollBar.blockIncrement)
                    .coerceAtMost(scrollBar.maximum - scrollBar.visibleAmount)
                e.consume()
            }

            KeyEvent.VK_PAGE_UP -> {
                scrollBar.value = (scrollBar.value - scrollBar.blockIncrement).coerceAtLeast(0)
                e.consume()
            }

            KeyEvent.VK_HOME -> {
                scrollBar.value = 0
                e.consume()
            }

            KeyEvent.VK_END -> {
                scrollBar.value = scrollBar.maximum - scrollBar.visibleAmount
                e.consume()
            }
        }

        if (!e.isConsumed && e.isControlDown) {
            if (e.keyCode == KeyEvent.VK_D) {
                scrollBar.value = (scrollBar.value + scrollBar.blockIncrement / 2)
                    .coerceAtMost(scrollBar.maximum - scrollBar.visibleAmount)
                e.consume()
            } else if (e.keyCode == KeyEvent.VK_U) {
                scrollBar.value = (scrollBar.value - scrollBar.blockIncrement / 2).coerceAtLeast(0)
                e.consume()
            }
        }
    }

    private fun handleWindowCtrlChords(e: KeyEvent, editor: Editor) {
        val ch = when (e.keyCode) {
            KeyEvent.VK_V -> 'v'
            KeyEvent.VK_S -> 's'
            KeyEvent.VK_H -> 'h'
            KeyEvent.VK_J -> 'j'
            KeyEvent.VK_K -> 'k'
            KeyEvent.VK_L -> 'l'
            KeyEvent.VK_W -> 'w'
            KeyEvent.VK_Q -> 'q'
            KeyEvent.VK_C -> 'c'
            KeyEvent.VK_O -> 'o'
            else -> null
        }
        if (ch != null) {
            hide()
            HelixKeyHandler.handleKey(ch, editor)
            e.consume()
        }
    }

    private class RoundedCardPanel(layout: java.awt.LayoutManager, private val minWidth: Int = JBUI.scale(290)) :
        JPanel(layout) {
        init {
            isOpaque = false
        }

        override fun getPreferredSize(): java.awt.Dimension {
            val pref = super.getPreferredSize()
            return java.awt.Dimension(maxOf(pref.width, minWidth), pref.height)
        }

        override fun paintComponent(g: java.awt.Graphics) {
            val g2 = g.create() as java.awt.Graphics2D
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(12)
            g2.color = CARD_BG
            g2.fillRoundRect(0, 0, width, height, arc, arc)
            g2.color = CARD_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.dispose()
            super.paintComponent(g)
        }
    }

    private class KeycapBadge(key: String) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            val label = JBLabel(key, javax.swing.SwingConstants.CENTER)
            label.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(10.5f))
            label.foreground = KEYCAP_FG
            add(label, BorderLayout.CENTER)
            border = JBUI.Borders.empty(1, 4)
        }

        override fun getPreferredSize(): java.awt.Dimension {
            val pref = super.getPreferredSize()
            val minWidth = JBUI.scale(20)
            val h = JBUI.scale(20)
            return java.awt.Dimension(maxOf(pref.width, minWidth), h)
        }

        override fun paintComponent(g: java.awt.Graphics) {
            val g2 = g.create() as java.awt.Graphics2D
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(6)
            g2.color = KEYCAP_BG
            g2.fillRoundRect(0, 0, width, height, arc, arc)
            g2.color = KEYCAP_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.dispose()
            super.paintComponent(g)
        }
    }

    private class WhichKeyRow(val item: WhichKeyItem, initialMode: WhichKeyHintMode, val onClick: () -> Unit) :
        JPanel(BorderLayout(JBUI.scale(8), 0)) {
        private var isHovered = false
        private val descLabel = JBLabel().apply {
            font = JBUI.Fonts.smallFont()
            foreground = ITEM_DESC_COLOR
            border = JBUI.Borders.emptyLeft(6)
        }

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = JBUI.Borders.empty(2, 6)

            val badge = KeycapBadge(item.key)
            add(badge, BorderLayout.WEST)

            val textPanel = JPanel(BorderLayout())
            textPanel.isOpaque = false

            val label = JBLabel(item.label)
            label.font = JBUI.Fonts.label().deriveFont(Font.PLAIN, JBUI.scaleFontSize(11.5f).toFloat())
            label.foreground = ITEM_TEXT_COLOR
            textPanel.add(label, BorderLayout.WEST)
            textPanel.add(descLabel, BorderLayout.EAST)

            add(textPanel, BorderLayout.CENTER)
            updateHintMode(initialMode)

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    isHovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    isHovered = false
                    repaint()
                }

                override fun mouseClicked(e: MouseEvent) {
                    onClick()
                }
            })
        }

        fun updateHintMode(mode: WhichKeyHintMode) {
            val text = when (mode) {
                WhichKeyHintMode.HELIX_COMMAND -> item.helixCommand
                WhichKeyHintMode.INTELLIJ_ACTION -> item.intelliJAction.ifEmpty { item.helixCommand }
            }
            descLabel.text = text
            descLabel.isVisible = text.isNotEmpty() && text != item.label

            toolTipText = when {
                item.intelliJAction.isNotEmpty() -> "IntelliJ Action: ${item.intelliJAction} (${item.helixCommand})"
                item.helixCommand.isNotEmpty() -> "Helix Command: ${item.helixCommand}"
                else -> item.label
            }
            revalidate()
            repaint()
        }

        override fun paintComponent(g: java.awt.Graphics) {
            if (isHovered) {
                val g2 = g.create() as java.awt.Graphics2D
                g2.setRenderingHint(
                    java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
                )
                g2.color = HOVER_BG
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }
}
