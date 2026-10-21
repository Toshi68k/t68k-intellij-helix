// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import jp.titze.intellij.helix.HelixBundle
import javax.swing.JComponent

/**
 * Main Settings configurable for Helix Keymap featuring a tabbed layout.
 */
class HelixConfigurable : SearchableConfigurable {

    val generalPanel = HelixGeneralSettingsPanel()
    val keymapBrowserPanel = HelixKeymapBrowserPanel()
    val ctrlKeyPanel = HelixCtrlKeyPanel()
    val configPathPanel = HelixConfigPathPanel()

    private var tabbedPane: JBTabbedPane? = null

    override fun getId(): String = "jp.titze.intellij.helix.settings"

    override fun getDisplayName(): String = HelixBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        val tabs = JBTabbedPane()
        tabs.addTab(HelixBundle.message("settings.tab.general"), JBScrollPane(generalPanel.panel))
        tabs.addTab(HelixBundle.message("settings.tab.keybindings"), keymapBrowserPanel.panel)
        tabs.addTab(HelixBundle.message("settings.tab.ctrlKeys"), JBScrollPane(ctrlKeyPanel.panel))
        tabs.addTab(HelixBundle.message("settings.tab.configFile"), JBScrollPane(configPathPanel.panel))

        tabbedPane = tabs
        reset()
        return tabs
    }

    override fun isModified(): Boolean {
        val settings = HelixSettings.instance
        return generalPanel.isModified(settings) ||
            ctrlKeyPanel.isModified(settings) ||
            configPathPanel.isModified(settings)
    }

    override fun apply() {
        val settings = HelixSettings.instance
        generalPanel.apply(settings)
        ctrlKeyPanel.apply(settings)
        configPathPanel.apply(settings)
        keymapBrowserPanel.reloadModeData()
    }

    override fun reset() {
        val settings = HelixSettings.instance
        generalPanel.reset(settings)
        ctrlKeyPanel.reset(settings)
        configPathPanel.reset(settings)
        keymapBrowserPanel.reloadModeData()
    }

    override fun disposeUIResources() {
        generalPanel.disposeUIResources()
        tabbedPane = null
    }
}
