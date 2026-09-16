package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixSearchManager

abstract class HelixShellBaseAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixShellPipeAction : HelixShellBaseAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixSearchManager.startShellPipe(editor)
    }
}

class HelixShellInsertOutputAction : HelixShellBaseAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixSearchManager.startShellInsert(editor)
    }
}

class HelixShellAppendOutputAction : HelixShellBaseAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixSearchManager.startShellAppend(editor)
    }
}

class HelixShellKeepPipeAction : HelixShellBaseAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixSearchManager.startShellKeepPipe(editor)
    }
}

class HelixShellPipeToAction : HelixShellBaseAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixSearchManager.startShellPipeTo(editor)
    }
}
