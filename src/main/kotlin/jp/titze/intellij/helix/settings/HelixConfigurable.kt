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
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class HelixConfigurable : SearchableConfigurable {

    private var stockHelixRadio: JBRadioButton? = null
    private var popupRadio: JBRadioButton? = null

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

        val helpLabel = JBLabel(
            "<html>Applies to <code>/</code> (search), <code>?</code> (reverse search), <code>s</code> (regex select), and <code>S</code> (regex split).<br/>In Stock Helix mode, matches and selections update live in the editor buffer as you type.<br/>Pressing <b>Esc</b> cancels and restores original selections.</html>",
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
            HelixSettings.instance.jumpListMaxEntries,
            HelixSettings.MIN_JUMP_LIST_ENTRIES,
            HelixSettings.MAX_JUMP_LIST_ENTRIES,
            10,
        )
        jumpListSpinner = spinner

        val rowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        rowPanel.border = JBUI.Borders.emptyLeft(12)
        val spinnerLabel = JBLabel("Maximum jump history entries:  ")
        rowPanel.add(spinnerLabel)
        rowPanel.add(spinner)

        val helpLabel = JBLabel(
            "<html>Maximum number of locations recorded in the jump list (default: 100, min: 10, max: 1000).<br/>Applies to <code>Ctrl+o</code> (jump backward) and <code>Ctrl+i</code> (jump forward).</html>",
        )
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(rowPanel, BorderLayout.NORTH)
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
        val sync = JBRadioButton("Sync with IDE (Follow active IntelliJ theme)")
        val dark = JBRadioButton("Dark (Force dark theme)")
        val light = JBRadioButton("Light (Force light theme)")

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
            "<html>Controls appearance for Helix overlays, Which-Key popups, Command Palette, and Jump List picker.</html>",
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

        val checkBox = JBCheckBox("Synchronize default register (\") with system clipboard")
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

    private fun getSelectedColorTheme(): HelixColorTheme = when {
        darkThemeRadio?.isSelected == true -> HelixColorTheme.DARK
        lightThemeRadio?.isSelected == true -> HelixColorTheme.LIGHT
        else -> HelixColorTheme.SYNC
    }

    override fun isModified(): Boolean {
        val settings = HelixSettings.instance
        if (getSelectedSearchUiMode() != settings.searchUiMode) return true
        if (jumpListSpinner?.number != settings.jumpListMaxEntries) return true
        if (getSelectedColorTheme() != settings.colorTheme) return true
        if (resetToNormalCheckBox?.isSelected != settings.resetToNormalOnTabSwitch) return true
        if (syncClipboardCheckBox?.isSelected != settings.syncClipboardWithDefaultRegister) return true
        return false
    }

    override fun apply() {
        val settings = HelixSettings.instance
        settings.searchUiMode = getSelectedSearchUiMode()
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
        jumpListSpinner = null
        syncThemeRadio = null
        darkThemeRadio = null
        lightThemeRadio = null
        resetToNormalCheckBox = null
        syncClipboardCheckBox = null
    }
}
