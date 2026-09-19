package jp.titze.intellij.helix.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.ui.HelixPromptHistory
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class HelixConfigurable : SearchableConfigurable {

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

    override fun getId(): String = "jp.titze.intellij.helix.settings"

    override fun getDisplayName(): String = "Helix Keymap"

    override fun createComponent(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(16)

        val contentBox = JPanel()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)

        contentBox.add(createSearchSection())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createLineNavigationSection())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createWhichKeySection())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createJumpListSection())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createThemeSection())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createRegistersSection())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createEditorBehaviorSection())

        mainPanel.add(contentBox, BorderLayout.NORTH)

        reset()
        return mainPanel
    }

    private fun createSearchSection(): JPanel {
        val section = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel("Search and Selection Prompt UI")
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val radioGroup = ButtonGroup()
        val stock = JBRadioButton("Stock Helix: Inline bottom bar with live search/select-as-you-type")
        val popup = JBRadioButton("Popup Dialog: Floating centered dialog window")
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
        val spinnerLabel = JBLabel("Maximum prompt history entries per category (10 - 500):")
        spinnerPanel.add(spinnerLabel, BorderLayout.WEST)
        spinnerPanel.add(spinner, BorderLayout.CENTER)

        val searchOptionsBox = JPanel()
        searchOptionsBox.layout = BoxLayout(searchOptionsBox, BoxLayout.Y_AXIS)
        searchOptionsBox.add(optionsPanel)
        searchOptionsBox.add(spinnerPanel)

        val helpLabel = JBLabel(
            "<html>Applies to <code>/</code> (search), <code>?</code> (reverse search), " +
                "<code>s</code> (regex select), and <code>S</code> (regex split).<br/>" +
                "In Stock Helix mode, matches and selections update live in the editor buffer as you type.<br/>" +
                "Pressing <b>↑</b> / <b>↓</b> cycles through previously entered queries from the history ring.<br/>" +
                "Pressing <b>Esc</b> cancels and restores original selections.</html>",
        )
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
        val titleLabel = JBLabel("Line Navigation (Vertical Movement)")
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val radioGroup = ButtonGroup()
        val stock = JBRadioButton("Stock Helix: j / k move by visual (screen) line, gj / gk move by physical line")
        val vim = JBRadioButton("Vim Standard: j / k move by physical document line, gj / gk move by visual line")
        stockLineNavRadio = stock
        vimLineNavRadio = vim

        radioGroup.add(stock)
        radioGroup.add(vim)

        val optionsPanel = JPanel(GridLayout(2, 1, 0, 6))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(stock)
        optionsPanel.add(vim)

        val helpLabel = JBLabel(
            "<html>In Stock Helix, vertical movement (<code>j</code>/<code>k</code>) follows visual screen lines " +
                "when soft-wrapping is enabled,<br/>while <code>gj</code>/<code>gk</code> jump by physical lines. " +
                "Vim standard inverts this so <code>j</code>/<code>k</code> moves across physical lines.</html>",
        )
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
        val titleLabel = JBLabel("Which-Key Chord Menus")
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val checkBox = JBCheckBox("Enable interactive Which-Key popups on chords (Space, g, m, [, ], z, Ctrl+w)")
        enableWhichKeyCheckBox = checkBox

        val radioGroup = ButtonGroup()
        val helixRadio = JBRadioButton("Show Helix command names (snake_case, e.g. file_picker, goto_definition)")
        val ideaRadio = JBRadioButton("Show IntelliJ action IDs (PascalCase, e.g. GotoFile, GotoDeclaration)")
        whichKeyHelixCommandRadio = helixRadio
        whichKeyIntelliJActionRadio = ideaRadio

        radioGroup.add(helixRadio)
        radioGroup.add(ideaRadio)

        val modesPanel = JPanel(GridLayout(2, 1, 0, 4))
        modesPanel.border = JBUI.Borders.empty(4, 24, 4, 0)
        modesPanel.add(helixRadio)
        modesPanel.add(ideaRadio)

        val columnsGroup = ButtonGroup()
        val cols3Radio = JBRadioButton("Up to 3 columns: Compact height (shorter menu for widescreen monitors)")
        val cols2Radio = JBRadioButton("Up to 2 columns: Classic width (narrower menu)")
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

        val helpLabel = JBLabel(
            "<html>When enabled, pausing on a chord prefix displays an interactive popup menu with actions.<br/>" +
                "In the popup, pressing <b>Tab</b> switches between Helix commands and IntelliJ action IDs.<br/>" +
                "Hovering over any row reveals its underlying IntelliJ Action ID in a tooltip.</html>",
        )
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
        val titleLabel = JBLabel("Jump List")
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
        val spinnerLabel = JBLabel("Maximum jump list entries per project (10 - 1000):")
        spinnerPanel.add(spinnerLabel, BorderLayout.WEST)
        spinnerPanel.add(spinner, BorderLayout.CENTER)

        val helpLabel = JBLabel(
            "<html>Controls how many jump points are remembered for <code>Ctrl-O</code>, <code>Ctrl-I</code>, " +
                "and <code>:jumps</code>.<br/>Older jump locations beyond this limit are automatically discarded.</html>",
        )
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
        val titleLabel = JBLabel("Color Theme")
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val radioGroup = ButtonGroup()
        val sync = JBRadioButton("Sync with IDE: Automatically match IntelliJ's light / dark appearance")
        val dark = JBRadioButton("Dark: Always use Helix dark theme (deep cyan / charcoal palette)")
        val light = JBRadioButton("Light: Always use Helix light theme (light teal / parchment palette)")

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

        val helpLabel = JBLabel(
            "<html>Applies to Which-Key popups, Command palette, Jump list, and Search / Regex prompts.</html>",
        )
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
        val titleLabel = JBLabel("Registers and Clipboard")
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val checkBox = JBCheckBox("Sync default register with system clipboard (Helix behavior)")
        syncClipboardCheckBox = checkBox

        val optionsPanel = JPanel(BorderLayout())
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(checkBox, BorderLayout.NORTH)

        val helpLabel = JBLabel(
            "<html>When enabled, default yank (<code>y</code>) and delete (<code>d</code>/<code>c</code>) " +
                "synchronize with the OS clipboard.<br/>" +
                "Use <code>\"_d</code> or <code>\"_c</code> to delete/change without overwriting the clipboard.<br/>" +
                "When disabled, default yank/delete stay in the internal register, and " +
                "<code>Space+y</code> / <code>Space+p</code> (or <code>\"+</code>) target the clipboard.</html>",
        )
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
        val titleLabel = JBLabel("Editor Behavior")
        titleLabel.font = JBUI.Fonts.label().asBold()
        section.add(titleLabel, BorderLayout.NORTH)

        val checkBox = JBCheckBox("Reset to Normal mode when opening files or switching tabs")
        resetToNormalCheckBox = checkBox

        val optionsPanel = JPanel(BorderLayout())
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(checkBox, BorderLayout.NORTH)

        val helpLabel = JBLabel(
            "<html>When enabled, opening a file or switching editor tabs automatically enters " +
                "<b>Normal</b> mode.<br/>When disabled, each editor tab retains its last active mode.</html>",
        )
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
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

    override fun isModified(): Boolean {
        val settings = HelixSettings.instance
        val modifiedChecks = listOf(
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
        )
        return modifiedChecks.any { it }
    }

    override fun apply() {
        val settings = HelixSettings.instance
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

        ProjectManager.getInstance().openProjects.forEach { project ->
            project.getService(HelixJumpListService::class.java)?.trimToCapacity()
        }
    }

    override fun reset() {
        val settings = HelixSettings.instance
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
    }

    override fun disposeUIResources() {
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
    }
}
