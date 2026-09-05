package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.GridLayout
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities

data class WhichKeyItem(
    val key: String,
    val label: String,
    val description: String = ""
)

object HelixWhichKeyPopup {
    private var activePopup: JBPopup? = null

    private val spaceItems = listOf(
        WhichKeyItem("f", "file_picker", "GotoFile"),
        WhichKeyItem("b", "buffer_picker", "RecentFiles"),
        WhichKeyItem("/", "global_search", "FindInPath"),
        WhichKeyItem("s", "symbol_picker", "Structure"),
        WhichKeyItem("S", "workspace_symbol_picker", "GotoSymbol"),
        WhichKeyItem("d", "diagnostics_picker", "ShowError"),
        WhichKeyItem("D", "workspace_diagnostics", "Problems"),
        WhichKeyItem("j", "jumplist_picker", "RecentLocations"),
        WhichKeyItem("a", "code_action", "Intentions"),
        WhichKeyItem("r", "rename_symbol", "RenameElement"),
        WhichKeyItem("w", "save", "SaveAll"),
        WhichKeyItem("y", "yank_main_selection", "Clipboard"),
        WhichKeyItem("p", "paste_clipboard_after"),
        WhichKeyItem("P", "paste_clipboard_before"),
        WhichKeyItem("R", "replace_with_clipboard"),
        WhichKeyItem("k", "hover", "QuickJavaDoc"),
        WhichKeyItem("?", "command_palette", "GotoAction")
    )

    private val gotoItems = listOf(
        WhichKeyItem("d", "goto_definition", "GotoDeclaration"),
        WhichKeyItem("y", "goto_type_definition", "GotoTypeDeclaration"),
        WhichKeyItem("r", "goto_reference", "FindUsages"),
        WhichKeyItem("h", "goto_line_start"),
        WhichKeyItem("l", "goto_line_end"),
        WhichKeyItem("s", "goto_first_nonwhitespace"),
        WhichKeyItem("g", "goto_file_start", "line <count>"),
        WhichKeyItem("e", "goto_file_end")
    )

    private val matchItems = listOf(
        WhichKeyItem("s", "surround_add", "ms<char>"),
        WhichKeyItem("r", "surround_replace", "mr<from><to>"),
        WhichKeyItem("d", "surround_delete", "md<char>"),
        WhichKeyItem("m", "match_bracket", "Jump to matching bracket"),
        WhichKeyItem("a", "select_around_textobject", "ma<obj>"),
        WhichKeyItem("i", "select_inside_textobject", "mi<obj>")
    )

    private val bracketOpenItems = listOf(
        WhichKeyItem("d", "goto_prev_diag", "GotoPreviousError"),
        WhichKeyItem("D", "goto_first_diag", "First error"),
        WhichKeyItem("f", "goto_prev_function", "MethodUp"),
        WhichKeyItem("t", "goto_prev_class", "Previous class/type"),
        WhichKeyItem("a", "goto_prev_parameter", "Previous argument"),
        WhichKeyItem("c", "goto_prev_comment", "Previous comment"),
        WhichKeyItem("T", "goto_prev_test", "Previous test"),
        WhichKeyItem("p", "goto_prev_paragraph", "Previous paragraph"),
        WhichKeyItem("g", "goto_prev_change", "VcsShowPrevChangeMarker"),
        WhichKeyItem("G", "goto_first_change", "First change"),
        WhichKeyItem("Space", "add_newline_above", "Blank line above"),
        WhichKeyItem("b", "goto_prev_buffer", "PreviousTab")
    )

    private val bracketCloseItems = listOf(
        WhichKeyItem("d", "goto_next_diag", "GotoNextError"),
        WhichKeyItem("D", "goto_last_diag", "Last error"),
        WhichKeyItem("f", "goto_next_function", "MethodDown"),
        WhichKeyItem("t", "goto_next_class", "Next class/type"),
        WhichKeyItem("a", "goto_next_parameter", "Next argument"),
        WhichKeyItem("c", "goto_next_comment", "Next comment"),
        WhichKeyItem("T", "goto_next_test", "Next test"),
        WhichKeyItem("p", "goto_next_paragraph", "Next paragraph"),
        WhichKeyItem("g", "goto_next_change", "VcsShowNextChangeMarker"),
        WhichKeyItem("G", "goto_last_change", "Last change"),
        WhichKeyItem("Space", "add_newline_below", "Blank line below"),
        WhichKeyItem("b", "goto_next_buffer", "NextTab")
    )

    fun hide() {
        val app = ApplicationManager.getApplication()
        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { hide() }
            return
        }
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

        hide()

        val (title, items) = when (prefix) {
            " " -> "SPACE: Pickers & Actions" to spaceItems
            "g" -> "GOTO (g): Navigation" to gotoItems
            "m" -> "MATCH (m): Surround & Textobjects" to matchItems
            "[" -> "JUMP BACK ([): Unimpaired" to bracketOpenItems
            "]" -> "JUMP FORWARD (]): Unimpaired" to bracketCloseItems
            else -> return
        }

        val panel = createWhichKeyPanel(title, items, editor)
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(false)
            .createPopup()

        activePopup = popup

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (activePopup === popup) {
                    activePopup = null
                }
            }
        })

        val component = editor.component
        val visibleRect = component.visibleRect
        val viewWidth = if (visibleRect.width > 0) visibleRect.width else component.width
        val viewHeight = if (visibleRect.height > 0) visibleRect.height else component.height

        if (component.isShowing && viewWidth > 0 && viewHeight > 0) {
            val prefSize = panel.preferredSize
            val pos = calculatePopupPosition(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                prefWidth = prefSize.width,
                prefHeight = prefSize.height,
                visibleX = visibleRect.x,
                visibleY = visibleRect.y
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
        marginY: Int = JBUI.scale(20)
    ): Point {
        val x = visibleX + (viewWidth - prefWidth - marginX).coerceAtLeast(0)
        val y = visibleY + (viewHeight - prefHeight - marginY).coerceAtLeast(0)
        return Point(x, y)
    }

    private fun createWhichKeyPanel(title: String, items: List<WhichKeyItem>, editor: Editor): JPanel {
        val mainPanel = JPanel(BorderLayout(0, 8))
        mainPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(0x03, 0xC7, 0xD3, 140), Color(0x03, 0xC7, 0xD3, 140)), 1),
            JBUI.Borders.empty(10, 14)
        )
        mainPanel.background = UIUtil.getPanelBackground()
        mainPanel.isFocusable = true

        // Header
        val headerPanel = JPanel(BorderLayout())
        headerPanel.isOpaque = false
        val titleLabel = JBLabel(title)
        titleLabel.font = JBUI.Fonts.label().asBold()
        titleLabel.foreground = JBColor(Color(0x03, 0xC7, 0xD3), Color(0x03, 0xC7, 0xD3))
        headerPanel.add(titleLabel, BorderLayout.WEST)
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Grid of items: 2 columns if > 4 items, else 1 column
        val columns = if (items.size > 4) 2 else 1
        val rows = (items.size + columns - 1) / columns
        val gridPanel = JPanel(GridLayout(rows, columns, 16, 4))
        gridPanel.isOpaque = false

        for (item in items) {
            val itemPanel = JPanel(BorderLayout(8, 0))
            itemPanel.isOpaque = false
            itemPanel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            val keyBadge = JBLabel(item.key)
            keyBadge.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(12f))
            keyBadge.foreground = JBColor(Color(0x03, 0xC7, 0xD3), Color(0x03, 0xC7, 0xD3))
            keyBadge.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(Color(0x03, 0xC7, 0xD3, 120), Color(0x03, 0xC7, 0xD3, 120)), 1),
                JBUI.Borders.empty(1, 5)
            )

            val text = if (item.description.isNotEmpty()) "${item.label} (${item.description})" else item.label
            val descLabel = JBLabel(text)
            descLabel.font = JBUI.Fonts.smallFont()

            itemPanel.add(keyBadge, BorderLayout.WEST)
            itemPanel.add(descLabel, BorderLayout.CENTER)

            itemPanel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    hide()
                    HelixKeyHandler.handleKey(item.key[0], editor)
                }
            })

            gridPanel.add(itemPanel)
        }
        mainPanel.add(gridPanel, BorderLayout.CENTER)

        // Footer hint
        val footerLabel = JBLabel("Press key to execute | Esc to dismiss")
        footerLabel.font = JBUI.Fonts.miniFont()
        footerLabel.foreground = UIUtil.getContextHelpForeground()
        footerLabel.border = JBUI.Borders.emptyTop(4)
        mainPanel.add(footerLabel, BorderLayout.SOUTH)

        mainPanel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    val state = HelixStateManager.getOrCreate(editor)
                    state.clearPendingSequence()
                    hide()
                    e.consume()
                }
            }

            override fun keyTyped(e: KeyEvent) {
                val ch = e.keyChar
                if (ch != KeyEvent.CHAR_UNDEFINED && ch != '\u001B') {
                    hide()
                    HelixKeyHandler.handleKey(ch, editor)
                    e.consume()
                }
            }
        })

        return mainPanel
    }
}
