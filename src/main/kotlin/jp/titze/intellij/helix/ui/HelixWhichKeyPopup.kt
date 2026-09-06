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
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Font
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities

data class WhichKeyItem(val key: String, val label: String, val description: String = "")

object HelixWhichKeyPopup {
    private var activePopup: JBPopup? = null
    private var currentPrefix: String? = null

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

    private val spaceItems = listOf(
        WhichKeyItem("b", "Buffer / Tab picker", "RecentFiles"),
        WhichKeyItem("f", "File picker", "GotoFile"),
        WhichKeyItem("/", "Global search", "FindInPath"),
        WhichKeyItem("s", "Symbol picker", "Structure"),
        WhichKeyItem("S", "Workspace symbol picker", "GotoSymbol"),
        WhichKeyItem("d", "Diagnostics picker", "ShowError"),
        WhichKeyItem("D", "Workspace diagnostics", "Problems"),
        WhichKeyItem("j", "Jumplist picker", "Jumplist"),
        WhichKeyItem("a", "Code action", "Intentions"),
        WhichKeyItem("r", "Rename symbol", "RenameElement"),
        WhichKeyItem("w", "Save", "SaveAll"),
        WhichKeyItem("y", "Yank main selection", "Clipboard"),
        WhichKeyItem("p", "Paste clipboard after"),
        WhichKeyItem("P", "Paste clipboard before"),
        WhichKeyItem("R", "Replace with clipboard"),
        WhichKeyItem("k", "Hover / Documentation", "QuickJavaDoc"),
        WhichKeyItem("?", "Command palette", "GotoAction"),
    )

    private val gotoItems = listOf(
        WhichKeyItem("d", "Goto definition", "GotoDeclaration"),
        WhichKeyItem("y", "Goto type definition", "GotoTypeDeclaration"),
        WhichKeyItem("r", "Goto reference", "FindUsages"),
        WhichKeyItem("h", "Goto line start"),
        WhichKeyItem("l", "Goto line end"),
        WhichKeyItem("s", "Goto first non-whitespace"),
        WhichKeyItem("g", "Goto line / file start", "line <count>"),
        WhichKeyItem("e", "Goto file end"),
    )

    private val matchItems = listOf(
        WhichKeyItem("s", "Surround add", "ms<char>"),
        WhichKeyItem("r", "Surround replace", "mr<from><to>"),
        WhichKeyItem("d", "Surround delete", "md<char>"),
        WhichKeyItem("m", "Match bracket", "Jump to matching bracket"),
        WhichKeyItem("a", "Select around textobject", "ma<obj>"),
        WhichKeyItem("i", "Select inside textobject", "mi<obj>"),
    )

    private val bracketOpenItems = listOf(
        WhichKeyItem("d", "Previous diagnostic", "GotoPreviousError"),
        WhichKeyItem("D", "First diagnostic", "First error"),
        WhichKeyItem("f", "Previous function", "MethodUp"),
        WhichKeyItem("t", "Previous class", "Previous class/type"),
        WhichKeyItem("a", "Previous parameter", "Previous argument"),
        WhichKeyItem("c", "Previous comment", "Previous comment"),
        WhichKeyItem("T", "Previous test", "Previous test"),
        WhichKeyItem("p", "Previous paragraph", "Previous paragraph"),
        WhichKeyItem("g", "Previous change", "VcsShowPrevChangeMarker"),
        WhichKeyItem("G", "First change", "First change"),
        WhichKeyItem("Space", "Add newline above", "Blank line above"),
        WhichKeyItem("b", "Previous buffer / tab", "PreviousTab"),
    )

    private val bracketCloseItems = listOf(
        WhichKeyItem("d", "Next diagnostic", "GotoNextError"),
        WhichKeyItem("D", "Last diagnostic", "Last error"),
        WhichKeyItem("f", "Next function", "MethodDown"),
        WhichKeyItem("t", "Next class", "Next class/type"),
        WhichKeyItem("a", "Next parameter", "Next argument"),
        WhichKeyItem("c", "Next comment", "Next comment"),
        WhichKeyItem("T", "Next test", "Next test"),
        WhichKeyItem("p", "Next paragraph", "Next paragraph"),
        WhichKeyItem("g", "Next change", "VcsShowNextChangeMarker"),
        WhichKeyItem("G", "Last change", "Last change"),
        WhichKeyItem("Space", "Add newline below", "Blank line below"),
        WhichKeyItem("b", "Next buffer / tab", "NextTab"),
    )

    private val viewItems = listOf(
        WhichKeyItem("c", "Center view", "align_view_center"),
        WhichKeyItem("t", "Align view top", "align_view_top"),
        WhichKeyItem("b", "Align view bottom", "align_view_bottom"),
        WhichKeyItem("m", "Align view middle (horiz)", "align_view_middle"),
        WhichKeyItem("j", "Scroll view down", "scroll_down"),
        WhichKeyItem("k", "Scroll view up", "scroll_up"),
        WhichKeyItem("d", "Scroll half page down", "half_page_down"),
        WhichKeyItem("u", "Scroll half page up", "half_page_up"),
        WhichKeyItem("f", "Scroll page down", "page_down"),
        WhichKeyItem("F", "Scroll page up", "page_up"),
        WhichKeyItem("z", "Center view", "align_view_center"),
    )

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
        val popup = activePopup
        activePopup = null
        popup?.cancel()
    }

    fun show(editor: Editor, prefix: String) {
        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) {
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

        val (title, items) = when (prefix) {
            " " -> "SPACE MENU" to spaceItems
            "g" -> "GOTO MENU" to gotoItems
            "m" -> "MATCH MENU" to matchItems
            "[" -> "JUMP BACK MENU" to bracketOpenItems
            "]" -> "JUMP FORWARD MENU" to bracketCloseItems
            "z" -> "VIEW MENU" to viewItems
            "Z" -> "STICKY VIEW MENU" to viewItems
            else -> return
        }

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
        visibleX: Int = 0,
        visibleY: Int = 0,
        marginX: Int = JBUI.scale(20),
        marginY: Int = JBUI.scale(20),
    ): Point {
        val x = visibleX + (viewWidth - prefWidth - marginX).coerceAtLeast(0)
        val y = visibleY + (viewHeight - prefHeight - marginY).coerceAtLeast(0)
        return Point(x, y)
    }

    private fun createWhichKeyPanel(
        title: String,
        items: List<WhichKeyItem>,
        editor: Editor,
        maxHeight: Int = 600,
    ): JPanel {
        val mainPanel = RoundedCardPanel(BorderLayout())
        mainPanel.isFocusable = true

        // Header: "SPACE MENU" on left, "ESC TO CANCEL" on right
        val headerPanel = JPanel(BorderLayout(JBUI.scale(12), 0))
        headerPanel.isOpaque = false
        headerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER_COLOR),
            JBUI.Borders.empty(12, 14, 10, 14),
        )

        val titleLabel = JBLabel(title)
        titleLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(11.5f).toFloat())
        titleLabel.foreground = TITLE_COLOR
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val cancelLabel = JBLabel("ESC TO CANCEL")
        cancelLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(9.5f).toFloat())
        cancelLabel.foreground = CANCEL_COLOR
        headerPanel.add(cancelLabel, BorderLayout.EAST)

        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Items list
        val itemsPanel = JPanel()
        itemsPanel.layout = javax.swing.BoxLayout(itemsPanel, javax.swing.BoxLayout.Y_AXIS)
        itemsPanel.isOpaque = false
        itemsPanel.border = JBUI.Borders.empty(6, 6, 8, 6)

        for (item in items) {
            val row = WhichKeyRow(item) {
                val state = HelixStateManager.getOrCreate(editor)
                val inStickyView = state.pendingSequence == "Z"
                if (!inStickyView) {
                    hide()
                }
                val triggerChar = if (item.key.equals("Space", ignoreCase = true)) ' ' else item.key[0]
                val handled = HelixKeyHandler.handleKey(triggerChar, editor)
                if (inStickyView) {
                    if (!handled || state.pendingSequence != "Z") {
                        hide()
                    }
                }
            }
            itemsPanel.add(row)
            itemsPanel.add(javax.swing.Box.createVerticalStrut(JBUI.scale(2)))
        }

        val scrollPane = com.intellij.ui.components.JBScrollPane(itemsPanel)
        scrollPane.border = JBUI.Borders.empty()
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
        scrollPane.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

        val prefItemsHeight = itemsPanel.preferredSize.height
        val headerHeight = headerPanel.preferredSize.height
        val totalPreferredHeight = prefItemsHeight + headerHeight + JBUI.scale(10)

        if (totalPreferredHeight > maxHeight) {
            val boundedScrollHeight = maxHeight - headerHeight - JBUI.scale(10)
            scrollPane.preferredSize = java.awt.Dimension(
                itemsPanel.preferredSize.width,
                boundedScrollHeight.coerceAtLeast(JBUI.scale(150)),
            )
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
                }
            }

            override fun keyTyped(e: KeyEvent) {
                val ch = e.keyChar
                if (ch != KeyEvent.CHAR_UNDEFINED && ch != '\u001B') {
                    val state = HelixStateManager.getOrCreate(editor)
                    val inStickyView = state.pendingSequence == "Z"
                    if (!inStickyView) {
                        hide()
                    }
                    val handled = HelixKeyHandler.handleKey(ch, editor)
                    if (inStickyView) {
                        if (!handled || state.pendingSequence != "Z") {
                            hide()
                        }
                    }
                    e.consume()
                }
            }
        })

        return mainPanel
    }

    private class RoundedCardPanel(layout: java.awt.LayoutManager) : JPanel(layout) {
        init {
            isOpaque = false
        }

        override fun getPreferredSize(): java.awt.Dimension {
            val pref = super.getPreferredSize()
            return java.awt.Dimension(maxOf(pref.width, JBUI.scale(290)), pref.height)
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
            label.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11.5f))
            label.foreground = KEYCAP_FG
            add(label, BorderLayout.CENTER)
            border = JBUI.Borders.empty(1, 5)
        }

        override fun getPreferredSize(): java.awt.Dimension {
            val pref = super.getPreferredSize()
            val minWidth = JBUI.scale(22)
            val h = JBUI.scale(22)
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

    private class WhichKeyRow(item: WhichKeyItem, val onClick: () -> Unit) : JPanel(BorderLayout(JBUI.scale(10), 0)) {
        private var isHovered = false

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = JBUI.Borders.empty(4, 8)

            val badge = KeycapBadge(item.key)
            add(badge, BorderLayout.WEST)

            val textPanel = JPanel(BorderLayout())
            textPanel.isOpaque = false

            val label = JBLabel(item.label)
            label.font = JBUI.Fonts.label().deriveFont(Font.PLAIN, JBUI.scaleFontSize(12.5f).toFloat())
            label.foreground = ITEM_TEXT_COLOR
            textPanel.add(label, BorderLayout.WEST)

            if (item.description.isNotEmpty() && item.description != item.label) {
                val desc = JBLabel(item.description)
                desc.font = JBUI.Fonts.smallFont()
                desc.foreground = ITEM_DESC_COLOR
                desc.border = JBUI.Borders.emptyLeft(8)
                textPanel.add(desc, BorderLayout.EAST)
            }

            add(textPanel, BorderLayout.CENTER)

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
