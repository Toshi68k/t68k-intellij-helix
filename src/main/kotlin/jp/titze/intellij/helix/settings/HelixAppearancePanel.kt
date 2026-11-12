// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * Settings panel for visual themes, status bar mode pills, cursor tinting, gutter line numbers, and Which-Key popups.
 */
class HelixAppearancePanel {

    private var syncThemeRadio: JBRadioButton? = null
    private var darkThemeRadio: JBRadioButton? = null
    private var lightThemeRadio: JBRadioButton? = null
    private var colorModesCheckBox: JBCheckBox? = null
    private var cursorTintCheckBox: JBCheckBox? = null

    private var lineNumberModeComboBox: ComboBox<HelixLineNumberMode>? = null

    private var enableWhichKeyCheckBox: JBCheckBox? = null
    private var whichKeyHelixCommandRadio: JBRadioButton? = null
    private var whichKeyIntelliJActionRadio: JBRadioButton? = null
    private var whichKeyColumnLayoutComboBox: ComboBox<WhichKeyColumnLayout>? = null
    private var whichKeyMaxRowsComboBox: ComboBox<Int>? = null

    val panel: JComponent = createMainPanel()

    private fun createMainPanel(): JComponent {
        val contentBox = JPanel()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)
        contentBox.border = JBUI.Borders.empty(16)

        contentBox.add(createThemeGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createLineNumbersGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createWhichKeyGroup())

        val wrapper = JPanel(BorderLayout())
        wrapper.add(contentBox, BorderLayout.NORTH)
        return wrapper
    }

    private fun createThemeGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.theme.title")), BorderLayout.NORTH)

        val radioGroup = ButtonGroup()
        val sync = JBRadioButton(HelixBundle.message("settings.theme.mode.sync"))
        val dark = JBRadioButton(HelixBundle.message("settings.theme.mode.dark"))
        val light = JBRadioButton(HelixBundle.message("settings.theme.mode.light"))
        syncThemeRadio = sync
        darkThemeRadio = dark
        lightThemeRadio = light

        radioGroup.add(sync)
        radioGroup.add(dark)
        radioGroup.add(light)

        val optionsPanel = JPanel(GridLayout(3, 1, 0, 6))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(sync)
        optionsPanel.add(dark)
        optionsPanel.add(light)

        val colorModesCb = JBCheckBox(HelixBundle.message("settings.theme.colorModes"))
        val cursorTintCb = JBCheckBox(HelixBundle.message("settings.theme.cursorTint"))
        colorModesCheckBox = colorModesCb
        cursorTintCheckBox = cursorTintCb

        val togglesPanel = JPanel(GridLayout(2, 1, 0, 4))
        togglesPanel.border = JBUI.Borders.empty(6, 12, 0, 0)
        togglesPanel.add(colorModesCb)
        togglesPanel.add(cursorTintCb)

        val helpLabel = JBLabel(HelixBundle.message("settings.theme.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val controlsBox = JPanel()
        controlsBox.layout = BoxLayout(controlsBox, BoxLayout.Y_AXIS)
        controlsBox.add(optionsPanel)
        controlsBox.add(togglesPanel)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(controlsBox, BorderLayout.NORTH)
        inner.add(helpLabel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
    }

    private fun createLineNumbersGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.lineNumbers.title")), BorderLayout.NORTH)

        val combo = ComboBox(HelixLineNumberMode.entries.toTypedArray())
        combo.renderer = object : SimpleListCellRenderer<HelixLineNumberMode>() {
            override fun customize(
                list: JList<out HelixLineNumberMode>,
                value: HelixLineNumberMode?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = value?.displayName.orEmpty()
            }
        }
        lineNumberModeComboBox = combo

        val comboPanel = JPanel(BorderLayout())
        comboPanel.border = JBUI.Borders.emptyLeft(12)
        comboPanel.add(combo, BorderLayout.WEST)

        val helpLabel = JBLabel(HelixBundle.message("settings.lineNumbers.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(comboPanel, BorderLayout.NORTH)
        inner.add(helpLabel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
    }

    private fun createWhichKeyGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.whichKey.title")), BorderLayout.NORTH)

        val checkBox = JBCheckBox(HelixBundle.message("settings.whichKey.enable"))
        enableWhichKeyCheckBox = checkBox

        val modeRadioGroup = ButtonGroup()
        val helixRadio = JBRadioButton(HelixBundle.message("settings.whichKey.hint.helix"))
        val ideaRadio = JBRadioButton(HelixBundle.message("settings.whichKey.hint.idea"))
        whichKeyHelixCommandRadio = helixRadio
        whichKeyIntelliJActionRadio = ideaRadio
        modeRadioGroup.add(helixRadio)
        modeRadioGroup.add(ideaRadio)

        val modesPanel = JPanel(GridLayout(2, 1, 0, 4))
        modesPanel.border = JBUI.Borders.empty(4, 24, 4, 0)
        modesPanel.add(helixRadio)
        modesPanel.add(ideaRadio)

        val colCombo = ComboBox(WhichKeyColumnLayout.entries.toTypedArray())
        colCombo.renderer = object : SimpleListCellRenderer<WhichKeyColumnLayout>() {
            override fun customize(
                list: JList<out WhichKeyColumnLayout>,
                value: WhichKeyColumnLayout?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = value?.displayName.orEmpty()
            }
        }
        whichKeyColumnLayoutComboBox = colCombo

        val maxRowsOptions = arrayOf(0, 6, 8, 10, 12, 15, 20)
        val rowsCombo = ComboBox(maxRowsOptions)
        rowsCombo.renderer = object : SimpleListCellRenderer<Int>() {
            override fun customize(
                list: JList<out Int>,
                value: Int?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = when (value) {
                    0, null -> HelixBundle.message("settings.whichKey.maxRows.auto")
                    else -> HelixBundle.message("settings.whichKey.maxRows.count", value)
                }
            }
        }
        whichKeyMaxRowsComboBox = rowsCombo

        val dropdownsPanel = JPanel(java.awt.GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 24, 4, 0)
            val gbc = java.awt.GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = java.awt.GridBagConstraints.WEST
                insets = JBUI.insets(2, 0, 4, 12)
            }
            add(JBLabel(HelixBundle.message("settings.whichKey.cols.label")), gbc)
            gbc.gridx = 1
            gbc.fill = java.awt.GridBagConstraints.HORIZONTAL
            gbc.weightx = 1.0
            add(colCombo, gbc)

            gbc.gridx = 0
            gbc.gridy = 1
            gbc.fill = java.awt.GridBagConstraints.NONE
            gbc.weightx = 0.0
            add(JBLabel(HelixBundle.message("settings.whichKey.maxRows.label")), gbc)
            gbc.gridx = 1
            gbc.fill = java.awt.GridBagConstraints.HORIZONTAL
            gbc.weightx = 1.0
            add(rowsCombo, gbc)
        }

        val helpLabel = JBLabel(HelixBundle.message("settings.whichKey.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val optionsPanel = JPanel(BorderLayout(0, 4))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(checkBox, BorderLayout.NORTH)

        val settingsBox = JPanel()
        settingsBox.layout = BoxLayout(settingsBox, BoxLayout.Y_AXIS)
        settingsBox.add(modesPanel)
        settingsBox.add(dropdownsPanel)
        optionsPanel.add(settingsBox, BorderLayout.CENTER)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(optionsPanel, BorderLayout.NORTH)
        inner.add(helpLabel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
    }

    private fun getSelectedColorTheme(): HelixColorTheme = when {
        darkThemeRadio?.isSelected == true -> HelixColorTheme.DARK
        lightThemeRadio?.isSelected == true -> HelixColorTheme.LIGHT
        else -> HelixColorTheme.SYNC
    }

    private fun getSelectedWhichKeyHintMode(): WhichKeyHintMode = if (whichKeyIntelliJActionRadio?.isSelected == true) {
        WhichKeyHintMode.INTELLIJ_ACTION
    } else {
        WhichKeyHintMode.HELIX_COMMAND
    }

    private fun getSelectedWhichKeyColumnLayout(): WhichKeyColumnLayout =
        (whichKeyColumnLayoutComboBox?.selectedItem as? WhichKeyColumnLayout) ?: WhichKeyColumnLayout.AUTO

    private fun getSelectedWhichKeyMaxRows(): Int = (whichKeyMaxRowsComboBox?.selectedItem as? Int) ?: 0

    fun isModified(settings: HelixSettings): Boolean {
        val themeModified = getSelectedColorTheme() != settings.colorTheme ||
            colorModesCheckBox?.isSelected != settings.colorModes ||
            cursorTintCheckBox?.isSelected != settings.cursorTint
        val lineNumModified = (lineNumberModeComboBox?.selectedItem as? HelixLineNumberMode) != settings.lineNumberMode
        val whichKeyModified = enableWhichKeyCheckBox?.isSelected != settings.enableWhichKeyPopups ||
            getSelectedWhichKeyHintMode() != settings.whichKeyHintMode ||
            getSelectedWhichKeyColumnLayout() != settings.whichKeyColumnLayout ||
            getSelectedWhichKeyMaxRows() != settings.whichKeyMaxRows
        return themeModified || lineNumModified || whichKeyModified
    }

    fun apply(settings: HelixSettings) {
        settings.colorTheme = getSelectedColorTheme()
        colorModesCheckBox?.let { settings.colorModes = it.isSelected }
        cursorTintCheckBox?.let { settings.cursorTint = it.isSelected }
        (lineNumberModeComboBox?.selectedItem as? HelixLineNumberMode)?.let { settings.lineNumberMode = it }
        enableWhichKeyCheckBox?.let { settings.enableWhichKeyPopups = it.isSelected }
        settings.whichKeyHintMode = getSelectedWhichKeyHintMode()
        settings.whichKeyColumnLayout = getSelectedWhichKeyColumnLayout()
        settings.whichKeyMaxRows = getSelectedWhichKeyMaxRows()

        HelixEditorEligibility.refreshAllEditors()
    }

    fun reset(settings: HelixSettings) {
        syncThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.SYNC)
        darkThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.DARK)
        lightThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.LIGHT)
        colorModesCheckBox?.isSelected = settings.colorModes
        cursorTintCheckBox?.isSelected = settings.cursorTint

        lineNumberModeComboBox?.selectedItem = settings.lineNumberMode

        enableWhichKeyCheckBox?.isSelected = settings.enableWhichKeyPopups
        whichKeyHelixCommandRadio?.isSelected = (settings.whichKeyHintMode == WhichKeyHintMode.HELIX_COMMAND)
        whichKeyIntelliJActionRadio?.isSelected = (settings.whichKeyHintMode == WhichKeyHintMode.INTELLIJ_ACTION)
        whichKeyColumnLayoutComboBox?.selectedItem = settings.whichKeyColumnLayout
        whichKeyMaxRowsComboBox?.selectedItem = settings.whichKeyMaxRows
    }

    fun disposeUIResources() {
        syncThemeRadio = null
        darkThemeRadio = null
        lightThemeRadio = null
        colorModesCheckBox = null
        cursorTintCheckBox = null
        lineNumberModeComboBox = null
        enableWhichKeyCheckBox = null
        whichKeyHelixCommandRadio = null
        whichKeyIntelliJActionRadio = null
        whichKeyColumnLayoutComboBox = null
        whichKeyMaxRowsComboBox = null
    }
}
