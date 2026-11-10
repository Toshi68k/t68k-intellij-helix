// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings panel for global activation, editor scopes, clipboard synchronization, and tab switching behavior.
 */
class HelixGeneralSettingsPanel {

    private var resetToNormalCheckBox: JBCheckBox? = null
    private var syncClipboardCheckBox: JBCheckBox? = null

    val activationPanel = HelixActivationPanel()
    val panel: JComponent = createMainPanel()

    private fun createMainPanel(): JComponent {
        val contentBox = JPanel()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)
        contentBox.border = JBUI.Borders.empty(16)

        contentBox.add(createActivationAndScopeGroup())
        contentBox.add(Box.createVerticalStrut(JBUI.scale(20)))
        contentBox.add(createEditorBehaviorGroup())

        val wrapper = JPanel(BorderLayout())
        wrapper.add(contentBox, BorderLayout.NORTH)
        return wrapper
    }

    private fun createActivationAndScopeGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.group.activation")), BorderLayout.NORTH)

        val inner = JPanel(BorderLayout())
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(activationPanel.panel, BorderLayout.CENTER)

        group.add(inner, BorderLayout.CENTER)
        return group
    }

    private fun createEditorBehaviorGroup(): JPanel {
        val group = JPanel(BorderLayout(0, 10))
        group.add(TitledSeparator(HelixBundle.message("settings.group.editorBehavior")), BorderLayout.NORTH)

        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.border = JBUI.Borders.emptyLeft(8)
        inner.add(createRegistersSection())
        inner.add(Box.createVerticalStrut(JBUI.scale(16)))
        inner.add(createEditorBehaviorSection())

        group.add(inner, BorderLayout.CENTER)
        return group
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
        helpLabel.border = JBUI.Borders.empty(6, 12, 0, 0)

        val box = JPanel(BorderLayout(0, 4))
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
        helpLabel.border = JBUI.Borders.empty(6, 12, 0, 0)

        val box = JPanel(BorderLayout(0, 4))
        box.add(optionsPanel, BorderLayout.NORTH)
        box.add(helpLabel, BorderLayout.CENTER)
        section.add(box, BorderLayout.CENTER)
        return section
    }

    fun isModified(settings: HelixSettings): Boolean {
        val activationModified = activationPanel.isModified(settings)
        val behaviorModified = resetToNormalCheckBox?.isSelected != settings.resetToNormalOnTabSwitch ||
            syncClipboardCheckBox?.isSelected != settings.syncClipboardWithDefaultRegister
        return activationModified || behaviorModified
    }

    fun apply(settings: HelixSettings) {
        activationPanel.apply(settings)
        resetToNormalCheckBox?.let { settings.resetToNormalOnTabSwitch = it.isSelected }
        syncClipboardCheckBox?.let { settings.syncClipboardWithDefaultRegister = it.isSelected }
        HelixEditorEligibility.refreshAllEditors()
    }

    fun reset(settings: HelixSettings) {
        activationPanel.reset(settings)
        resetToNormalCheckBox?.isSelected = settings.resetToNormalOnTabSwitch
        syncClipboardCheckBox?.isSelected = settings.syncClipboardWithDefaultRegister
    }

    fun disposeUIResources() {
        resetToNormalCheckBox = null
        syncClipboardCheckBox = null
    }
}
