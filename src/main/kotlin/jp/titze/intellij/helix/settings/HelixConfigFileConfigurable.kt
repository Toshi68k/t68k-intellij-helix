// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBScrollPane
import jp.titze.intellij.helix.HelixBundle
import javax.swing.JComponent

/**
 * Child settings configurable for Helix TOML configuration file path and live reloading.
 */
class HelixConfigFileConfigurable :
    SearchableConfigurable,
    Configurable.NoScroll {

    val configPathPanel = HelixConfigPathPanel()

    override fun getId(): String = "jp.titze.intellij.helix.settings.config"

    override fun getDisplayName(): String = HelixBundle.message("settings.config.displayName")

    override fun createComponent(): JComponent {
        reset()
        val scrollPane = JBScrollPane(configPathPanel.panel)
        scrollPane.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = com.intellij.util.ui.JBUI.Borders.empty()
        return scrollPane
    }

    override fun isModified(): Boolean {
        val settings = HelixSettings.instance
        return configPathPanel.isModified(settings)
    }

    override fun apply() {
        val settings = HelixSettings.instance
        configPathPanel.apply(settings)
    }

    override fun reset() {
        val settings = HelixSettings.instance
        configPathPanel.reset(settings)
    }

    override fun disposeUIResources() {
        // No specific resources to dispose
    }
}
