// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.keymap.HelixCommandRegistry
import jp.titze.intellij.helix.state.HelixMode
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Modal dialog for creating or modifying a Helix keybinding.
 */
class HelixKeybindingEditDialog(
    private val parentComponent: JComponent,
    val initialMode: HelixMode = HelixMode.NORMAL,
    val initialShortcut: String = "",
    val initialAction: String = "",
) : DialogWrapper(parentComponent, true) {

    enum class ActionType(val bundleKey: String) {
        INTELLIJ("dialog.editKeybinding.type.intellij"),
        HELIX("dialog.editKeybinding.type.helix"),
        NO_OP("dialog.editKeybinding.type.noop"),
        RAW("dialog.editKeybinding.type.raw"),
        ;

        override fun toString(): String = HelixBundle.message(bundleKey)
    }

    private val modeCombo = JComboBox(arrayOf(HelixMode.NORMAL, HelixMode.SELECT, HelixMode.INSERT))
    private val shortcutField = JBTextField(initialShortcut)
    private val recordButton = JButton(HelixBundle.message("dialog.editKeybinding.record"))

    private val typeCombo = JComboBox(ActionType.values())
    private val intellijActionField = JBTextField()
    private val browseActionsButton = JButton(HelixBundle.message("dialog.editKeybinding.browse"))
    private val helixCommandCombo = JComboBox<String>()
    private val rawActionField = JBTextField()

    private val actionCardLayout = CardLayout()
    private val actionCardsPanel = JPanel(actionCardLayout)

    val resultMode: HelixMode get() = modeCombo.selectedItem as? HelixMode ?: HelixMode.NORMAL
    val resultShortcut: String
        get() {
            val raw = shortcutField.text.trim()
            return raw.replace(Regex("\\s+\\+\\s+"), " ")
        }

    val resultAction: String
        get() = when (typeCombo.selectedItem as? ActionType ?: ActionType.INTELLIJ) {
            ActionType.INTELLIJ -> {
                val raw = intellijActionField.text.trim()
                if (raw.startsWith(":action ")) raw else ":action $raw"
            }

            ActionType.HELIX -> helixCommandCombo.selectedItem as? String ?: ""

            ActionType.NO_OP -> "no_op"

            ActionType.RAW -> rawActionField.text.trim()
        }

    init {
        title = if (initialShortcut.isEmpty()) {
            HelixBundle.message("dialog.editKeybinding.add.title")
        } else {
            HelixBundle.message("dialog.editKeybinding.edit.title")
        }
        setupInitialValues()
        init()
    }

    private fun setupInitialValues() {
        modeCombo.selectedItem = initialMode

        val commands = HelixCommandRegistry.getAll().map { it.id }.distinct().sorted()
        for (cmd in commands) {
            helixCommandCombo.addItem(cmd)
        }

        setupTypeAndActionFields(initialAction)
    }

    private fun setupTypeAndActionFields(action: String) {
        when {
            action == "no_op" -> {
                typeCombo.selectedItem = ActionType.NO_OP
            }

            action.startsWith(":action ") -> {
                typeCombo.selectedItem = ActionType.INTELLIJ
                intellijActionField.text = action.removePrefix(":action ").trim()
            }

            HelixCommandRegistry.get(action) != null -> {
                typeCombo.selectedItem = ActionType.HELIX
                helixCommandCombo.selectedItem = action
            }

            action.isNotEmpty() -> {
                typeCombo.selectedItem = ActionType.RAW
                rawActionField.text = action
            }

            else -> {
                typeCombo.selectedItem = ActionType.INTELLIJ
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, JBUI.scale(10)))
        root.preferredSize = Dimension(JBUI.scale(460), JBUI.scale(230))
        root.border = JBUI.Borders.empty(12)

        val formPanel = JPanel(GridLayout(0, 1, 0, JBUI.scale(8)))

        formPanel.add(createModeRow())
        formPanel.add(createShortcutRow())
        formPanel.add(createActionTypeRow())
        formPanel.add(createActionCards())

        root.add(formPanel, BorderLayout.CENTER)
        setupEventListeners()
        switchActionCard(typeCombo.selectedItem as? ActionType ?: ActionType.INTELLIJ)

        return root
    }

    private fun createModeRow(): JPanel {
        val panel = JPanel(BorderLayout(JBUI.scale(8), 0))
        panel.add(JBLabel(HelixBundle.message("dialog.editKeybinding.mode")), BorderLayout.WEST)
        panel.add(modeCombo, BorderLayout.CENTER)
        return panel
    }

    private fun createShortcutRow(): JPanel {
        val panel = JPanel(BorderLayout(JBUI.scale(8), 0))
        panel.add(JBLabel(HelixBundle.message("dialog.editKeybinding.shortcut")), BorderLayout.WEST)
        shortcutField.emptyText.text = HelixBundle.message("dialog.editKeybinding.shortcut.placeholder")
        panel.add(shortcutField, BorderLayout.CENTER)

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        rightPanel.add(recordButton)
        panel.add(rightPanel, BorderLayout.EAST)
        return panel
    }

    private fun createActionTypeRow(): JPanel {
        val panel = JPanel(BorderLayout(JBUI.scale(8), 0))
        panel.add(JBLabel(HelixBundle.message("dialog.editKeybinding.actionType")), BorderLayout.WEST)
        panel.add(typeCombo, BorderLayout.CENTER)
        return panel
    }

    private fun createActionCards(): JPanel {
        val intellijPanel = JPanel(BorderLayout(JBUI.scale(6), 0))
        intellijActionField.emptyText.text = "e.g. GotoFile, SaveAll"
        intellijPanel.add(intellijActionField, BorderLayout.CENTER)
        intellijPanel.add(browseActionsButton, BorderLayout.EAST)

        val helixPanel = JPanel(BorderLayout())
        helixPanel.add(helixCommandCombo, BorderLayout.CENTER)

        val noOpPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        noOpPanel.add(JBLabel(HelixBundle.message("dialog.editKeybinding.type.noop.help")))

        val rawPanel = JPanel(BorderLayout())
        rawActionField.emptyText.text = "e.g. [\"half_page_down\", \"align_view_center\"]"
        rawPanel.add(rawActionField, BorderLayout.CENTER)

        actionCardsPanel.add(intellijPanel, ActionType.INTELLIJ.name)
        actionCardsPanel.add(helixPanel, ActionType.HELIX.name)
        actionCardsPanel.add(noOpPanel, ActionType.NO_OP.name)
        actionCardsPanel.add(rawPanel, ActionType.RAW.name)

        return actionCardsPanel
    }

    private fun setupEventListeners() {
        recordButton.addActionListener {
            val dialog = HelixShortcutCaptureDialog(parentComponent)
            if (dialog.showAndGet()) {
                val recorded = dialog.rawSequence.ifEmpty { dialog.recordedShortcut }
                if (recorded.isNotEmpty()) {
                    shortcutField.text = recorded
                }
            }
        }

        browseActionsButton.addActionListener {
            val dialog = HelixActionPickerDialog(parentComponent)
            if (dialog.showAndGet()) {
                val action = dialog.selectedAction
                if (action != null) {
                    intellijActionField.text = action.actionId
                }
            }
        }

        typeCombo.addActionListener {
            val selected = typeCombo.selectedItem as? ActionType ?: ActionType.INTELLIJ
            switchActionCard(selected)
        }
    }

    private fun switchActionCard(type: ActionType) {
        actionCardLayout.show(actionCardsPanel, type.name)
    }

    public override fun doValidate(): ValidationInfo? {
        if (shortcutField.text.trim().isEmpty()) {
            return ValidationInfo(HelixBundle.message("dialog.editKeybinding.error.emptyShortcut"), shortcutField)
        }
        val type = typeCombo.selectedItem as? ActionType ?: ActionType.INTELLIJ
        if (type == ActionType.INTELLIJ && intellijActionField.text.trim().isEmpty()) {
            return ValidationInfo(HelixBundle.message("dialog.editKeybinding.error.emptyAction"), intellijActionField)
        }
        if (type == ActionType.RAW && rawActionField.text.trim().isEmpty()) {
            return ValidationInfo(HelixBundle.message("dialog.editKeybinding.error.emptyAction"), rawActionField)
        }
        return null
    }
}
