// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.ui.HelixPromptHistory
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Encapsulates general editor settings (activation, search, which-key, theme, jumplist, scratch splits).
 */
class HelixGeneralSettingsPanel {

    private var stockHelixRadio: JBRadioButton? = null
    private var popupRadio: JBRadioButton? = null
    private var promptHistorySpinner: JBIntSpinner? = null

    private var stockLineNavRadio: JBRadioButton? = null
    private var vimLineNavRadio: JBRadioButton? = null

    private var enableWhichKeyCheckBox: JBCheckBox? = null
    private var whichKeyHelixCommandRadio: JBRadioButton? = null
    private var whichKeyIntelliJActionRadio: JBRadioButton? = null
    private var whichKey3ColsRadio: JBRadioButton? = null
    private var whichKey2ColsRadio: JBRadioButton? = null

    private var jumpListSpinner: JBIntSpinner? = null

    private var syncThemeRadio: JBRadioButton? = null
    private var darkThemeRadio: JBRadioButton? = null
    private var lightThemeRadio: JBRadioButton? = null

    private var resetToNormalCheckBox: JBCheckBox? = null
    private var syncClipboardCheckBox: JBCheckBox? = null
    private var scratchInteractiveRadio: JBRadioButton? = null
    private var scratchBufferRadio: JBRadioButton? = null

    val activationPanel = HelixActivationPanel()
    val panel: JComponent = createMainPanel()

    private fun createMainPanel(): JComponent {
        val contentBox = JPanel()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)
        contentBox.border = JBUI.Borders.empty(16)

        contentBox.add(createActivationAndScopeGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createEditingAndNavigationGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createVisualPromptsAndFeedbackGroup())

        val wrapper = JPanel(BorderLayout())
        wrapper.add(contentBox, BorderLayout.NORTH)
        return wrapper
    }

    private fun createActivationAndScopeGroup(): JPanel {
        val group = JPanel()
        group.layout = BoxLayout(group, BoxLayout.Y_AXIS)
        group.add(TitledSeparator(HelixBundle.message("settings.group.activation")))
        group.add(Box.createVerticalStrut(JBUI.scale(10)))

        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(activationPanel.panel)
        inner.add(Box.createVerticalStrut(JBUI.scale(16)))
        inner.add(createThemeSection())

        group.add(inner)
        return group
    }

    private fun createEditingAndNavigationGroup(): JPanel {
        val group = JPanel()
        group.layout = BoxLayout(group, BoxLayout.Y_AXIS)
        group.add(TitledSeparator(HelixBundle.message("settings.group.behavior")))
        group.add(Box.createVerticalStrut(JBUI.scale(10)))

        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(createLineNavigationSection())
        inner.add(Box.createVerticalStrut(JBUI.scale(16)))
        inner.add(createScratchSplitSection())
        inner.add(Box.createVerticalStrut(JBUI.scale(16)))
        inner.add(createRegistersSection())
        inner.add(Box.createVerticalStrut(JBUI.scale(16)))
        inner.add(createEditorBehaviorSection())
        inner.add(Box.createVerticalStrut(JBUI.scale(16)))
        inner.add(createJumpListSection())

        group.add(inner)
        return group
    }

    private fun createVisualPromptsAndFeedbackGroup(): JPanel {
        val group = JPanel()
        group.layout = BoxLayout(group, BoxLayout.Y_AXIS)
        group.add(TitledSeparator(HelixBundle.message("settings.group.feedback")))
        group.add(Box.createVerticalStrut(JBUI.scale(10)))

        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(createSearchSection())
        inner.add(Box.createVerticalStrut(JBUI.scale(16)))
        inner.add(createWhichKeySection())

        group.add(inner)
        return group
    }

    private fun createSearchSection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel(HelixBundle.message("settings.search.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val radioGroup = ButtonGroup()
        val stock = JBRadioButton(HelixBundle.message("settings.search.mode.stock"))
        val popup = JBRadioButton(HelixBundle.message("settings.search.mode.popup"))
        stockHelixRadio = stock
        popupRadio = popup

        radioGroup.add(stock)
        radioGroup.add(popup)

        val optionsPanel = JPanel(GridLayout(2, 1, 0, 6))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(stock)
        optionsPanel.add(popup)

        val spinner = JBIntSpinner(
            HelixSettings.DEFAULT_PROMPT_HISTORY_MAX_ENTRIES,
            HelixSettings.MIN_PROMPT_HISTORY_ENTRIES,
            HelixSettings.MAX_PROMPT_HISTORY_ENTRIES,
        )
        promptHistorySpinner = spinner

        val spinnerPanel = JPanel(BorderLayout(8, 0))
        spinnerPanel.border = JBUI.Borders.empty(6, 12, 0, 0)
        val spinnerLabel = JBLabel(HelixBundle.message("settings.search.history.label"))
        spinnerPanel.add(spinnerLabel, BorderLayout.WEST)
        spinnerPanel.add(spinner, BorderLayout.CENTER)

        val searchOptionsBox = JPanel()
        searchOptionsBox.layout = BoxLayout(searchOptionsBox, BoxLayout.Y_AXIS)
        searchOptionsBox.add(optionsPanel)
        searchOptionsBox.add(spinnerPanel)

        val helpLabel = JBLabel(HelixBundle.message("settings.search.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(searchOptionsBox, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    private fun createLineNavigationSection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel(HelixBundle.message("settings.lineNav.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val radioGroup = ButtonGroup()
        val stock = JBRadioButton(HelixBundle.message("settings.lineNav.mode.stock"))
        val vim = JBRadioButton(HelixBundle.message("settings.lineNav.mode.vim"))
        stockLineNavRadio = stock
        vimLineNavRadio = vim

        radioGroup.add(stock)
        radioGroup.add(vim)

        val optionsPanel = JPanel(GridLayout(2, 1, 0, 6))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(stock)
        optionsPanel.add(vim)

        val helpLabel = JBLabel(HelixBundle.message("settings.lineNav.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    private fun createWhichKeySection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel(HelixBundle.message("settings.whichKey.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val checkBox = JBCheckBox(HelixBundle.message("settings.whichKey.enable"))
        enableWhichKeyCheckBox = checkBox

        val radioGroup = ButtonGroup()
        val helixRadio = JBRadioButton(HelixBundle.message("settings.whichKey.hint.helix"))
        val ideaRadio = JBRadioButton(HelixBundle.message("settings.whichKey.hint.idea"))
        whichKeyHelixCommandRadio = helixRadio
        whichKeyIntelliJActionRadio = ideaRadio

        radioGroup.add(helixRadio)
        radioGroup.add(ideaRadio)

        val modesPanel = JPanel(GridLayout(2, 1, 0, 4))
        modesPanel.border = JBUI.Borders.empty(4, 24, 4, 0)
        modesPanel.add(helixRadio)
        modesPanel.add(ideaRadio)

        val columnsGroup = ButtonGroup()
        val cols3Radio = JBRadioButton(HelixBundle.message("settings.whichKey.cols.3"))
        val cols2Radio = JBRadioButton(HelixBundle.message("settings.whichKey.cols.2"))
        whichKey3ColsRadio = cols3Radio
        whichKey2ColsRadio = cols2Radio

        columnsGroup.add(cols3Radio)
        columnsGroup.add(cols2Radio)

        val columnsPanel = JPanel(GridLayout(2, 1, 0, 4))
        columnsPanel.border = JBUI.Borders.empty(4, 24, 4, 0)
        columnsPanel.add(cols3Radio)
        columnsPanel.add(cols2Radio)

        val settingsBox = JPanel()
        settingsBox.layout = BoxLayout(settingsBox, BoxLayout.Y_AXIS)
        settingsBox.add(modesPanel)
        settingsBox.add(Box.createVerticalStrut(JBUI.scale(6)))
        settingsBox.add(columnsPanel)

        val optionsPanel = JPanel(BorderLayout(0, 4))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(checkBox, BorderLayout.NORTH)
        optionsPanel.add(settingsBox, BorderLayout.CENTER)

        val helpLabel = JBLabel(HelixBundle.message("settings.whichKey.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    private fun createJumpListSection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel(HelixBundle.message("settings.jumpList.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val spinner = JBIntSpinner(
            HelixSettings.DEFAULT_JUMP_LIST_MAX_ENTRIES,
            HelixSettings.MIN_JUMP_LIST_ENTRIES,
            HelixSettings.MAX_JUMP_LIST_ENTRIES,
        )
        jumpListSpinner = spinner

        val spinnerPanel = JPanel(BorderLayout(8, 0))
        spinnerPanel.border = JBUI.Borders.emptyLeft(12)
        val spinnerLabel = JBLabel(HelixBundle.message("settings.jumpList.history.label"))
        spinnerPanel.add(spinnerLabel, BorderLayout.WEST)
        spinnerPanel.add(spinner, BorderLayout.CENTER)

        val helpLabel = JBLabel(HelixBundle.message("settings.jumpList.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(spinnerPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    private fun createThemeSection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel(HelixBundle.message("settings.theme.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

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

        val helpLabel = JBLabel(HelixBundle.message("settings.theme.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    private fun createRegistersSection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel(HelixBundle.message("settings.registers.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val checkBox = JBCheckBox(HelixBundle.message("settings.registers.syncClipboard"))
        syncClipboardCheckBox = checkBox

        val optionsPanel = JPanel(BorderLayout())
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(checkBox, BorderLayout.NORTH)

        val helpLabel = JBLabel(HelixBundle.message("settings.registers.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    private fun createEditorBehaviorSection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel(HelixBundle.message("settings.editorBehavior.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val checkBox = JBCheckBox(HelixBundle.message("settings.editorBehavior.resetToNormal"))
        resetToNormalCheckBox = checkBox

        val optionsPanel = JPanel(BorderLayout())
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(checkBox, BorderLayout.NORTH)

        val helpLabel = JBLabel(HelixBundle.message("settings.editorBehavior.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    private fun createScratchSplitSection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel(HelixBundle.message("settings.scratchSplit.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val radioGroup = ButtonGroup()
        val interactive = JBRadioButton(HelixBundle.message("settings.scratchSplit.mode.interactive"))
        val buffer = JBRadioButton(HelixBundle.message("settings.scratchSplit.mode.buffer"))
        scratchInteractiveRadio = interactive
        scratchBufferRadio = buffer

        radioGroup.add(interactive)
        radioGroup.add(buffer)

        val optionsPanel = JPanel(GridLayout(2, 1, 0, 6))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(interactive)
        optionsPanel.add(buffer)

        val helpLabel = JBLabel(HelixBundle.message("settings.scratchSplit.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    private fun getSelectedScratchSplitMode(): HelixScratchSplitMode = if (scratchBufferRadio?.isSelected == true) {
        HelixScratchSplitMode.EMPTY_BUFFER
    } else {
        HelixScratchSplitMode.INTERACTIVE
    }

    private fun getSelectedSearchUiMode(): HelixSearchUiMode = if (stockHelixRadio?.isSelected == true) {
        HelixSearchUiMode.STOCK_HELIX
    } else {
        HelixSearchUiMode.POPUP
    }

    private fun getSelectedLineNavigationMode(): HelixLineNavigationMode = if (vimLineNavRadio?.isSelected == true) {
        HelixLineNavigationMode.VIM_STANDARD
    } else {
        HelixLineNavigationMode.HELIX_STANDARD
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

    private fun getSelectedColorTheme(): HelixColorTheme = when {
        darkThemeRadio?.isSelected == true -> HelixColorTheme.DARK
        lightThemeRadio?.isSelected == true -> HelixColorTheme.LIGHT
        else -> HelixColorTheme.SYNC
    }

    fun isModified(settings: HelixSettings): Boolean {
        val modifiedChecks = listOf(
            activationPanel.isModified(settings),
            getSelectedSearchUiMode() != settings.searchUiMode,
            getSelectedLineNavigationMode() != settings.lineNavigationMode,
            promptHistorySpinner?.number != settings.promptHistoryMaxEntries,
            enableWhichKeyCheckBox?.isSelected != settings.enableWhichKeyPopups,
            getSelectedWhichKeyHintMode() != settings.whichKeyHintMode,
            getSelectedWhichKeyColumnLayout() != settings.whichKeyColumnLayout,
            jumpListSpinner?.number != settings.jumpListMaxEntries,
            getSelectedColorTheme() != settings.colorTheme,
            resetToNormalCheckBox?.isSelected != settings.resetToNormalOnTabSwitch,
            syncClipboardCheckBox?.isSelected != settings.syncClipboardWithDefaultRegister,
            getSelectedScratchSplitMode() != settings.scratchSplitMode,
        )
        return modifiedChecks.any { it }
    }

    fun apply(settings: HelixSettings) {
        activationPanel.apply(settings)
        settings.searchUiMode = getSelectedSearchUiMode()
        settings.lineNavigationMode = getSelectedLineNavigationMode()
        promptHistorySpinner?.let {
            settings.promptHistoryMaxEntries = it.number
            HelixPromptHistory.trimToCapacity()
        }
        enableWhichKeyCheckBox?.let { settings.enableWhichKeyPopups = it.isSelected }
        settings.whichKeyHintMode = getSelectedWhichKeyHintMode()
        settings.whichKeyColumnLayout = getSelectedWhichKeyColumnLayout()
        jumpListSpinner?.let { settings.jumpListMaxEntries = it.number }
        settings.colorTheme = getSelectedColorTheme()
        resetToNormalCheckBox?.let { settings.resetToNormalOnTabSwitch = it.isSelected }
        syncClipboardCheckBox?.let { settings.syncClipboardWithDefaultRegister = it.isSelected }
        settings.scratchSplitMode = getSelectedScratchSplitMode()

        HelixEditorEligibility.refreshAllEditors()

        ProjectManager.getInstance().openProjects.forEach { project ->
            project.getService(HelixJumpListService::class.java)?.trimToCapacity()
        }
    }

    fun reset(settings: HelixSettings) {
        activationPanel.reset(settings)
        stockHelixRadio?.isSelected = (settings.searchUiMode == HelixSearchUiMode.STOCK_HELIX)
        popupRadio?.isSelected = (settings.searchUiMode == HelixSearchUiMode.POPUP)
        stockLineNavRadio?.isSelected = (settings.lineNavigationMode == HelixLineNavigationMode.HELIX_STANDARD)
        vimLineNavRadio?.isSelected = (settings.lineNavigationMode == HelixLineNavigationMode.VIM_STANDARD)
        promptHistorySpinner?.value = settings.promptHistoryMaxEntries

        enableWhichKeyCheckBox?.isSelected = settings.enableWhichKeyPopups
        whichKeyHelixCommandRadio?.isSelected = (settings.whichKeyHintMode == WhichKeyHintMode.HELIX_COMMAND)
        whichKeyIntelliJActionRadio?.isSelected = (settings.whichKeyHintMode == WhichKeyHintMode.INTELLIJ_ACTION)
        whichKey3ColsRadio?.isSelected = (settings.whichKeyColumnLayout == WhichKeyColumnLayout.THREE_COLUMNS)
        whichKey2ColsRadio?.isSelected = (settings.whichKeyColumnLayout == WhichKeyColumnLayout.TWO_COLUMNS)

        jumpListSpinner?.value = settings.jumpListMaxEntries

        syncThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.SYNC)
        darkThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.DARK)
        lightThemeRadio?.isSelected = (settings.colorTheme == HelixColorTheme.LIGHT)

        resetToNormalCheckBox?.isSelected = settings.resetToNormalOnTabSwitch
        syncClipboardCheckBox?.isSelected = settings.syncClipboardWithDefaultRegister
        scratchInteractiveRadio?.isSelected = (settings.scratchSplitMode == HelixScratchSplitMode.INTERACTIVE)
        scratchBufferRadio?.isSelected = (settings.scratchSplitMode == HelixScratchSplitMode.EMPTY_BUFFER)
    }

    fun disposeUIResources() {
        stockHelixRadio = null
        popupRadio = null
        stockLineNavRadio = null
        vimLineNavRadio = null
        promptHistorySpinner = null
        enableWhichKeyCheckBox = null
        whichKeyHelixCommandRadio = null
        whichKeyIntelliJActionRadio = null
        whichKey3ColsRadio = null
        whichKey2ColsRadio = null
        jumpListSpinner = null
        syncThemeRadio = null
        darkThemeRadio = null
        lightThemeRadio = null
        resetToNormalCheckBox = null
        syncClipboardCheckBox = null
        scratchInteractiveRadio = null
        scratchBufferRadio = null
    }
}
