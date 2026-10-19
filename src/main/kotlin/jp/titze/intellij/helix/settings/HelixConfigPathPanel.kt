// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.config.HelixConfigLoader
import jp.titze.intellij.helix.config.HelixConfigWatcher
import jp.titze.intellij.helix.config.HelixTomlParser
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class HelixConfigPathPanel {

    val pathField = TextFieldWithBrowseButton()
    val statusLabel = JBLabel()
    val panel: JPanel = JPanel(BorderLayout(0, 8))

    init {
        val titleLabel = JBLabel(HelixBundle.message("settings.config.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()
        panel.add(titleLabel, BorderLayout.NORTH)

        setupPathField()

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        buttonPanel.border = JBUI.Borders.empty(4, 0)

        val openButton = JButton(HelixBundle.message("settings.config.btn.open"))
        openButton.addActionListener {
            val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
            HelixConfigLoader.openOrCreateConfigFile(project)
        }

        val reloadButton = JButton(HelixBundle.message("settings.config.btn.reload"))
        reloadButton.addActionListener {
            val result = HelixConfigLoader.loadConfig()
            HelixConfigWatcher.notifyReloadResult(result)
            refreshStatus()
        }

        val resetButton = JButton(HelixBundle.message("settings.config.btn.reset"))
        resetButton.addActionListener {
            pathField.text = ""
            refreshStatus()
        }

        buttonPanel.add(openButton)
        buttonPanel.add(Box.createHorizontalStrut(JBUI.scale(8)))
        buttonPanel.add(reloadButton)
        buttonPanel.add(Box.createHorizontalStrut(JBUI.scale(8)))
        buttonPanel.add(resetButton)

        statusLabel.font = JBUI.Fonts.smallFont()
        statusLabel.foreground = UIUtil.getContextHelpForeground()

        val helpLabel = JBLabel(HelixBundle.message("settings.config.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()

        pathField.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        buttonPanel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        statusLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        helpLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT

        val contentBox = JPanel()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)
        contentBox.border = JBUI.Borders.emptyLeft(12)

        contentBox.add(pathField)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(6)))
        contentBox.add(buttonPanel)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(6)))
        contentBox.add(statusLabel)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(8)))
        contentBox.add(helpLabel)

        panel.add(contentBox, BorderLayout.CENTER)
        refreshStatus()
    }

    private fun setupPathField() {
        (pathField.textField as? JBTextField)?.emptyText?.text =
            HelixBundle.message("settings.config.path.placeholder")

        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        descriptor.title = HelixBundle.message("settings.config.fileChooser.title")
        descriptor.withExtensionFilter("toml")

        pathField.addBrowseFolderListener(null, descriptor)
    }

    fun refreshStatus() {
        val target = pathField.text.trim()
        val file = if (target.isNotEmpty()) File(target) else HelixConfigLoader.findConfigFile()
        if (file == null || !file.exists()) {
            val defaultTarget = HelixConfigLoader.getTargetConfigFile().absolutePath
            statusLabel.text = HelixBundle.message("settings.config.status.default", defaultTarget)
            statusLabel.foreground = UIUtil.getContextHelpForeground()
            return
        }

        val parseResult = try {
            HelixTomlParser.parse(file.readText())
        } catch (e: Exception) {
            statusLabel.text = HelixBundle.message("settings.config.status.error", e.message ?: "read error")
            statusLabel.foreground = com.intellij.util.ui.NamedColorUtil.getErrorForeground()
            return
        }

        if (parseResult.errors.isNotEmpty()) {
            statusLabel.text = HelixBundle.message("settings.config.status.error", parseResult.errors.first())
            statusLabel.foreground = com.intellij.util.ui.NamedColorUtil.getErrorForeground()
        } else {
            statusLabel.text = HelixBundle.message(
                "settings.config.status.active",
                parseResult.bindings.size,
                file.name,
            )
            statusLabel.foreground = UIUtil.getContextHelpForeground()
        }
    }

    fun isModified(settings: HelixSettings): Boolean = pathField.text.trim() != settings.customConfigPath.trim()

    fun apply(settings: HelixSettings) {
        val newPath = pathField.text.trim()
        val changed = newPath != settings.customConfigPath.trim()
        settings.customConfigPath = newPath
        if (changed) {
            val result = HelixConfigLoader.loadConfig()
            HelixConfigWatcher.notifyReloadResult(result)
        }
        refreshStatus()
    }

    fun reset(settings: HelixSettings) {
        pathField.text = settings.customConfigPath
        refreshStatus()
    }
}
