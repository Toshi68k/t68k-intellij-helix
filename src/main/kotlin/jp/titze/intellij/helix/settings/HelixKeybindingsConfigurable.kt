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
 * Child settings configurable for Helix Keybindings featuring Visual Browser and Ctrl Key routing tabs.
 */
class HelixKeybindingsConfigurable :
    SearchableConfigurable,
    Configurable.NoScroll {

    val keymapBrowserPanel = HelixKeymapBrowserPanel()
    val ctrlKeyPanel = HelixCtrlKeyPanel()

    private var tabbedPane: JBTabbedPane? = null

    override fun getId(): String = "jp.titze.intellij.helix.settings.keybindings"

    override fun getDisplayName(): String = HelixBundle.message("settings.keybindings.displayName")

    override fun createComponent(): JComponent {
        val tabs = JBTabbedPane()
        tabs.addTab(HelixBundle.message("settings.tab.keybindings"), keymapBrowserPanel.panel)
        val ctrlScroll = JBScrollPane(ctrlKeyPanel.panel)
        ctrlScroll.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ctrlScroll.border = com.intellij.util.ui.JBUI.Borders.empty()
        tabs.addTab(HelixBundle.message("settings.tab.ctrlKeys"), ctrlScroll)

        tabbedPane = tabs
        reset()
        return tabs
    }

    override fun isModified(): Boolean {
        val settings = HelixSettings.instance
        return ctrlKeyPanel.isModified(settings)
    }

    override fun apply() {
        val settings = HelixSettings.instance
        ctrlKeyPanel.apply(settings)
        keymapBrowserPanel.reloadModeData()
    }

    override fun reset() {
        val settings = HelixSettings.instance
        ctrlKeyPanel.reset(settings)
        keymapBrowserPanel.reloadModeData()
    }

    override fun disposeUIResources() {
        tabbedPane = null
    }
}
