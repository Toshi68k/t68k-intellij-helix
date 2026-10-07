package jp.titze.intellij.helix.settings

import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.keymap.HelixKeyContext
import jp.titze.intellij.helix.keymap.HelixKeyOwner
import jp.titze.intellij.helix.keymap.HelixShortcutPolicy
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class HelixCtrlKeyPanel {

    private val currentOverrides = mutableMapOf<String, String>()

    private val tableModel = object : AbstractTableModel() {
        private val columns = listOf(
            HelixBundle.message("settings.ctrlKey.col.key"),
            HelixBundle.message("settings.ctrlKey.col.normal"),
            HelixBundle.message("settings.ctrlKey.col.insert"),
        )

        override fun getRowCount(): Int = HelixShortcutPolicy.KEYS.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val key = HelixShortcutPolicy.KEYS[rowIndex]
            return when (columnIndex) {
                0 -> "Ctrl+${key.letter}"

                1 -> if (key.supports(HelixKeyContext.NORMAL)) {
                    HelixShortcutPolicy.owner(key, HelixKeyContext.NORMAL, currentOverrides).name
                } else {
                    "-"
                }

                2 -> if (key.supports(HelixKeyContext.INSERT)) {
                    HelixShortcutPolicy.owner(key, HelixKeyContext.INSERT, currentOverrides).name
                } else {
                    "-"
                }

                else -> ""
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            val key = HelixShortcutPolicy.KEYS[rowIndex]
            return when (columnIndex) {
                1 -> key.supports(HelixKeyContext.NORMAL)
                2 -> key.supports(HelixKeyContext.INSERT)
                else -> false
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val key = HelixShortcutPolicy.KEYS[rowIndex]
            val ownerStr = aValue?.toString() ?: return
            val context = if (columnIndex == 1) HelixKeyContext.NORMAL else HelixKeyContext.INSERT
            val id = HelixShortcutPolicy.overrideKey(context, key.letter)
            currentOverrides[id] = ownerStr
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }

    private val table = JBTable(tableModel)

    val panel: JPanel = JPanel(BorderLayout(0, 8))

    init {
        val titleLabel = JBLabel(HelixBundle.message("settings.ctrlKey.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        panel.add(titleLabel, BorderLayout.NORTH)

        setupTableRenderersAndEditors()

        val tableScroll = com.intellij.ui.components.JBScrollPane(table)
        tableScroll.preferredSize = JBUI.size(500, 220)

        val resetButton = JButton(HelixBundle.message("settings.ctrlKey.resetDefaults"))
        resetButton.addActionListener {
            currentOverrides.clear()
            tableModel.fireTableDataChanged()
        }

        val buttonPanel = JPanel(BorderLayout())
        buttonPanel.add(resetButton, BorderLayout.EAST)

        val helpLabel = JBLabel(HelixBundle.message("settings.ctrlKey.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()

        val bottomBox = JPanel(BorderLayout(0, 4))
        bottomBox.add(helpLabel, BorderLayout.CENTER)
        bottomBox.add(buttonPanel, BorderLayout.SOUTH)

        val content = JPanel(BorderLayout(0, 6))
        content.border = JBUI.Borders.emptyLeft(12)
        content.add(tableScroll, BorderLayout.CENTER)
        content.add(bottomBox, BorderLayout.SOUTH)

        panel.add(content, BorderLayout.CENTER)
    }

    private fun setupTableRenderersAndEditors() {
        table.rowHeight = JBUI.scale(24)
        val comboBox = JComboBox(arrayOf(HelixKeyOwner.HELIX.name, HelixKeyOwner.IDE.name))
        table.columnModel.getColumn(1).cellEditor = DefaultCellEditor(comboBox)
        table.columnModel.getColumn(2).cellEditor = DefaultCellEditor(comboBox)

        val renderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                t: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                col: Int,
            ): Component {
                val c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col)
                val key = HelixShortcutPolicy.KEYS.getOrNull(row)
                if (key != null) {
                    val tooltip = when (col) {
                        1 -> key.normalBundleKey?.let { HelixBundle.message(it) }
                        2 -> key.insertBundleKey?.let { HelixBundle.message(it) }
                        else -> null
                    }
                    toolTipText = tooltip
                }
                return c
            }
        }
        table.columnModel.getColumn(0).cellRenderer = renderer
        table.columnModel.getColumn(1).cellRenderer = renderer
        table.columnModel.getColumn(2).cellRenderer = renderer
    }

    fun isModified(settings: HelixSettings): Boolean {
        val currentNormalized = HelixShortcutPolicy.normalize(currentOverrides)
        val savedNormalized = HelixShortcutPolicy.normalize(settings.ctrlKeyOverrides)
        return currentNormalized != savedNormalized
    }

    fun apply(settings: HelixSettings) {
        settings.ctrlKeyOverrides = HelixShortcutPolicy.normalize(currentOverrides)
    }

    fun reset(settings: HelixSettings) {
        currentOverrides.clear()
        currentOverrides.putAll(settings.ctrlKeyOverrides)
        tableModel.fireTableDataChanged()
    }
}
