// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.config.HelixConfigLoader
import jp.titze.intellij.helix.keymap.BindingSource
import jp.titze.intellij.helix.keymap.HelixKeybindingRow
import jp.titze.intellij.helix.keymap.HelixKeymapExtractor
import jp.titze.intellij.helix.keymap.KeymapPrefixGroup
import jp.titze.intellij.helix.state.HelixMode
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.font.TextAttribute
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Visual keymap explorer panel for browsing, searching, and inspecting Helix bindings.
 */
class HelixKeymapBrowserPanel {

    private val modeComboBox = JComboBox(arrayOf(HelixMode.NORMAL, HelixMode.SELECT, HelixMode.INSERT))
    private val prefixComboBox = JComboBox(KeymapPrefixGroup.values())
    private val searchField = SearchTextField()
    private val recordButton = JButton("⌨")
    private val statusLabel = JBLabel()

    private val tableModel = HelixKeymapTableModel()
    val table = JBTable(tableModel)
    val panel: JComponent = createPanel()

    private var allRowsForCurrentMode: List<HelixKeybindingRow> = emptyList()

    init {
        setupListeners()
        setupTable()
        reloadModeData()
    }

    private fun createPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 10))
        root.border = JBUI.Borders.empty(12)

        root.add(createTopFilterBar(), BorderLayout.NORTH)
        root.add(JScrollPane(table), BorderLayout.CENTER)
        root.add(createBottomActionBar(), BorderLayout.SOUTH)

        return root
    }

    private fun createTopFilterBar(): JPanel {
        val bar = JPanel(BorderLayout(10, 0))

        val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        leftControls.add(JBLabel(HelixBundle.message("settings.keymap.mode.label")))
        leftControls.add(modeComboBox)
        leftControls.add(Box.createHorizontalStrut(JBUI.scale(10)))
        leftControls.add(JBLabel(HelixBundle.message("settings.keymap.prefix.label")))
        leftControls.add(prefixComboBox)

        val rightControls = JPanel(BorderLayout(4, 0))
        searchField.textEditor.emptyText.text = HelixBundle.message("settings.keymap.search.placeholder")
        recordButton.toolTipText = HelixBundle.message("settings.keymap.record.tooltip")
        rightControls.add(searchField, BorderLayout.CENTER)
        rightControls.add(recordButton, BorderLayout.EAST)

        bar.add(leftControls, BorderLayout.WEST)
        bar.add(rightControls, BorderLayout.CENTER)
        return bar
    }

    private fun createBottomActionBar(): JPanel {
        val bar = JPanel(BorderLayout())

        val leftActions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        val openTomlBtn = JButton(HelixBundle.message("settings.keymap.btn.openToml"))
        openTomlBtn.addActionListener {
            val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
            HelixConfigLoader.openOrCreateConfigFile(project)
        }

        val refreshBtn = JButton(HelixBundle.message("settings.keymap.btn.refresh"))
        refreshBtn.addActionListener {
            reloadModeData()
        }

        leftActions.add(openTomlBtn)
        leftActions.add(refreshBtn)

        val rightStatus = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        statusLabel.font = JBUI.Fonts.smallFont()
        statusLabel.foreground = UIUtil.getContextHelpForeground()
        rightStatus.add(statusLabel)

        bar.add(leftActions, BorderLayout.WEST)
        bar.add(rightStatus, BorderLayout.EAST)
        return bar
    }

    private fun setupTable() {
        table.autoCreateRowSorter = true
        table.fillsViewportHeight = true
        table.setShowGrid(false)
        table.rowHeight = JBUI.scale(24)

        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(120)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(220)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(280)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(120)

        table.columnModel.getColumn(3).cellRenderer = SourceCellRenderer()
    }

    private fun setupListeners() {
        modeComboBox.addActionListener { reloadModeData() }
        prefixComboBox.addActionListener { applyFilter() }
        searchField.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                applyFilter()
            }
        })
        recordButton.addActionListener {
            val dialog = HelixShortcutCaptureDialog(panel)
            if (dialog.showAndGet()) {
                searchField.text = dialog.recordedShortcut
            }
        }
    }

    fun reloadModeData() {
        val mode = modeComboBox.selectedItem as? HelixMode ?: HelixMode.NORMAL
        allRowsForCurrentMode = HelixKeymapExtractor.extractRows(mode)
        applyFilter()
    }

    private fun applyFilter() {
        val selectedPrefix = prefixComboBox.selectedItem as? KeymapPrefixGroup ?: KeymapPrefixGroup.ALL
        val query = searchField.text.trim().lowercase()

        val filtered = allRowsForCurrentMode.filter { row ->
            matchesPrefix(row, selectedPrefix) && matchesQuery(row, query)
        }

        tableModel.setRows(filtered)
        updateStatus(filtered.size)
    }

    private fun matchesPrefix(row: HelixKeybindingRow, prefix: KeymapPrefixGroup): Boolean =
        prefix == KeymapPrefixGroup.ALL || row.prefixGroup == prefix

    private fun matchesQuery(row: HelixKeybindingRow, query: String): Boolean {
        if (query.isEmpty()) return true
        return row.displayShortcut.lowercase().contains(query) ||
            row.rawSequence.lowercase().contains(query) ||
            row.command.lowercase().contains(query) ||
            row.description.lowercase().contains(query)
    }

    private fun updateStatus(visibleCount: Int) {
        val customCount = allRowsForCurrentMode.count { it.source != BindingSource.DEFAULT }
        statusLabel.text = HelixBundle.message("settings.keymap.status.count", visibleCount, customCount)
    }

    class HelixKeymapTableModel : AbstractTableModel() {
        private val columns = listOf(
            HelixBundle.message("settings.keymap.col.shortcut"),
            HelixBundle.message("settings.keymap.col.command"),
            HelixBundle.message("settings.keymap.col.description"),
            HelixBundle.message("settings.keymap.col.source"),
        )

        private var rows: List<HelixKeybindingRow> = emptyList()

        fun setRows(newRows: List<HelixKeybindingRow>) {
            rows = newRows
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        fun getRow(index: Int): HelixKeybindingRow? = rows.getOrNull(index)

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows.getOrNull(rowIndex) ?: return ""
            return when (columnIndex) {
                0 -> row.displayShortcut
                1 -> row.command
                2 -> row.description
                3 -> row.source
                else -> ""
            }
        }
    }

    private class SourceCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val source = value as? BindingSource ?: return comp

            text = "[${source.displayName}]"
            if (!isSelected) {
                when (source) {
                    BindingSource.DEFAULT -> {
                        foreground = UIUtil.getContextHelpForeground()
                    }

                    BindingSource.CUSTOM_INTELLIJ -> {
                        foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                    }

                    BindingSource.CUSTOM_CONFIG -> {
                        foreground = JBUI.CurrentTheme.Focus.focusColor()
                    }

                    BindingSource.NO_OP -> {
                        foreground = JBUI.CurrentTheme.Label.disabledForeground()
                        val attributes = font.attributes.toMutableMap()
                        attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
                        font = font.deriveFont(attributes)
                    }
                }
            }
            return comp
        }
    }
}
