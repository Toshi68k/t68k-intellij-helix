// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.keymap.HelixActionExtractor
import jp.titze.intellij.helix.keymap.HelixIntelliJAction
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel

/**
 * Modal dialog for searching and discovering IntelliJ Action IDs to bind in Helix configuration.
 */
class HelixActionPickerDialog(parent: JComponent) : DialogWrapper(parent, true) {

    private val allActions: List<HelixIntelliJAction> by lazy {
        HelixActionExtractor.extractActions()
    }

    private var filteredActions: List<HelixIntelliJAction> = emptyList()

    private val searchField = SearchTextField()
    private val tableModel = ActionTableModel()
    private val table = JBTable(tableModel)
    private val statusLabel = JBLabel()
    private val previewCommandField = JTextField()
    private val copyButton = JButton(HelixBundle.message("dialog.actionPicker.btn.copy"))

    var selectedAction: HelixIntelliJAction? = null
        private set

    val selectedTomlCommand: String?
        get() = selectedAction?.tomlCommand

    init {
        title = HelixBundle.message("dialog.actionPicker.title")
        init()
        applyFilter("")
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 10))
        root.preferredSize = Dimension(JBUI.scale(720), JBUI.scale(420))
        root.border = JBUI.Borders.empty(10)

        root.add(createTopSearchPanel(), BorderLayout.NORTH)
        root.add(createTablePanel(), BorderLayout.CENTER)
        root.add(createBottomPreviewPanel(), BorderLayout.SOUTH)

        setupListeners()
        return root
    }

    private fun createTopSearchPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 0))
        searchField.textEditor.emptyText.text = HelixBundle.message("dialog.actionPicker.search.placeholder")
        panel.add(searchField, BorderLayout.CENTER)
        panel.add(statusLabel, BorderLayout.EAST)
        return panel
    }

    private fun createTablePanel(): JScrollPane {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.rowHeight = JBUI.scale(24)
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 0)

        val colModel = table.columnModel
        colModel.getColumn(0).preferredWidth = JBUI.scale(160)
        colModel.getColumn(1).preferredWidth = JBUI.scale(180)
        colModel.getColumn(2).preferredWidth = JBUI.scale(100)
        colModel.getColumn(3).preferredWidth = JBUI.scale(240)

        return JScrollPane(table)
    }

    private fun createBottomPreviewPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 0))
        val label = JBLabel(HelixBundle.message("dialog.actionPicker.preview.label"))

        previewCommandField.isEditable = false
        previewCommandField.font = JBUI.Fonts.label()

        copyButton.isFocusable = false
        copyButton.addActionListener {
            val cmd = previewCommandField.text
            if (cmd.isNotBlank()) {
                CopyPasteManager.getInstance().setContents(StringSelection(cmd))
                copyButton.text = HelixBundle.message("dialog.actionPicker.copied")
            }
        }

        val leftWrapper = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        leftWrapper.add(label)

        panel.add(leftWrapper, BorderLayout.WEST)
        panel.add(previewCommandField, BorderLayout.CENTER)
        panel.add(copyButton, BorderLayout.EAST)
        return panel
    }

    private fun setupListeners() {
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                applyFilter(searchField.text)
            }
        })

        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                updateSelectedAction()
            }
        }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && table.selectedRow >= 0) {
                    updateSelectedAction()
                    doOKAction()
                }
            }
        })
    }

    private fun applyFilter(query: String) {
        filteredActions = HelixActionExtractor.filterActions(allActions, query)
        tableModel.fireTableDataChanged()
        statusLabel.text = HelixBundle.message(
            "dialog.actionPicker.status.count",
            filteredActions.size,
            allActions.size,
        )
        if (filteredActions.isNotEmpty()) {
            table.setRowSelectionInterval(0, 0)
        } else {
            selectedAction = null
            previewCommandField.text = ""
            copyButton.isEnabled = false
        }
        updateSelectedAction()
    }

    private fun updateSelectedAction() {
        val row = table.selectedRow
        if (row in filteredActions.indices) {
            val item = filteredActions[row]
            selectedAction = item
            previewCommandField.text = item.tomlCommand
            copyButton.isEnabled = true
            copyButton.text = HelixBundle.message("dialog.actionPicker.btn.copy")
        } else {
            selectedAction = null
            previewCommandField.text = ""
            copyButton.isEnabled = false
        }
    }

    private inner class ActionTableModel : AbstractTableModel() {
        private val columns = arrayOf(
            HelixBundle.message("dialog.actionPicker.col.id"),
            HelixBundle.message("dialog.actionPicker.col.name"),
            HelixBundle.message("dialog.actionPicker.col.shortcut"),
            HelixBundle.message("dialog.actionPicker.col.description"),
        )

        override fun getRowCount(): Int = filteredActions.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(col: Int): String = columns[col]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val item = filteredActions.getOrNull(rowIndex) ?: return ""
            return when (columnIndex) {
                0 -> item.actionId
                1 -> item.displayName
                2 -> item.shortcutText
                3 -> item.description
                else -> ""
            }
        }
    }
}
