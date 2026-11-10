// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import jp.titze.intellij.helix.HelixBundle
import javax.swing.JComponent

/**
 * Main Settings configurable for Helix Keymap featuring General, Appearance, and Behavior tabs.
 */
class HelixConfigurable :
    SearchableConfigurable,
    Configurable.NoScroll {

    val generalPanel = HelixGeneralSettingsPanel()
    val appearancePanel = HelixAppearancePanel()
    val behaviorPanel = HelixBehaviorPanel()

    private var tabbedPane: JBTabbedPane? = null

    override fun getId(): String = "jp.titze.intellij.helix.settings"

    override fun getDisplayName(): String = HelixBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        val tabs = JBTabbedPane()
        tabs.addTab(HelixBundle.message("settings.tab.general"), createScrollPane(generalPanel.panel))
        tabs.addTab(HelixBundle.message("settings.tab.appearance"), createScrollPane(appearancePanel.panel))
        tabs.addTab(HelixBundle.message("settings.tab.behavior"), createScrollPane(behaviorPanel.panel))

        tabbedPane = tabs
        reset()
        return tabs
    }

    private fun createScrollPane(content: JComponent): JBScrollPane {
        val scrollPane = JBScrollPane(content)
        scrollPane.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = com.intellij.util.ui.JBUI.Borders.empty()
        return scrollPane
    }

    override fun isModified(): Boolean {
        val settings = HelixSettings.instance
        return generalPanel.isModified(settings) ||
            appearancePanel.isModified(settings) ||
            behaviorPanel.isModified(settings)
    }

    override fun apply() {
        val settings = HelixSettings.instance
        generalPanel.apply(settings)
        appearancePanel.apply(settings)
        behaviorPanel.apply(settings)
    }

    override fun reset() {
        val settings = HelixSettings.instance
        generalPanel.reset(settings)
        appearancePanel.reset(settings)
        behaviorPanel.reset(settings)
    }

    override fun disposeUIResources() {
        generalPanel.disposeUIResources()
        appearancePanel.disposeUIResources()
        behaviorPanel.disposeUIResources()
        tabbedPane = null
    }
}
