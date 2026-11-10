// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.config.HelixConfigLoader
import jp.titze.intellij.helix.config.HelixConfigModifier
import jp.titze.intellij.helix.keymap.BindingSource
import jp.titze.intellij.helix.keymap.HelixKeybindingRow
import jp.titze.intellij.helix.keymap.HelixKeymapExtractor
import jp.titze.intellij.helix.keymap.KeymapPrefixGroup
import jp.titze.intellij.helix.state.HelixMode
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Visual keymap explorer panel for browsing, searching, and inspecting Helix bindings.
 */
class HelixKeymapBrowserPanel {

    private val modeComboBox = ComboBox(arrayOf(HelixMode.NORMAL, HelixMode.SELECT, HelixMode.INSERT))
    private val prefixComboBox = ComboBox(KeymapPrefixGroup.entries.toTypedArray())
    private val searchField = SearchTextField()
    private val recordButton = JButton(HelixBundle.message("settings.keymap.btn.findByKeystroke"))
    private val statusLabel = JBLabel()

    val addBindingBtn = JButton(HelixBundle.message("settings.keymap.btn.add"))
    val editBindingBtn = JButton(HelixBundle.message("settings.keymap.btn.edit"))
    val resetBindingBtn = JButton(HelixBundle.message("settings.keymap.btn.reset"))
    val disableBindingBtn = JButton(HelixBundle.message("settings.keymap.btn.disable"))

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
        val bar = JPanel(BorderLayout(0, 6))

        val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        leftControls.add(JBLabel(HelixBundle.message("settings.keymap.mode.label")))
        leftControls.add(modeComboBox)
        leftControls.add(Box.createHorizontalStrut(JBUI.scale(10)))
        leftControls.add(JBLabel(HelixBundle.message("settings.keymap.prefix.label")))
        leftControls.add(prefixComboBox)

        val rightControls = JPanel(BorderLayout(6, 0))
        searchField.textEditor.emptyText.text = HelixBundle.message("settings.keymap.search.placeholder")
        recordButton.toolTipText = HelixBundle.message("settings.keymap.record.tooltip")

        rightControls.add(searchField, BorderLayout.CENTER)
        rightControls.add(recordButton, BorderLayout.EAST)

        bar.add(leftControls, BorderLayout.NORTH)
        bar.add(rightControls, BorderLayout.SOUTH)
        return bar
    }

    private fun createBottomActionBar(): JPanel {
        val bar = JPanel(BorderLayout(0, 6))

        val primaryActions = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val openTomlBtn = JButton(HelixBundle.message("settings.keymap.btn.openToml"))
        openTomlBtn.addActionListener {
            val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
            HelixConfigLoader.openOrCreateConfigFile(project)
        }

        val refreshBtn = JButton(HelixBundle.message("settings.keymap.btn.refresh"))
        refreshBtn.addActionListener {
            reloadModeData()
        }

        addBindingBtn.addActionListener { showAddBindingDialog() }
        editBindingBtn.addActionListener { showEditBindingDialog() }
        resetBindingBtn.addActionListener { resetSelectedBinding() }
        disableBindingBtn.addActionListener { disableSelectedBinding() }

        updateActionButtonsState()

        primaryActions.add(addBindingBtn)
        primaryActions.add(editBindingBtn)
        primaryActions.add(resetBindingBtn)
        primaryActions.add(disableBindingBtn)

        val secondaryBar = JPanel(BorderLayout())
        val secondaryActions = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        secondaryActions.add(openTomlBtn)
        secondaryActions.add(refreshBtn)

        val rightStatus = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        statusLabel.font = JBUI.Fonts.smallFont()
        statusLabel.foreground = UIUtil.getContextHelpForeground()
        rightStatus.add(statusLabel)

        secondaryBar.add(secondaryActions, BorderLayout.WEST)
        secondaryBar.add(rightStatus, BorderLayout.EAST)

        bar.add(primaryActions, BorderLayout.NORTH)
        bar.add(secondaryBar, BorderLayout.SOUTH)
        return bar
    }

    private fun setupTable() {
        table.autoCreateRowSorter = true
        table.fillsViewportHeight = true
        table.setShowGrid(false)
        table.rowHeight = JBUI.scale(24)
        table.preferredScrollableViewportSize = Dimension(JBUI.scale(500), JBUI.scale(220))
        table.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS

        val col0 = table.columnModel.getColumn(0)
        col0.preferredWidth = JBUI.scale(85)
        col0.minWidth = JBUI.scale(65)
        col0.maxWidth = JBUI.scale(110)

        val col1 = table.columnModel.getColumn(1)
        col1.preferredWidth = JBUI.scale(180)
        col1.minWidth = JBUI.scale(130)
        col1.maxWidth = JBUI.scale(260)

        val col2 = table.columnModel.getColumn(2)
        col2.preferredWidth = JBUI.scale(220)
        col2.minWidth = JBUI.scale(140)

        val col3 = table.columnModel.getColumn(3)
        col3.preferredWidth = JBUI.scale(75)
        col3.minWidth = JBUI.scale(60)
        col3.maxWidth = JBUI.scale(95)
        col3.cellRenderer = SourceCellRenderer()

        table.selectionModel.addListSelectionListener {
            updateActionButtonsState()
        }
        setupTableInteractions()
    }

    private fun setupTableInteractions() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    showEditBindingDialog()
                }
            }

            override fun mousePressed(e: MouseEvent) {
                maybeShowContextMenu(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                maybeShowContextMenu(e)
            }
        })
    }

    private fun maybeShowContextMenu(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val row = table.rowAtPoint(e.point)
        if (row >= 0 && !table.isRowSelected(row)) {
            table.setRowSelectionInterval(row, row)
        }
        createContextMenu().show(e.component, e.x, e.y)
    }

    private fun createContextMenu(): JPopupMenu {
        val menu = JPopupMenu()
        val selected = getSelectedRowData()

        val addAction = JMenuItem(HelixBundle.message("settings.keymap.menu.add"))
        addAction.addActionListener { showAddBindingDialog() }
        menu.add(addAction)

        val editAction = JMenuItem(HelixBundle.message("settings.keymap.menu.edit"))
        editAction.isEnabled = selected != null
        editAction.addActionListener { showEditBindingDialog() }
        menu.add(editAction)

        val resetAction = JMenuItem(HelixBundle.message("settings.keymap.menu.reset"))
        resetAction.isEnabled = selected != null && selected.source != BindingSource.DEFAULT
        resetAction.addActionListener { resetSelectedBinding() }
        menu.add(resetAction)

        val disableAction = JMenuItem(HelixBundle.message("settings.keymap.menu.disable"))
        disableAction.isEnabled = selected != null && selected.source != BindingSource.NO_OP
        disableAction.addActionListener { disableSelectedBinding() }
        menu.add(disableAction)

        menu.addSeparator()

        val copyCmd = JMenuItem(HelixBundle.message("settings.keymap.menu.copyCommand"))
        copyCmd.isEnabled = selected != null
        copyCmd.addActionListener {
            val cmd = selected?.command
            if (!cmd.isNullOrEmpty()) {
                CopyPasteManager.getInstance().setContents(StringSelection(cmd))
                statusLabel.text = HelixBundle.message("settings.keymap.status.copiedAction", cmd)
            }
        }
        menu.add(copyCmd)

        val copySeq = JMenuItem(HelixBundle.message("settings.keymap.menu.copyShortcut"))
        copySeq.isEnabled = selected != null
        copySeq.addActionListener {
            val seq = selected?.displayShortcut
            if (!seq.isNullOrEmpty()) {
                CopyPasteManager.getInstance().setContents(StringSelection(seq))
                statusLabel.text = HelixBundle.message("settings.keymap.status.copiedAction", seq)
            }
        }
        menu.add(copySeq)

        return menu
    }

    fun getSelectedRowData(): HelixKeybindingRow? {
        val selectedViewRow = table.selectedRow
        if (selectedViewRow < 0) return null
        val modelRow = table.convertRowIndexToModel(selectedViewRow)
        return tableModel.getRow(modelRow)
    }

    fun updateActionButtonsState() {
        val selected = getSelectedRowData()
        editBindingBtn.isEnabled = selected != null
        resetBindingBtn.isEnabled = selected != null && selected.source != BindingSource.DEFAULT
        disableBindingBtn.isEnabled = selected != null && selected.source != BindingSource.NO_OP
    }

    fun showAddBindingDialog() {
        val currentMode = modeComboBox.selectedItem as? HelixMode ?: HelixMode.NORMAL
        val dialog = HelixKeybindingEditDialog(panel, initialMode = currentMode)
        if (dialog.showAndGet()) {
            val targetFile = HelixConfigLoader.getTargetConfigFile()
            val saved = HelixConfigModifier.setBinding(
                targetFile,
                dialog.resultMode,
                dialog.resultShortcut,
                dialog.resultAction,
            )
            if (saved) {
                applyModeSelection(dialog.resultMode)
                selectRowBySequence(dialog.resultShortcut)
                statusLabel.text = HelixBundle.message(
                    "dialog.editKeybinding.status.saved",
                    dialog.resultShortcut,
                    dialog.resultMode.name.lowercase(),
                )
            } else {
                statusLabel.text = HelixBundle.message(
                    "dialog.editKeybinding.status.failed",
                    dialog.resultShortcut,
                    dialog.resultMode.name.lowercase(),
                )
                statusLabel.foreground = JBColor.RED
            }
        }
    }

    fun showEditBindingDialog(row: HelixKeybindingRow? = getSelectedRowData()) {
        if (row == null) return
        val currentMode = modeComboBox.selectedItem as? HelixMode ?: HelixMode.NORMAL
        val oldSeq = row.rawSequence.ifEmpty { row.displayShortcut }
        val dialog = HelixKeybindingEditDialog(
            panel,
            initialMode = currentMode,
            initialShortcut = oldSeq,
            initialAction = row.command,
        )
        if (dialog.showAndGet()) {
            val targetFile = HelixConfigLoader.getTargetConfigFile()
            if (row.source != BindingSource.DEFAULT && oldSeq != dialog.resultShortcut) {
                HelixConfigModifier.removeBinding(targetFile, currentMode, oldSeq)
            }
            val saved = HelixConfigModifier.setBinding(
                targetFile,
                dialog.resultMode,
                dialog.resultShortcut,
                dialog.resultAction,
            )
            if (saved) {
                applyModeSelection(dialog.resultMode)
                selectRowBySequence(dialog.resultShortcut)
                statusLabel.text = HelixBundle.message(
                    "dialog.editKeybinding.status.saved",
                    dialog.resultShortcut,
                    dialog.resultMode.name.lowercase(),
                )
            } else {
                statusLabel.text = HelixBundle.message(
                    "dialog.editKeybinding.status.failed",
                    dialog.resultShortcut,
                    dialog.resultMode.name.lowercase(),
                )
                statusLabel.foreground = JBColor.RED
            }
        }
    }

    fun resetSelectedBinding(row: HelixKeybindingRow? = getSelectedRowData()) {
        if (row == null) return
        val currentMode = modeComboBox.selectedItem as? HelixMode ?: HelixMode.NORMAL
        val seq = row.rawSequence.ifEmpty { row.displayShortcut }
        val targetFile = HelixConfigLoader.getTargetConfigFile()
        val removed = HelixConfigModifier.removeBinding(targetFile, currentMode, seq)
        if (removed) {
            reloadModeData()
            selectRowBySequence(seq)
            statusLabel.text = HelixBundle.message(
                "dialog.editKeybinding.status.reset",
                seq,
                currentMode.name.lowercase(),
            )
        } else {
            statusLabel.text = HelixBundle.message(
                "dialog.editKeybinding.status.failed",
                seq,
                currentMode.name.lowercase(),
            )
            statusLabel.foreground = JBColor.RED
        }
    }

    fun disableSelectedBinding(row: HelixKeybindingRow? = getSelectedRowData()) {
        if (row == null) return
        val currentMode = modeComboBox.selectedItem as? HelixMode ?: HelixMode.NORMAL
        val seq = row.rawSequence.ifEmpty { row.displayShortcut }
        val targetFile = HelixConfigLoader.getTargetConfigFile()
        val disabled = HelixConfigModifier.setNoOp(targetFile, currentMode, seq)
        if (disabled) {
            reloadModeData()
            selectRowBySequence(seq)
            statusLabel.text = HelixBundle.message(
                "dialog.editKeybinding.status.disabled",
                seq,
                currentMode.name.lowercase(),
            )
        } else {
            statusLabel.text = HelixBundle.message(
                "dialog.editKeybinding.status.failed",
                seq,
                currentMode.name.lowercase(),
            )
            statusLabel.foreground = JBColor.RED
        }
    }

    private fun applyModeSelection(mode: HelixMode) {
        if (modeComboBox.selectedItem != mode) {
            modeComboBox.selectedItem = mode
        } else {
            reloadModeData()
        }
    }

    fun selectRowBySequence(sequence: String) {
        val count = table.rowCount
        for (i in 0 until count) {
            val modelRow = table.convertRowIndexToModel(i)
            val row = tableModel.getRow(modelRow)
            if (row != null && (row.rawSequence == sequence || row.displayShortcut == sequence)) {
                table.setRowSelectionInterval(i, i)
                table.scrollRectToVisible(table.getCellRect(i, 0, true))
                updateActionButtonsState()
                break
            }
        }
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
        val shadowedCount = allRowsForCurrentMode.count { it.isShadowed }
        val baseMsg = HelixBundle.message("settings.keymap.status.count", visibleCount, customCount)
        if (shadowedCount > 0) {
            val warning = HelixBundle.message("settings.keymap.conflict.warning", shadowedCount)
            statusLabel.text = "$baseMsg  •  $warning"
            statusLabel.foreground = JBColor.RED
        } else {
            statusLabel.text = baseMsg
            statusLabel.foreground = UIUtil.getContextHelpForeground()
        }
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

    private inner class SourceCellRenderer : DefaultTableCellRenderer() {
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
            val modelRow = table?.convertRowIndexToModel(row) ?: row
            val rowData = tableModel.getRow(modelRow)

            if (rowData != null && rowData.isShadowed) {
                text = "[${source.displayName}] ⚠"
                toolTipText = rowData.conflictReason
                if (!isSelected) {
                    foreground = JBColor.RED
                }
            } else {
                text = "[${source.displayName}]"
                toolTipText = null
                if (!isSelected) {
                    applySourceForeground(source)
                }
            }
            return comp
        }

        private fun applySourceForeground(source: BindingSource) {
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
    }
}
