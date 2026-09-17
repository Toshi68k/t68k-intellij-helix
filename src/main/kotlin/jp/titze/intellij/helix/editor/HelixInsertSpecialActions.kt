package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixKillToLineEndAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActions.killToLineEnd(editor)
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixDeleteWordBackwardAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.deleteWordBackward(editor)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixDeleteWordForwardAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.deleteWordForward(editor)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixInsertRegisterAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            state.setPendingSequence("C-r")
            HelixWhichKeyPopup.show(editor, "C-r")
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixInsertLineEndAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActionDelegate.executeAction("EditorLineEnd", editor)
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixInsertDeleteCharBackwardAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActionDelegate.executeAction("EditorBackSpace", editor)
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
