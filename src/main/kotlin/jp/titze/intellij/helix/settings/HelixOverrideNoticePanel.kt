// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.config.HelixConfigLoader
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JPanel

/**
 * Renders an inline notice when an editor setting is locked/overridden by a TOML config file:
 * Set by intellij.toml: line-number = "relative" · Open file
 */
class HelixOverrideNoticePanel(val optionKey: String) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {

    val noticeLabel = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
        foreground = UIUtil.getContextHelpForeground()
    }

    private val separatorLabel = JBLabel(" · ").apply {
        font = JBUI.Fonts.smallFont()
        foreground = UIUtil.getContextHelpForeground()
    }

    val openLink = ActionLink(HelixBundle.message("settings.override.openFile")) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
        val override = HelixEffectiveSettings.getOverride(optionKey, project)
        val file = override?.sourceFile
        if (project != null && file != null && file.exists()) {
            val line = maxOf(0, override.lineNumber - 1)
            HelixConfigLoader.openIoFileInEditor(project, file, line, ModalityState.stateForComponent(this))
        }
    }.apply {
        font = JBUI.Fonts.smallFont()
    }

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.empty(2, 0)
        add(noticeLabel)
        add(Box.createHorizontalStrut(JBUI.scale(4)))
        add(separatorLabel)
        add(Box.createHorizontalStrut(JBUI.scale(4)))
        add(openLink)
        isVisible = false
    }

    fun update(): Boolean {
        val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
        val override = HelixEffectiveSettings.getOverride(optionKey, project)
        return if (override != null) {
            val fileName = override.sourceFile?.let {
                if (it.parentFile?.name == HelixConfigLoader.PROJECT_CONFIG_DIR) {
                    "${HelixConfigLoader.PROJECT_CONFIG_DIR}/${it.name}"
                } else {
                    it.name
                }
            } ?: "config.toml"
            noticeLabel.text = HelixBundle.message(
                "settings.override.notice",
                fileName,
                override.key,
                override.rawValueString,
            )
            isVisible = true
            true
        } else {
            isVisible = false
            false
        }
    }
}
