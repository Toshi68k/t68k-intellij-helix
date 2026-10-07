package jp.titze.intellij.helix.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.JPanel

class HelixActivationPanel {

    private val masterEnabledBox = JBCheckBox(HelixBundle.message("settings.activation.enabled"))
    private val mainEditorBox = JBCheckBox(HelixBundle.message("settings.activation.mainEditor"))
    private val diffBox = JBCheckBox(HelixBundle.message("settings.activation.diff"))
    private val consoleBox = JBCheckBox(HelixBundle.message("settings.activation.console"))
    private val commitMessageBox = JBCheckBox(HelixBundle.message("settings.activation.commitMessage"))
    private val otherEditorsBox = JBCheckBox(HelixBundle.message("settings.activation.otherEditors"))

    val panel: JPanel = JPanel(BorderLayout(0, 8))

    init {
        val titleLabel = JBLabel(HelixBundle.message("settings.activation.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        panel.add(titleLabel, BorderLayout.NORTH)

        mainEditorBox.isSelected = true
        mainEditorBox.isEnabled = false

        val scopeOptionsPanel = JPanel(GridLayout(5, 1, 0, 4))
        scopeOptionsPanel.border = JBUI.Borders.empty(4, 24, 4, 0)
        scopeOptionsPanel.add(mainEditorBox)
        scopeOptionsPanel.add(diffBox)
        scopeOptionsPanel.add(consoleBox)
        scopeOptionsPanel.add(commitMessageBox)
        scopeOptionsPanel.add(otherEditorsBox)

        val settingsBox = JPanel()
        settingsBox.layout = BoxLayout(settingsBox, BoxLayout.Y_AXIS)
        settingsBox.add(scopeOptionsPanel)

        val optionsPanel = JPanel(BorderLayout(0, 4))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(masterEnabledBox, BorderLayout.NORTH)
        optionsPanel.add(settingsBox, BorderLayout.CENTER)

        val helpLabel = JBLabel(HelixBundle.message("settings.activation.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        panel.add(box, BorderLayout.CENTER)
    }

    fun isModified(settings: HelixSettings): Boolean = masterEnabledBox.isSelected != settings.enabled ||
        diffBox.isSelected != settings.activateInDiff ||
        consoleBox.isSelected != settings.activateInConsole ||
        commitMessageBox.isSelected != settings.activateInCommitMessage ||
        otherEditorsBox.isSelected != settings.activateInOtherEditors

    fun apply(settings: HelixSettings) {
        settings.enabled = masterEnabledBox.isSelected
        settings.activateInDiff = diffBox.isSelected
        settings.activateInConsole = consoleBox.isSelected
        settings.activateInCommitMessage = commitMessageBox.isSelected
        settings.activateInOtherEditors = otherEditorsBox.isSelected
    }

    fun reset(settings: HelixSettings) {
        masterEnabledBox.isSelected = settings.enabled
        diffBox.isSelected = settings.activateInDiff
        consoleBox.isSelected = settings.activateInConsole
        commitMessageBox.isSelected = settings.activateInCommitMessage
        otherEditorsBox.isSelected = settings.activateInOtherEditors
    }
}
