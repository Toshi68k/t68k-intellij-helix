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
    private var whichKey3ColsRadio: JBRadioButton? = null
    private var whichKey2ColsRadio: JBRadioButton? = null

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

        val colRadioGroup = ButtonGroup()
        val cols3Radio = JBRadioButton(HelixBundle.message("settings.whichKey.cols.3"))
        val cols2Radio = JBRadioButton(HelixBundle.message("settings.whichKey.cols.2"))
        whichKey3ColsRadio = cols3Radio
        whichKey2ColsRadio = cols2Radio
        colRadioGroup.add(cols3Radio)
        colRadioGroup.add(cols2Radio)

        val columnsPanel = JPanel(GridLayout(2, 1, 0, 4))
        columnsPanel.border = JBUI.Borders.empty(4, 24, 4, 0)
        columnsPanel.add(cols3Radio)
        columnsPanel.add(cols2Radio)

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
        settingsBox.add(Box.createVerticalStrut(JBUI.scale(6)))
        settingsBox.add(columnsPanel)
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

    private fun getSelectedWhichKeyColumnLayout(): WhichKeyColumnLayout = if (whichKey2ColsRadio?.isSelected == true) {
        WhichKeyColumnLayout.TWO_COLUMNS
    } else {
        WhichKeyColumnLayout.THREE_COLUMNS
    }

    fun isModified(settings: HelixSettings): Boolean {
        val themeModified = getSelectedColorTheme() != settings.colorTheme ||
            colorModesCheckBox?.isSelected != settings.colorModes ||
            cursorTintCheckBox?.isSelected != settings.cursorTint
        val lineNumModified = (lineNumberModeComboBox?.selectedItem as? HelixLineNumberMode) != settings.lineNumberMode
        val whichKeyModified = enableWhichKeyCheckBox?.isSelected != settings.enableWhichKeyPopups ||
            getSelectedWhichKeyHintMode() != settings.whichKeyHintMode ||
            getSelectedWhichKeyColumnLayout() != settings.whichKeyColumnLayout
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
        whichKey3ColsRadio?.isSelected = (settings.whichKeyColumnLayout == WhichKeyColumnLayout.THREE_COLUMNS)
        whichKey2ColsRadio?.isSelected = (settings.whichKeyColumnLayout == WhichKeyColumnLayout.TWO_COLUMNS)
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
        whichKey3ColsRadio = null
        whichKey2ColsRadio = null
    }
}
