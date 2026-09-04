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
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.action.HelixActionDelegate
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
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

    val COMMANDS = listOf(
        HelixCommandItem("write", listOf("w"), "Save all modified files") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
        },
        HelixCommandItem("quit", listOf("q"), "Close active editor tab") { editor ->
            HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem("wq", listOf("x"), "Save all and close active tab") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
            ApplicationManager.getApplication().invokeLater {
                HelixActionDelegate.executeAction("CloseContent", editor)
            }
        },
        HelixCommandItem("wa", emptyList(), "Save all modified buffers") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
        },
        HelixCommandItem("qa", emptyList(), "Close all editor tabs") { editor ->
            HelixActionDelegate.executeAction("CloseAllEditors", editor)
        },
        HelixCommandItem("vsplit", listOf("vsp"), "Split editor vertically") { editor ->
            HelixActionDelegate.executeAction("SplitVertically", editor)
        },
        HelixCommandItem("hsplit", listOf("sp"), "Split editor horizontally") { editor ->
            HelixActionDelegate.executeAction("SplitHorizontally", editor)
        },
        HelixCommandItem("format", emptyList(), "Reformat code") { editor ->
            HelixActionDelegate.executeAction("ReformatCode", editor)
        },
        HelixCommandItem("reload", listOf("e!"), "Reload / synchronize file from disk") { editor ->
            HelixActionDelegate.executeAction("SynchronizeCurrentFile", editor)
        },
        HelixCommandItem("open", emptyList(), "Open file picker") { editor ->
            HelixActionDelegate.executeAction("GotoFile", editor) || HelixActionDelegate.executeAction("SearchEverywhere", editor)
        },
        HelixCommandItem("buffer", listOf("b"), "Open buffer switcher") { editor ->
            HelixActionDelegate.executeAction("RecentFiles", editor)
        },
        HelixCommandItem("find", emptyList(), "Find in project files") { editor ->
            HelixActionDelegate.executeAction("FindInPath", editor)
        }
    )

    fun show(editor: Editor) {
        val app = ApplicationManager.getApplication()
        if (app != null && (app.isUnitTestMode || app.isHeadlessEnvironment)) {
            return
        }

        if (app != null && !app.isDispatchThread) {
            SwingUtilities.invokeLater { show(editor) }
            return
        }

        val mainPanel = JPanel(BorderLayout(0, 8))
        mainPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(0x03, 0xC7, 0xD3, 140), Color(0x03, 0xC7, 0xD3, 140)), 1),
            JBUI.Borders.empty(10, 12)
        )
        mainPanel.background = UIUtil.getPanelBackground()
        mainPanel.preferredSize = Dimension(420, 260)

        // Top prompt panel
        val promptPanel = JPanel(BorderLayout(8, 0))
        promptPanel.isOpaque = false

        val promptBadge = JBLabel(" : ")
        promptBadge.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(13f).toInt())
        promptBadge.foreground = JBColor(Color(0x03, 0xC7, 0xD3), Color(0x03, 0xC7, 0xD3))
        promptBadge.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(0x03, 0xC7, 0xD3, 120), Color(0x03, 0xC7, 0xD3, 120)), 1),
            JBUI.Borders.empty(1, 4)
        )
        promptPanel.add(promptBadge, BorderLayout.WEST)

        val textField = JBTextField()
        textField.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(13f).toInt())
        textField.emptyText.text = "type command (w, q, wq, vsp, sp, format, ...)"
        promptPanel.add(textField, BorderLayout.CENTER)
        mainPanel.add(promptPanel, BorderLayout.NORTH)

        // Command suggestion list
        val listModel = DefaultListModel<HelixCommandItem>()
        COMMANDS.forEach { listModel.addElement(it) }

        val list = JBList(listModel)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        list.isOpaque = false
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val item = value as? HelixCommandItem
                val panel = JPanel(BorderLayout(8, 0))
                panel.border = JBUI.Borders.empty(3, 6)

                if (isSelected) {
                    panel.background = JBColor(Color(0x03, 0xC7, 0xD3, 45), Color(0x03, 0xC7, 0xD3, 45))
                } else {
                    panel.isOpaque = false
                }

                val cmdLabel = JBLabel(item?.displayCommand ?: "")
                cmdLabel.font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f).toInt())
                cmdLabel.foreground = if (isSelected) {
                    JBColor(Color(0x03, 0xC7, 0xD3), Color(0x03, 0xC7, 0xD3))
                } else {
                    UIUtil.getLabelForeground()
                }

                val descLabel = JBLabel(item?.description ?: "")
                descLabel.font = JBUI.Fonts.miniFont()
                descLabel.foreground = UIUtil.getContextHelpForeground()

                panel.add(cmdLabel, BorderLayout.WEST)
                panel.add(descLabel, BorderLayout.CENTER)
                return panel
            }
        }

        val scrollPane = JBScrollPane(list)
        scrollPane.border = JBUI.Borders.empty()
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // Footer hint
        val footerLabel = JBLabel("Enter: run | Tab: complete | ↑/↓: select | Esc: cancel")
        footerLabel.font = JBUI.Fonts.miniFont()
        footerLabel.foreground = UIUtil.getContextHelpForeground()
        footerLabel.border = JBUI.Borders.emptyTop(4)
        mainPanel.add(footerLabel, BorderLayout.SOUTH)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, textField)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(false)
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

        // Fallbacks for standard vim/helix commands
        when (cleanCmd) {
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
        }
    }
}
