// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
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
 * Settings panel for editor behavior, vertical line navigation, search prompt UI, scratch splits, and jump list.
 */
class HelixBehaviorPanel {

    private var stockLineNavRadio: JBRadioButton? = null
    private var vimLineNavRadio: JBRadioButton? = null

    private var stockHelixRadio: JBRadioButton? = null
    private var popupRadio: JBRadioButton? = null
    private var promptHistorySpinner: JBIntSpinner? = null

    private var scratchInteractiveRadio: JBRadioButton? = null
    private var scratchBufferRadio: JBRadioButton? = null

    private var jumpListSpinner: JBIntSpinner? = null

    val panel: JComponent = createMainPanel()

    private fun createMainPanel(): JComponent {
        val contentBox = JPanel()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)
        contentBox.border = JBUI.Borders.empty(16)

        contentBox.add(createLineNavigationGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createSearchGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createScratchSplitGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createJumpListGroup())

        val wrapper = JPanel(BorderLayout())
        wrapper.add(contentBox, BorderLayout.NORTH)
        return wrapper
    }

    private fun createLineNavigationGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.lineNav.title")), BorderLayout.NORTH)

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
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(optionsPanel, BorderLayout.NORTH)
        inner.add(helpLabel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
    }

    private fun createSearchGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.search.title")), BorderLayout.NORTH)

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

        val spinnerPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0))
        spinnerPanel.border = JBUI.Borders.empty(6, 12, 0, 0)
        spinnerPanel.add(JBLabel(HelixBundle.message("settings.search.history.label")))
        spinnerPanel.add(Box.createHorizontalStrut(JBUI.scale(8)))
        spinnerPanel.add(spinner)

        val controlsBox = JPanel()
        controlsBox.layout = BoxLayout(controlsBox, BoxLayout.Y_AXIS)
        controlsBox.add(optionsPanel)
        controlsBox.add(spinnerPanel)

        val helpLabel = JBLabel(HelixBundle.message("settings.search.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(controlsBox, BorderLayout.NORTH)
        inner.add(helpLabel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
    }

    private fun createScratchSplitGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.scratchSplit.title")), BorderLayout.NORTH)

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
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(optionsPanel, BorderLayout.NORTH)
        inner.add(helpLabel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
    }

    private fun createJumpListGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.jumpList.title")), BorderLayout.NORTH)

        val spinner = JBIntSpinner(
            HelixSettings.DEFAULT_JUMP_LIST_MAX_ENTRIES,
            HelixSettings.MIN_JUMP_LIST_ENTRIES,
            HelixSettings.MAX_JUMP_LIST_ENTRIES,
        )
        jumpListSpinner = spinner

        val spinnerPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0))
        spinnerPanel.border = JBUI.Borders.empty(6, 12, 0, 0)
        spinnerPanel.add(JBLabel(HelixBundle.message("settings.jumpList.history.label")))
        spinnerPanel.add(Box.createHorizontalStrut(JBUI.scale(8)))
        spinnerPanel.add(spinner)

        val helpLabel = JBLabel(HelixBundle.message("settings.jumpList.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.empty(8, 12, 0, 0)

        val inner = JPanel(BorderLayout(0, 6))
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(spinnerPanel, BorderLayout.NORTH)
        inner.add(helpLabel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
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

    fun isModified(settings: HelixSettings): Boolean {
        val lineNavModified = getSelectedLineNavigationMode() != settings.lineNavigationMode
        val searchModified = getSelectedSearchUiMode() != settings.searchUiMode ||
            promptHistorySpinner?.number != settings.promptHistoryMaxEntries
        val scratchOrJumpModified = getSelectedScratchSplitMode() != settings.scratchSplitMode ||
            jumpListSpinner?.number != settings.jumpListMaxEntries
        return lineNavModified || searchModified || scratchOrJumpModified
    }

    fun apply(settings: HelixSettings) {
        settings.lineNavigationMode = getSelectedLineNavigationMode()
        settings.searchUiMode = getSelectedSearchUiMode()
        promptHistorySpinner?.let {
            settings.promptHistoryMaxEntries = it.number
            HelixPromptHistory.trimToCapacity()
        }
        settings.scratchSplitMode = getSelectedScratchSplitMode()
        jumpListSpinner?.let { settings.jumpListMaxEntries = it.number }

        ProjectManager.getInstance().openProjects.forEach { project ->
            project.getService(HelixJumpListService::class.java)?.trimToCapacity()
        }
    }

    fun reset(settings: HelixSettings) {
        stockLineNavRadio?.isSelected = (settings.lineNavigationMode == HelixLineNavigationMode.HELIX_STANDARD)
        vimLineNavRadio?.isSelected = (settings.lineNavigationMode == HelixLineNavigationMode.VIM_STANDARD)
        stockHelixRadio?.isSelected = (settings.searchUiMode == HelixSearchUiMode.STOCK_HELIX)
        popupRadio?.isSelected = (settings.searchUiMode == HelixSearchUiMode.POPUP)
        promptHistorySpinner?.value = settings.promptHistoryMaxEntries
        scratchInteractiveRadio?.isSelected = (settings.scratchSplitMode == HelixScratchSplitMode.INTERACTIVE)
        scratchBufferRadio?.isSelected = (settings.scratchSplitMode == HelixScratchSplitMode.EMPTY_BUFFER)
        jumpListSpinner?.value = settings.jumpListMaxEntries
    }

    fun disposeUIResources() {
        stockLineNavRadio = null
        vimLineNavRadio = null
        stockHelixRadio = null
        popupRadio = null
        promptHistorySpinner = null
        scratchInteractiveRadio = null
        scratchBufferRadio = null
        jumpListSpinner = null
    }
}
