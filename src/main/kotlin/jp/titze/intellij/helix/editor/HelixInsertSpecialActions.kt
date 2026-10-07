package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixKillToLineEndAction : HelixEditorAction(requireInsertable = true) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActions.killToLineEnd(editor)
        }
    }
}

class HelixDeleteWordBackwardAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.deleteWordBackward(editor)
    }
}

class HelixDeleteWordForwardAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.deleteWordForward(editor)
    }
}

class HelixInsertRegisterAction : HelixEditorAction(requireInsertable = true) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            state.setPendingSequence("C-r")
            HelixWhichKeyPopup.show(editor, "C-r")
        }
    }
}

class HelixInsertLineEndAction : HelixEditorAction(requireInsertable = true) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActionDelegate.executeAction("EditorLineEnd", editor)
        }
    }
}

class HelixInsertDeleteCharBackwardAction : HelixEditorAction(requireInsertable = true) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActionDelegate.executeAction("EditorBackSpace", editor)
        }
    }
}
