package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixEscapeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)

        HelixWhichKeyPopup.hide()
        if (HelixPromptBar.cancelActivePrompt(editor)) {
            return
        }
        if (state.pendingSequence.isNotEmpty() || state.hasCount) {
            state.clearPendingSequence()
            state.clearCount()
            return
        }

        when (state.mode) {
            HelixMode.INSERT -> {
                HelixActions.enterNormalMode(editor)
                HelixMotions.collapseSelection(editor)
            }
            HelixMode.SELECT -> {
                HelixActions.enterNormalMode(editor)
            }
            HelixMode.NORMAL -> {
                HelixMotions.collapseSelection(editor)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixExpandSelectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("EditorSelectWord", editor)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixShrinkSelectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("EditorUnSelectWord", editor)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixSelectNextOccurrenceAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("SelectNextOccurrence", editor)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixCommentLineAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("CommentByLineComment", editor)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixCommandPaletteAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixCommandPopup.show(editor)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixPageDownAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.pageDown(editor, count)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixPageUpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.pageUp(editor, count)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixHalfPageDownAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.halfPageDown(editor, count)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixHalfPageUpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.halfPageUp(editor, count)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

class HelixCopySelectionOnNextLineAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.copySelectionOnNextLine(editor, count)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixCopySelectionOnPrevLineAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.copySelectionOnPrevLine(editor, count)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixRemovePrimarySelectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.removePrimarySelection(editor)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixRotateSelectionsForwardAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.rotateSelections(editor, forward = true)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixRotateSelectionsBackwardAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.rotateSelections(editor, forward = false)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixSplitSelectionOnNewlineAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.splitSelectionOnNewlines(editor)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class HelixFlipSelectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.flipSelection(editor)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val state = editor?.let { HelixStateManager.getOrCreate(it) }
        e.presentation.isEnabled = editor != null && state != null && !state.mode.isInsertable
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

