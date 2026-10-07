package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import jp.titze.intellij.helix.settings.HelixSettings

class HelixToggleModeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val settings = HelixSettings.instance
        settings.enabled = !settings.enabled
        HelixEditorEligibility.refreshAllEditors()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
