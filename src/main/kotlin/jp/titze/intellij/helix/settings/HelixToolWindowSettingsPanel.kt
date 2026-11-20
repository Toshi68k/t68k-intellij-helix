// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

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

class HelixToolWindowSettingsPanel {

    private val masterEnabledBox = JBCheckBox(HelixBundle.message("settings.toolwindow.master"))
    private val projectBox = JBCheckBox(HelixBundle.message("settings.toolwindow.project"))
    private val findBox = JBCheckBox(HelixBundle.message("settings.toolwindow.find"))
    private val vcsBox = JBCheckBox(HelixBundle.message("settings.toolwindow.vcs"))
    private val problemsBox = JBCheckBox(HelixBundle.message("settings.toolwindow.problems"))
    private val structureBox = JBCheckBox(HelixBundle.message("settings.toolwindow.structure"))
    private val servicesBox = JBCheckBox(HelixBundle.message("settings.toolwindow.services"))
    private val debugBox = JBCheckBox(HelixBundle.message("settings.toolwindow.debug"))
    private val otherBox = JBCheckBox(HelixBundle.message("settings.toolwindow.other"))

    val panel: JPanel = JPanel(BorderLayout(0, 8))

    init {
        val titleLabel = JBLabel(HelixBundle.message("settings.toolwindow.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        panel.add(titleLabel, BorderLayout.NORTH)

        val targetsPanel = JPanel(GridLayout(8, 1, 0, 4))
        targetsPanel.border = JBUI.Borders.empty(4, 24, 4, 0)
        targetsPanel.add(projectBox)
        targetsPanel.add(findBox)
        targetsPanel.add(vcsBox)
        targetsPanel.add(problemsBox)
        targetsPanel.add(structureBox)
        targetsPanel.add(servicesBox)
        targetsPanel.add(debugBox)
        targetsPanel.add(otherBox)

        masterEnabledBox.addActionListener {
            updateTargetBoxesEnabled()
        }

        val settingsBox = JPanel()
        settingsBox.layout = BoxLayout(settingsBox, BoxLayout.Y_AXIS)
        settingsBox.add(targetsPanel)

        val optionsPanel = JPanel(BorderLayout(0, 4))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(masterEnabledBox, BorderLayout.NORTH)
        optionsPanel.add(settingsBox, BorderLayout.CENTER)

        val helpLabel = JBLabel(HelixBundle.message("settings.toolwindow.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val box = JPanel(BorderLayout(0, 8))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        panel.add(box, BorderLayout.CENTER)
    }

    private fun updateTargetBoxesEnabled() {
        val enabled = masterEnabledBox.isSelected
        projectBox.isEnabled = enabled
        findBox.isEnabled = enabled
        vcsBox.isEnabled = enabled
        problemsBox.isEnabled = enabled
        structureBox.isEnabled = enabled
        servicesBox.isEnabled = enabled
        debugBox.isEnabled = enabled
        otherBox.isEnabled = enabled
    }

    fun isModified(settings: HelixSettings): Boolean {
        if (masterEnabledBox.isSelected != settings.toolWindowNavigationEnabled) return true
        if (otherBox.isSelected != settings.toolWindowOtherEnabled) return true
        val currentTargets = collectSelectedTargets()
        return currentTargets != settings.toolWindowEnabledTargets
    }

    private fun collectSelectedTargets(): Set<String> {
        val set = mutableSetOf<String>()
        if (projectBox.isSelected) set.add("Project")
        if (findBox.isSelected) set.add("Find")
        if (vcsBox.isSelected) {
            set.add("Version Control")
            set.add("Commit")
        }
        if (problemsBox.isSelected) set.add("Problems View")
        if (structureBox.isSelected) set.add("Structure")
        if (servicesBox.isSelected) {
            set.add("Services")
            set.add("Run")
        }
        if (debugBox.isSelected) set.add("Debug")
        return set
    }

    fun apply(settings: HelixSettings) {
        settings.toolWindowNavigationEnabled = masterEnabledBox.isSelected
        settings.toolWindowOtherEnabled = otherBox.isSelected
        settings.toolWindowEnabledTargets = collectSelectedTargets()
    }

    fun reset(settings: HelixSettings) {
        masterEnabledBox.isSelected = settings.toolWindowNavigationEnabled
        val targets = settings.toolWindowEnabledTargets
        projectBox.isSelected = targets.contains("Project")
        findBox.isSelected = targets.contains("Find")
        vcsBox.isSelected = targets.contains("Version Control") || targets.contains("Commit")
        problemsBox.isSelected = targets.contains("Problems View")
        structureBox.isSelected = targets.contains("Structure")
        servicesBox.isSelected = targets.contains("Services") || targets.contains("Run")
        debugBox.isSelected = targets.contains("Debug")
        otherBox.isSelected = settings.toolWindowOtherEnabled
        updateTargetBoxesEnabled()
    }
}
