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
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
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
    private var darkNeutralThemeRadio: JBRadioButton? = null
    private var darkThemeRadio: JBRadioButton? = null
    private var lightThemeRadio: JBRadioButton? = null
    private var colorModesCheckBox: JBCheckBox? = null
    private var cursorTintCheckBox: JBCheckBox? = null
    private var colorModesNotice: HelixOverrideNoticePanel? = null
    private var cursorTintNotice: HelixOverrideNoticePanel? = null

    private var cursorShapeNormalComboBox: ComboBox<HelixCursorShape>? = null
    private var cursorShapeInsertComboBox: ComboBox<HelixCursorShape>? = null
    private var cursorShapeSelectComboBox: ComboBox<HelixCursorShape>? = null
    private var cursorShapeNormalNotice: HelixOverrideNoticePanel? = null
    private var cursorShapeInsertNotice: HelixOverrideNoticePanel? = null
    private var cursorShapeSelectNotice: HelixOverrideNoticePanel? = null

    internal var lineNumberModeComboBox: ComboBox<HelixLineNumberMode>? = null
    internal var lineNumberNotice: HelixOverrideNoticePanel? = null

    private var enableWhichKeyCheckBox: JBCheckBox? = null
    private var whichKeyHelixCommandRadio: JBRadioButton? = null
    private var whichKeyIntelliJActionRadio: JBRadioButton? = null
    private var whichKeyColumnLayoutComboBox: ComboBox<WhichKeyColumnLayout>? = null
    private var whichKeyMaxRowsComboBox: ComboBox<Int>? = null

    private val overridesChangeListener: () -> Unit = {
        reset()
    }

    init {
        HelixEffectiveSettings.addChangeListener(overridesChangeListener)
    }

    val panel: JComponent = createMainPanel()

    private fun createMainPanel(): JComponent {
        val contentBox = JPanel()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)
        contentBox.border = JBUI.Borders.empty(16)

        contentBox.add(createThemeGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createCursorShapeGroup())
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
        val darkNeutral = JBRadioButton(HelixBundle.message("settings.theme.mode.darkNeutral"))
        val dark = JBRadioButton(HelixBundle.message("settings.theme.mode.dark"))
        val light = JBRadioButton(HelixBundle.message("settings.theme.mode.light"))
        syncThemeRadio = sync
        darkNeutralThemeRadio = darkNeutral
        darkThemeRadio = dark
        lightThemeRadio = light

        radioGroup.add(sync)
        radioGroup.add(darkNeutral)
        radioGroup.add(dark)
        radioGroup.add(light)

        val colorModesCb = JBCheckBox(HelixBundle.message("settings.theme.colorModes"))
        val cursorTintCb = JBCheckBox(HelixBundle.message("settings.theme.cursorTint"))
        colorModesCheckBox = colorModesCb
        cursorTintCheckBox = cursorTintCb

        val cmNotice = HelixOverrideNoticePanel("color-modes")
        val ctNotice = HelixOverrideNoticePanel("cursor-tint")
        colorModesNotice = cmNotice
        cursorTintNotice = ctNotice

        val controlsPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyLeft(12)
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.NONE
                weightx = 1.0
                insets = JBUI.insets(2, 0)
            }
            add(sync, gbc)
            gbc.gridy = 1
            add(darkNeutral, gbc)
            gbc.gridy = 2
            add(dark, gbc)
            gbc.gridy = 3
            add(light, gbc)
            gbc.gridy = 4
            gbc.insets = JBUI.insets(6, 0, 2, 0)
            add(colorModesCb, gbc)
            gbc.gridy = 5
            gbc.insets = JBUI.insetsBottom(2)
            add(cmNotice, gbc)
            gbc.gridy = 6
            gbc.insets = JBUI.insets(4, 0, 2, 0)
            add(cursorTintCb, gbc)
            gbc.gridy = 7
            gbc.insets = JBUI.insetsBottom(2)
            add(ctNotice, gbc)
        }

        val helpLabel = JBLabel(HelixBundle.message("settings.theme.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(controlsPanel, BorderLayout.NORTH)
        inner.add(helpLabel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
    }

    private fun createCursorShapeGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.cursorShape.title")), BorderLayout.NORTH)

        fun createShapeCombo(): ComboBox<HelixCursorShape> {
            val combo = ComboBox(HelixCursorShape.entries.toTypedArray())
            combo.renderer = object : SimpleListCellRenderer<HelixCursorShape>() {
                override fun customize(
                    list: JList<out HelixCursorShape>,
                    value: HelixCursorShape?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    text = value?.displayName.orEmpty()
                }
            }
            return combo
        }

        val normalCombo = createShapeCombo()
        val insertCombo = createShapeCombo()
        val selectCombo = createShapeCombo()
        cursorShapeNormalComboBox = normalCombo
        cursorShapeInsertComboBox = insertCombo
        cursorShapeSelectComboBox = selectCombo

        val normalNotice = HelixOverrideNoticePanel("cursor-shape.normal")
        val insertNotice = HelixOverrideNoticePanel("cursor-shape.insert")
        val selectNotice = HelixOverrideNoticePanel("cursor-shape.select")
        cursorShapeNormalNotice = normalNotice
        cursorShapeInsertNotice = insertNotice
        cursorShapeSelectNotice = selectNotice

        val shapesGrid = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 12, 0, 0)
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.NONE
                weightx = 0.0
                insets = JBUI.insets(2, 0, 2, 12)
            }
            add(JBLabel(HelixBundle.message("settings.cursorShape.normal")), gbc)
            gbc.gridx = 1
            gbc.weightx = 1.0
            gbc.insets = JBUI.insets(2, 0)
            add(normalCombo, gbc)

            gbc.gridx = 0
            gbc.gridy = 1
            gbc.gridwidth = 2
            gbc.weightx = 0.0
            gbc.insets = JBUI.emptyInsets()
            add(normalNotice, gbc)

            gbc.gridy = 2
            gbc.gridwidth = 1
            gbc.insets = JBUI.insets(2, 0, 2, 12)
            add(JBLabel(HelixBundle.message("settings.cursorShape.insert")), gbc)
            gbc.gridx = 1
            gbc.weightx = 1.0
            gbc.insets = JBUI.insets(2, 0)
            add(insertCombo, gbc)

            gbc.gridx = 0
            gbc.gridy = 3
            gbc.gridwidth = 2
            gbc.weightx = 0.0
            gbc.insets = JBUI.emptyInsets()
            add(insertNotice, gbc)

            gbc.gridy = 4
            gbc.gridwidth = 1
            gbc.insets = JBUI.insets(2, 0, 2, 12)
            add(JBLabel(HelixBundle.message("settings.cursorShape.select")), gbc)
            gbc.gridx = 1
            gbc.weightx = 1.0
            gbc.insets = JBUI.insets(2, 0)
            add(selectCombo, gbc)

            gbc.gridx = 0
            gbc.gridy = 5
            gbc.gridwidth = 2
            gbc.weightx = 0.0
            gbc.insets = JBUI.emptyInsets()
            add(selectNotice, gbc)
        }

        val helpLabel = JBLabel(HelixBundle.message("settings.cursorShape.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(shapesGrid, BorderLayout.NORTH)
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
        val lnNotice = HelixOverrideNoticePanel("line-number")
        lineNumberNotice = lnNotice

        val comboPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyLeft(12)
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.NONE
                weightx = 1.0
                insets = JBUI.insets(2, 0)
            }
            add(combo, gbc)
            gbc.gridy = 1
            gbc.insets = JBUI.emptyInsets()
            add(lnNotice, gbc)
        }

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

        val dropdownsPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 24, 4, 0)
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(2, 0, 4, 12)
            }
            add(JBLabel(HelixBundle.message("settings.whichKey.cols.label")), gbc)
            gbc.gridx = 1
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.weightx = 1.0
            add(colCombo, gbc)

            gbc.gridx = 0
            gbc.gridy = 1
            gbc.fill = GridBagConstraints.NONE
            gbc.weightx = 0.0
            add(JBLabel(HelixBundle.message("settings.whichKey.maxRows.label")), gbc)
            gbc.gridx = 1
            gbc.fill = GridBagConstraints.HORIZONTAL
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
        darkNeutralThemeRadio?.isSelected == true -> HelixColorTheme.DARK_NEUTRAL
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

    fun isModified(settings: HelixSettings = HelixSettings.instance): Boolean {
        val themeModified = getSelectedColorTheme() != settings.colorTheme ||
            (
                !HelixEffectiveSettings.isOverridden("color-modes") &&
                    colorModesCheckBox?.isSelected != settings.colorModes
                ) ||
            (
                !HelixEffectiveSettings.isOverridden("cursor-tint") &&
                    cursorTintCheckBox?.isSelected != settings.cursorTint
                )
        val lineNumModified = !HelixEffectiveSettings.isOverridden("line-number") &&
            (lineNumberModeComboBox?.selectedItem as? HelixLineNumberMode) != settings.lineNumberMode
        val cursorShapeModified = (
            !HelixEffectiveSettings.isOverridden("cursor-shape.normal") &&
                (cursorShapeNormalComboBox?.selectedItem as? HelixCursorShape) != settings.cursorShapeNormal
            ) ||
            (
                !HelixEffectiveSettings.isOverridden("cursor-shape.insert") &&
                    (cursorShapeInsertComboBox?.selectedItem as? HelixCursorShape) != settings.cursorShapeInsert
                ) ||
            (
                !HelixEffectiveSettings.isOverridden("cursor-shape.select") &&
                    (cursorShapeSelectComboBox?.selectedItem as? HelixCursorShape) != settings.cursorShapeSelect
                )
        val whichKeyModified = enableWhichKeyCheckBox?.isSelected != settings.enableWhichKeyPopups ||
            getSelectedWhichKeyHintMode() != settings.whichKeyHintMode ||
            getSelectedWhichKeyColumnLayout() != settings.whichKeyColumnLayout ||
            getSelectedWhichKeyMaxRows() != settings.whichKeyMaxRows
        return themeModified || lineNumModified || cursorShapeModified || whichKeyModified
    }

    fun apply(settings: HelixSettings = HelixSettings.instance) {
        settings.colorTheme = getSelectedColorTheme()
        if (!HelixEffectiveSettings.isOverridden("color-modes")) {
            colorModesCheckBox?.let { settings.colorModes = it.isSelected }
        }
        if (!HelixEffectiveSettings.isOverridden("cursor-tint")) {
            cursorTintCheckBox?.let { settings.cursorTint = it.isSelected }
        }
        if (!HelixEffectiveSettings.isOverridden("line-number")) {
            (lineNumberModeComboBox?.selectedItem as? HelixLineNumberMode)?.let { settings.lineNumberMode = it }
        }
        if (!HelixEffectiveSettings.isOverridden("cursor-shape.normal")) {
            (cursorShapeNormalComboBox?.selectedItem as? HelixCursorShape)?.let { settings.cursorShapeNormal = it }
        }
        if (!HelixEffectiveSettings.isOverridden("cursor-shape.insert")) {
            (cursorShapeInsertComboBox?.selectedItem as? HelixCursorShape)?.let { settings.cursorShapeInsert = it }
        }
        if (!HelixEffectiveSettings.isOverridden("cursor-shape.select")) {
            (cursorShapeSelectComboBox?.selectedItem as? HelixCursorShape)?.let { settings.cursorShapeSelect = it }
        }
        enableWhichKeyCheckBox?.let { settings.enableWhichKeyPopups = it.isSelected }
        settings.whichKeyHintMode = getSelectedWhichKeyHintMode()
        settings.whichKeyColumnLayout = getSelectedWhichKeyColumnLayout()
        settings.whichKeyMaxRows = getSelectedWhichKeyMaxRows()

        HelixEditorEligibility.refreshAllEditors()
    }

    fun reset(settings: HelixSettings = HelixSettings.instance) {
        syncThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.SYNC)
        darkNeutralThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.DARK_NEUTRAL)
        darkThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.DARK)
        lightThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.LIGHT)

        val cmOverridden = colorModesNotice?.update() == true
        colorModesCheckBox?.isEnabled = !cmOverridden
        colorModesCheckBox?.isSelected = HelixEffectiveSettings.colorModes

        val ctOverridden = cursorTintNotice?.update() == true
        cursorTintCheckBox?.isEnabled = !ctOverridden
        cursorTintCheckBox?.isSelected = HelixEffectiveSettings.cursorTint

        val lnOverridden = lineNumberNotice?.update() == true
        lineNumberModeComboBox?.isEnabled = !lnOverridden
        lineNumberModeComboBox?.selectedItem = HelixEffectiveSettings.lineNumberMode

        val csnOverridden = cursorShapeNormalNotice?.update() == true
        cursorShapeNormalComboBox?.isEnabled = !csnOverridden
        cursorShapeNormalComboBox?.selectedItem = HelixEffectiveSettings.cursorShapeNormal

        val csiOverridden = cursorShapeInsertNotice?.update() == true
        cursorShapeInsertComboBox?.isEnabled = !csiOverridden
        cursorShapeInsertComboBox?.selectedItem = HelixEffectiveSettings.cursorShapeInsert

        val cssOverridden = cursorShapeSelectNotice?.update() == true
        cursorShapeSelectComboBox?.isEnabled = !cssOverridden
        cursorShapeSelectComboBox?.selectedItem = HelixEffectiveSettings.cursorShapeSelect

        enableWhichKeyCheckBox?.isSelected = settings.enableWhichKeyPopups
        whichKeyHelixCommandRadio?.isSelected = (settings.whichKeyHintMode == WhichKeyHintMode.HELIX_COMMAND)
        whichKeyIntelliJActionRadio?.isSelected = (settings.whichKeyHintMode == WhichKeyHintMode.INTELLIJ_ACTION)
        whichKeyColumnLayoutComboBox?.selectedItem = settings.whichKeyColumnLayout
        whichKeyMaxRowsComboBox?.selectedItem = settings.whichKeyMaxRows
    }

    fun disposeUIResources() {
        syncThemeRadio = null
        darkNeutralThemeRadio = null
        darkThemeRadio = null
        lightThemeRadio = null
        colorModesCheckBox = null
        cursorTintCheckBox = null
        colorModesNotice = null
        cursorTintNotice = null
        cursorShapeNormalComboBox = null
        cursorShapeInsertComboBox = null
        cursorShapeSelectComboBox = null
        cursorShapeNormalNotice = null
        cursorShapeInsertNotice = null
        cursorShapeSelectNotice = null
        lineNumberModeComboBox = null
        lineNumberNotice = null
        enableWhichKeyCheckBox = null
        whichKeyHelixCommandRadio = null
        whichKeyIntelliJActionRadio = null
        whichKeyColumnLayoutComboBox = null
        whichKeyMaxRowsComboBox = null
        HelixEffectiveSettings.removeChangeListener(overridesChangeListener)
    }
}
