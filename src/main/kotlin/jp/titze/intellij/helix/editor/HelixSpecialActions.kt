package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixJumplistPopup
import jp.titze.intellij.helix.ui.HelixRegistersPopup
import jp.titze.intellij.helix.ui.HelixSearchManager

class HelixEscapeAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixEscapeHandler.handleEscape(editor)
    }
}

class HelixExpandSelectionAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("EditorSelectWord", editor)
    }
}

class HelixShrinkSelectionAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("EditorUnSelectWord", editor)
    }
}

class HelixSelectPrevSiblingAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.selectPrevSibling(editor)
    }
}

class HelixSelectNextSiblingAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.selectNextSibling(editor)
    }
}

class HelixMoveParentNodeStartAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.moveParentNodeStart(editor)
    }
}

class HelixMoveParentNodeEndAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.moveParentNodeEnd(editor)
    }
}

class HelixSelectAllSiblingsAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.selectAllSiblings(editor)
    }
}

class HelixSelectAllChildrenAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.selectAllChildren(editor)
    }
}

class HelixSelectNextOccurrenceAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("SelectNextOccurrence", editor)
    }
}

class HelixCommentLineAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("CommentByLineComment", editor)
    }
}

class HelixPageDownAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.pageDown(editor, count)
    }
}

class HelixPageUpAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.pageUp(editor, count)
    }
}

class HelixHalfPageDownAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActionDelegate.executeAction("EditorDelete", editor)
            return
        }
        val count = state.takeCount() ?: 1
        HelixMotions.halfPageDown(editor, count)
    }
}

class HelixHalfPageUpAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActions.killToLineStart(editor)
            return
        }
        val count = state.takeCount() ?: 1
        HelixMotions.halfPageUp(editor, count)
    }
}

class HelixCopySelectionOnNextLineAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.copySelectionOnNextLine(editor, count)
    }
}

class HelixCopySelectionOnPrevLineAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.copySelectionOnPrevLine(editor, count)
    }
}

class HelixRemovePrimarySelectionAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.removePrimarySelection(editor)
    }
}

class HelixRotateSelectionsForwardAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.rotateSelections(editor, forward = true)
    }
}

class HelixRotateSelectionsBackwardAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.rotateSelections(editor, forward = false)
    }
}

class HelixSplitSelectionOnNewlineAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.splitSelectionOnNewlines(editor)
    }
}

class HelixFlipSelectionAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.flipSelection(editor)
    }
}

class HelixJumpBackwardAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: editor.project ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixJumpListService.getInstance(project).jumpBackward(editor, count)
    }
}

class HelixJumpForwardAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: editor.project ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixJumpListService.getInstance(project).jumpForward(editor, count)
    }
}

class HelixSaveJumpAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: editor.project ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActions.commitUndoCheckpoint(editor)
            return
        }
        state.clearCount()
        HelixJumpListService.getInstance(project).recordCurrent(editor, force = true)
    }
}

class HelixUndoCheckpointAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActions.commitUndoCheckpoint(editor)
    }
}

class HelixJumplistPickerAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixJumplistPopup.show(editor)
    }
}

class HelixRegistersPickerAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixRegistersPopup.show(editor)
    }
}

class HelixSwitchCaseAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixActions.toggleCase(editor, count)
    }
}

class HelixSwitchToLowercaseAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixActions.toLowerCase(editor, count)
    }
}

class HelixSwitchToUppercaseAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixActions.toUpperCase(editor, count)
    }
}

class HelixIncrementAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActionDelegate.executeAction("EditorLineStart", editor)
            return
        }
        val count = state.takeCount() ?: 1
        HelixActions.increment(editor, count)
    }
}

class HelixDecrementAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActionDelegate.executeAction("CodeCompletion", editor)
            return
        }
        val count = state.takeCount() ?: 1
        HelixActions.decrement(editor, count)
    }
}

class HelixWindowChordAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActions.deleteWordBackward(editor)
            return
        }
        HelixKeyHandler.startWindowChord(editor)
    }
}

class HelixTrimSelectionsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixActions.trimSelections(editor)
    }
}

class HelixAlignSelectionsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixActions.alignSelections(editor)
    }
}

class HelixKeepSelectionsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixSearchManager.startKeepSelections(editor)
    }
}

class HelixRemoveSelectionsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixSearchManager.startRemoveSelections(editor)
    }
}

class HelixEnsureSelectionsForwardAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixActions.ensureSelectionsForward(editor)
    }
}

class HelixMergeSelectionsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixActions.mergeSelections(editor)
    }
}

class HelixRotateSelectionsContentsForwardAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixActions.rotateSelectionsContents(editor, forward = true)
    }
}

class HelixRotateSelectionsContentsBackwardAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixActions.rotateSelectionsContents(editor, forward = false)
    }
}

class HelixReverseSelectionsContentsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixActions.reverseSelectionsContents(editor)
    }
}

class HelixExtendToLineBoundsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixMotions.extendToLineBounds(editor, count)
    }
}

class HelixShrinkToLineBoundsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixMotions.shrinkToLineBounds(editor)
    }
}

class HelixRepeatLastMotionAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixActions.repeatLastMotion(editor, count)
    }
}

class HelixDeleteNoYankAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            HelixActions.deleteWordForward(editor)
            return
        }
        val count = state.takeCount() ?: 1
        HelixActions.deleteSelectionNoYank(editor, count)
    }
}

class HelixChangeNoYankAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixActions.changeSelectionNoYank(editor, count)
    }
}

class HelixMergeAllSelectionsAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixActions.mergeAllSelections(editor)
    }
}

class HelixJoinSelectionsSpaceAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        HelixActions.joinLines(editor, count, selectSpace = true)
    }
}

class HelixSearchSelectionRawAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        HelixKeyHandler.recordJump(editor)
        HelixActions.searchSelection(editor, detectWordBoundaries = false)
    }
}

class HelixSignatureHelpAction : HelixEditorAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        HelixActionDelegate.executeAction("ParameterInfo", editor)
    }
}

class HelixEarlierAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        repeat(count) { HelixActionDelegate.executeAction("\$Undo", editor) }
    }
}

class HelixLaterAction : HelixEditorAction(requireInsertable = false) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return
        val count = state.takeCount() ?: 1
        repeat(count) { HelixActionDelegate.executeAction("\$Redo", editor) }
    }
}
