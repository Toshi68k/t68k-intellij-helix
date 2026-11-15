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
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Settings panel for managing the Helix TOML configuration file path, diagnostics, and reload actions.
 */
class HelixConfigPathPanel {

    val pathField = TextFieldWithBrowseButton()
    val statusLabel = JBLabel()
    val warningLabel = JBLabel()
    val panel: JPanel = JPanel(BorderLayout(0, 8))

    val overridesTableModel = DefaultTableModel(
        arrayOf(
            HelixBundle.message("settings.config.overrides.col.key"),
            HelixBundle.message("settings.config.overrides.col.value"),
            HelixBundle.message("settings.config.overrides.col.source"),
            HelixBundle.message("settings.config.overrides.col.status"),
        ),
        0,
    )
    val overridesTable = com.intellij.ui.table.JBTable(overridesTableModel).apply {
        fillsViewportHeight = true
        setShowGrid(true)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedOverride()
                }
            }
        })
    }

    init {
        panel.border = JBUI.Borders.empty(16)

        val titleLabel = JBLabel(HelixBundle.message("settings.config.title"))
        titleLabel.font = JBUI.Fonts.label().asBold()

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

        warningLabel.font = JBUI.Fonts.smallFont()
        warningLabel.foreground = com.intellij.ui.JBColor.namedColor(
            "Label.warningForeground",
            com.intellij.ui.JBColor(0x9E6200, 0xDB9D38),
        )
        warningLabel.isVisible = false

        val helpLabel = JBLabel(HelixBundle.message("settings.config.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()

        val overridesTitle = JBLabel(HelixBundle.message("settings.config.overrides.title")).apply {
            font = JBUI.Fonts.label().asBold()
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
        }

        val tableScrollPane = com.intellij.ui.components.JBScrollPane(overridesTable).apply {
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
            preferredSize = Dimension(Int.MAX_VALUE, JBUI.scale(160))
            minimumSize = Dimension(100, JBUI.scale(100))
        }

        pathField.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        buttonPanel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        statusLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        warningLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        helpLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT

        pathField.maximumSize = Dimension(
            Int.MAX_VALUE,
            maxOf(pathField.preferredSize.height, JBUI.scale(30)),
        )

        val contentBox = JPanel()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)
        contentBox.border = JBUI.Borders.emptyLeft(12)

        contentBox.add(pathField)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(8)))
        contentBox.add(buttonPanel)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(8)))
        contentBox.add(statusLabel)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(4)))
        contentBox.add(warningLabel)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(8)))
        contentBox.add(helpLabel)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(16)))
        contentBox.add(overridesTitle)
        contentBox.add(Box.createVerticalStrut(JBUI.scale(8)))
        contentBox.add(tableScrollPane)

        val topContainer = JPanel(BorderLayout(0, 8))
        topContainer.add(titleLabel, BorderLayout.NORTH)
        topContainer.add(contentBox, BorderLayout.CENTER)

        panel.add(topContainer, BorderLayout.NORTH)
        refreshStatus()
    }

    private fun setupPathField() {
        (pathField.textField as? JBTextField)?.emptyText?.text =
            HelixBundle.message("settings.config.path.placeholder")

        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        descriptor.title = HelixBundle.message("settings.config.fileChooser.title")
        descriptor.withExtensionFilter("toml")

        pathField.addBrowseFolderListener(null, descriptor)
        pathField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = refreshStatus()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = refreshStatus()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = refreshStatus()
        })
    }

    private fun openSelectedOverride() {
        val row = overridesTable.selectedRow
        if (row != -1) {
            val target = pathField.text.trim()
            val file = if (target.isNotEmpty()) File(target) else HelixConfigLoader.findConfigFile() ?: return
            val overrides = if (file.exists()) {
                val parseResult = try {
                    jp.titze.intellij.helix.config.HelixTomlParser.parse(file.readText())
                } catch (_: Exception) {
                    null
                }
                if (parseResult != null) {
                    jp.titze.intellij.helix.config.HelixEditorOptions
                        .evaluateTomlOptions(parseResult.editorOptions, file)
                        .values
                        .toList()
                } else {
                    HelixEffectiveSettings.getAllOverrides()
                }
            } else {
                HelixEffectiveSettings.getAllOverrides()
            }

            if (row < overrides.size) {
                val override = overrides[row]
                val sourceFile = override.sourceFile ?: file
                if (sourceFile.exists()) {
                    val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
                    val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
                        sourceFile,
                    )
                    if (vf != null && project != null) {
                        val line = maxOf(0, override.lineNumber - 1)
                        com.intellij.openapi.fileEditor.OpenFileDescriptor(project, vf, line, 0).navigate(true)
                    }
                }
            }
        }
    }

    fun refreshStatus() {
        val target = pathField.text.trim()
        val file = if (target.isNotEmpty()) File(target) else HelixConfigLoader.findConfigFile()
        if (file == null || !file.exists()) {
            val defaultTarget = HelixConfigLoader.getTargetConfigFile().absolutePath
            statusLabel.text = HelixBundle.message("settings.config.status.default", defaultTarget)
            statusLabel.foreground = UIUtil.getContextHelpForeground()
            warningLabel.isVisible = false
            overridesTableModel.rowCount = 0
            return
        }

        if (HelixConfigLoader.isCliConfigFile(file)) {
            warningLabel.text = HelixBundle.message("settings.config.warning.cliConfig", file.name)
            warningLabel.isVisible = true
        } else {
            warningLabel.isVisible = false
        }

        val result = try {
            HelixConfigLoader.validateConfig(file.readText())
        } catch (e: Exception) {
            statusLabel.text = HelixBundle.message("settings.config.status.error", e.message ?: "read error")
            statusLabel.foreground = com.intellij.util.ui.NamedColorUtil.getErrorForeground()
            overridesTableModel.rowCount = 0
            return
        }

        val parseResult = try {
            jp.titze.intellij.helix.config.HelixTomlParser.parse(file.readText())
        } catch (_: Exception) {
            null
        }
        val currentOverrides = if (parseResult != null) {
            jp.titze.intellij.helix.config.HelixEditorOptions
                .evaluateTomlOptions(parseResult.editorOptions, file)
                .values
                .toList()
        } else {
            HelixEffectiveSettings.getAllOverrides()
        }
        overridesTableModel.rowCount = 0
        for (override in currentOverrides) {
            val src = "${override.sourceFile?.name ?: file.name}:${override.lineNumber}"
            overridesTableModel.addRow(
                arrayOf(
                    override.key,
                    override.rawValueString,
                    src,
                    override.status.displayName,
                ),
            )
        }

        if (result.errors.isNotEmpty()) {
            statusLabel.text = HelixBundle.message("settings.config.status.error", result.errors.first())
            statusLabel.foreground = com.intellij.util.ui.NamedColorUtil.getErrorForeground()
        } else {
            statusLabel.text = HelixBundle.message(
                "settings.config.status.active",
                result.bindingCount,
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
