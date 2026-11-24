// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.harpoon.HarpoonSlotState
import jp.titze.intellij.helix.harpoon.HelixHarpoonEntry
import jp.titze.intellij.helix.harpoon.HelixHarpoonService
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
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

data class HarpoonListItem(val slotIndex: Int, val entry: HelixHarpoonEntry?, val dormant: HarpoonSlotState? = null)

object HelixHarpoonPopup {

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

    private class KeycapBadge(initialText: String = "", initialActive: Boolean = false) : JPanel(BorderLayout()) {
        private var isActive = initialActive
        private val label = JBLabel(initialText).apply {
            font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f))
            foreground = if (initialActive) KEYCAP_CURRENT_FG else KEYCAP_FG
            border = JBUI.Borders.empty(2, 6)
        }

        init {
            isOpaque = false
            add(label, BorderLayout.CENTER)
        }

        fun updateBadge(text: String, active: Boolean) {
            isActive = active
            label.text = text
            label.foreground = if (active) KEYCAP_CURRENT_FG else KEYCAP_FG
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = if (isActive) KEYCAP_CURRENT_BG else KEYCAP_BG
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
            g2.color = KEYCAP_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(6), JBUI.scale(6))
            g2.dispose()
        }
    }

    fun show(editor: Editor) {
        val project = editor.project ?: return
        val service = HelixHarpoonService.getInstance(project)

        val items = (0 until HelixHarpoonService.SLOT_COUNT).map { i ->
            val entry = service.getSlot(i)
            HarpoonListItem(i, entry, if (entry == null) service.getDormantSlot(i) else null)
        }
        val listModel = CollectionListModel(items)
        val list = JBList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            isOpaque = false
            background = CARD_BG
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            selectedIndex = 0
        }
        HelixListUI.install(list)
        list.fixedCellHeight = JBUI.scale(30)
        list.cellRenderer = createCellRenderer(project)

        val scrollPane = com.intellij.ui.components.JBScrollPane(list).apply {
            border = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        }

        val cardPanel = RoundedCardPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12, 14)
            preferredSize = Dimension(JBUI.scale(620), JBUI.scale(210))
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(8)
            val titleLabel = JBLabel("Harpoon / Quick Slots").apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())
                foreground = TITLE_COLOR
            }
            val hintLabel = JBLabel("1-5 jump · a pin · d unpin · J/K reorder").apply {
                font = font.deriveFont(Font.PLAIN, JBUI.scaleFontSize(10f).toFloat())
                foreground = CANCEL_COLOR
            }
            add(titleLabel, BorderLayout.WEST)
            add(hintLabel, BorderLayout.EAST)
        }

        cardPanel.add(headerPanel, BorderLayout.NORTH)
        cardPanel.add(scrollPane, BorderLayout.CENTER)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(cardPanel, list)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .createPopup()

        fun refreshList() {
            val selected = list.selectedIndex
            val updated = (0 until HelixHarpoonService.SLOT_COUNT).map { i ->
                val entry = service.getSlot(i)
                HarpoonListItem(i, entry, if (entry == null) service.getDormantSlot(i) else null)
            }
            listModel.replaceAll(updated)
            list.selectedIndex = selected.coerceIn(0, HelixHarpoonService.SLOT_COUNT - 1)
            list.repaint()
        }

        setupKeyListeners(list, popup, editor, service, ::refreshList)
        setupMouseListeners(list, popup, editor, service)

        popup.showInBestPositionFor(editor)
    }

    private fun setupKeyListeners(
        list: JBList<HarpoonListItem>,
        popup: JBPopup,
        editor: Editor,
        service: HelixHarpoonService,
        refreshList: () -> Unit,
    ) {
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ESCAPE, KeyEvent.VK_Q -> popup.cancel()

                    KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                        val sel = list.selectedValue
                        if (sel?.entry != null) {
                            popup.cancel()
                            service.jumpToSlot(sel.slotIndex, editor)
                        }
                    }

                    KeyEvent.VK_A -> {
                        val sel = list.selectedValue
                        if (sel != null) {
                            service.setSlot(sel.slotIndex, editor)
                        } else {
                            service.pinCurrent(editor)
                        }
                        refreshList()
                    }

                    KeyEvent.VK_D, KeyEvent.VK_X, KeyEvent.VK_DELETE -> {
                        val sel = list.selectedValue
                        if (sel != null) {
                            service.clearSlot(sel.slotIndex)
                            refreshList()
                        }
                    }

                    KeyEvent.VK_J, KeyEvent.VK_DOWN -> {
                        if (e.isShiftDown) {
                            val cur = list.selectedIndex
                            if (cur < HelixHarpoonService.SLOT_COUNT - 1) {
                                service.swapSlots(cur, cur + 1)
                                list.selectedIndex = cur + 1
                                refreshList()
                            }
                        } else {
                            val next = (list.selectedIndex + 1).coerceAtMost(HelixHarpoonService.SLOT_COUNT - 1)
                            list.selectedIndex = next
                        }
                        e.consume()
                    }

                    KeyEvent.VK_K, KeyEvent.VK_UP -> {
                        if (e.isShiftDown) {
                            val cur = list.selectedIndex
                            if (cur > 0) {
                                service.swapSlots(cur, cur - 1)
                                list.selectedIndex = cur - 1
                                refreshList()
                            }
                        } else {
                            val prev = (list.selectedIndex - 1).coerceAtLeast(0)
                            list.selectedIndex = prev
                        }
                        e.consume()
                    }

                    in KeyEvent.VK_1..KeyEvent.VK_5 -> {
                        val slot = e.keyCode - KeyEvent.VK_1
                        if (service.getSlot(slot) != null) {
                            popup.cancel()
                            service.jumpToSlot(slot, editor)
                        }
                    }
                }
            }
        })
    }

    private fun setupMouseListeners(
        list: JBList<HarpoonListItem>,
        popup: JBPopup,
        editor: Editor,
        service: HelixHarpoonService,
    ) {
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val idx = list.locationToIndex(e.point)
                if (idx >= 0) {
                    val item = list.model.getElementAt(idx)
                    if (item?.entry != null) {
                        popup.cancel()
                        service.jumpToSlot(item.slotIndex, editor)
                    }
                }
            }
        })
    }

    private fun formatDirectory(entry: HelixHarpoonEntry, project: Project): String {
        val basePath = project.basePath
        val fullPath = entry.virtualFile.path
        if (basePath != null && fullPath.startsWith(basePath)) {
            val relDir = fullPath.removePrefix(basePath).trimStart('/').substringBeforeLast('/', "")
            if (relDir.isEmpty()) return ""
            if (relDir.length <= 26) return relDir
            val parts = relDir.split('/')
            return if (parts.size >= 2) "…/${parts.takeLast(2).joinToString("/")}" else "…/${parts.last()}"
        }
        return entry.virtualFile.parent?.name ?: ""
    }

    private fun formatSnippet(previewText: String): String {
        val preview = previewText.trim().replace("\r\n", " ").replace("\n", " ")
        if (preview.isEmpty()) return ""
        val maxLen = jp.titze.intellij.helix.settings.HelixSettings.instance.whichKeySnippetMaxLength.coerceIn(16, 36)
        return if (preview.length > maxLen) {
            preview.take(maxLen - 1).trimEnd() + "…"
        } else {
            preview
        }
    }

    private fun createCellRenderer(project: Project): ListCellRenderer<HarpoonListItem> =
        object : ListCellRenderer<HarpoonListItem> {
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

            private val badge = KeycapBadge()
            private val contentPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply { isOpaque = false }
            private val nameLabel = JBLabel().apply {
                font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
                foreground = ITEM_TEXT_COLOR
            }
            private val dirLabel = JBLabel().apply {
                font = JBUI.Fonts.smallFont()
                foreground = ITEM_PATH_COLOR
            }
            private val snippetLabel = JBLabel().apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f))
                foreground = ITEM_SNIPPET_COLOR
            }
            private val westDetails = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                add(nameLabel)
                add(Box.createHorizontalStrut(JBUI.scale(6)))
                add(dirLabel)
            }

            init {
                cellPanel.add(badge, BorderLayout.WEST)
                contentPanel.add(westDetails, BorderLayout.CENTER)
                contentPanel.add(snippetLabel, BorderLayout.EAST)
                cellPanel.add(contentPanel, BorderLayout.CENTER)
            }

            override fun getListCellRendererComponent(
                list: JList<out HarpoonListItem>?,
                value: HarpoonListItem?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                cellPanel.isSelectedRow = isSelected
                if (value == null) return cellPanel

                val entry = value.entry
                val dormant = value.dormant
                badge.updateBadge((value.slotIndex + 1).toString(), entry != null || dormant != null)

                when {
                    entry != null -> renderActiveEntry(entry)
                    dormant != null -> renderDormantEntry(dormant)
                    else -> renderEmptySlot(value.slotIndex)
                }

                return cellPanel
            }

            private fun renderActiveEntry(entry: HelixHarpoonEntry) {
                val fileName = entry.virtualFile.name
                val displayName = if (fileName.length > 36) fileName.take(35) + "…" else fileName
                nameLabel.text = "$displayName:${entry.line}"
                nameLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
                nameLabel.foreground = ITEM_TEXT_COLOR

                val dir = formatDirectory(entry, project)
                dirLabel.text = dir
                dirLabel.isVisible = dir.isNotEmpty()

                val snippet = formatSnippet(entry.previewText)
                snippetLabel.text = snippet
                snippetLabel.isVisible = snippet.isNotEmpty()

                cellPanel.toolTipText = "${entry.virtualFile.path}:${entry.line}\n${entry.previewText}"
            }

            private fun renderDormantEntry(dormant: HarpoonSlotState) {
                val fileName = dormant.fileUrl.substringAfterLast('/')
                val displayName = if (fileName.length > 36) fileName.take(35) + "…" else fileName
                nameLabel.text = "$displayName:${dormant.line}"
                nameLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
                nameLabel.foreground = ITEM_PATH_COLOR

                dirLabel.text = "[other branch]"
                dirLabel.isVisible = true

                val snippet = formatSnippet(dormant.previewText)
                snippetLabel.text = snippet
                snippetLabel.isVisible = snippet.isNotEmpty()

                cellPanel.toolTipText = "${dormant.fileUrl}:${dormant.line}\n(File not found on current branch)"
            }

            private fun renderEmptySlot(slotIndex: Int) {
                nameLabel.text = "(empty slot ${slotIndex + 1})"
                nameLabel.font = nameLabel.font.deriveFont(Font.ITALIC, JBUI.scaleFontSize(11f).toFloat())
                nameLabel.foreground = CANCEL_COLOR
                dirLabel.text = ""
                dirLabel.isVisible = false
                snippetLabel.text = ""
                snippetLabel.isVisible = false
                cellPanel.toolTipText = null
            }
        }
}
