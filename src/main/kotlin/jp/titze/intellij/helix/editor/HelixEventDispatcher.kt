package jp.titze.intellij.helix.editor

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.keymap.HelixWindowKeymap
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixSearchManager
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup
import java.awt.AWTEvent
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

class HelixEventDispatcher : IdeEventQueue.EventDispatcher {

    override fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent) return false
        if (handleWhichKeyPopupEvent(e)) return true
        if (e.id != KeyEvent.KEY_PRESSED) return false

        val editor = findFocusedEditor(e) ?: return false
        if (editor.isOneLineMode || editor.isViewer) return false

        val isCtrl = e.isControlDown && !e.isMetaDown && !e.isAltDown
        val isAlt = e.isAltDown && !e.isControlDown && !e.isMetaDown
        val isNoModifiers = !e.isControlDown && !e.isMetaDown && !e.isAltDown && !e.isShiftDown

        if (handleEscapeEvent(e, editor, isNoModifiers, isCtrl)) return true

        val state = HelixStateManager.getOrCreate(editor)

        if (state.mode.isInsertable) {
            val handledInsert = when {
                isCtrl -> handleCtrlInsertShortcut(e, editor, state)
                isAlt -> handleAltInsertShortcut(e, editor)
                else -> false
            }
            if (handledInsert) {
                e.consume()
                return true
            }
            return false
        }

        val handled = when {
            isCtrl -> handleCtrlShortcut(e, editor, state)
            isNoModifiers -> handleNoModifiersShortcut(e, editor, state)
            isAlt -> handleAltShortcut(e, editor, state)
            else -> false
        }

        if (handled) {
            e.consume()
            return true
        }

        return false
    }

    private fun handleWhichKeyPopupEvent(e: KeyEvent): Boolean {
        if (!HelixWhichKeyPopup.isShowing()) return false
        if (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_TAB) {
            HelixWhichKeyPopup.toggleHintMode()
            e.consume()
            return true
        }
        if (e.id == KeyEvent.KEY_TYPED && e.keyChar == '\t') {
            e.consume()
            return true
        }
        return false
    }

    private fun handleEscapeEvent(e: KeyEvent, editor: Editor, isNoModifiers: Boolean, isCtrl: Boolean): Boolean {
        val isEscape = (isNoModifiers && e.keyCode == KeyEvent.VK_ESCAPE) ||
            (isCtrl && e.keyCode == KeyEvent.VK_OPEN_BRACKET)
        if (!isEscape) return false
        val lookup = com.intellij.codeInsight.lookup.LookupManager.getActiveLookup(editor)
        if (lookup != null) return false
        HelixEscapeHandler.handleEscape(editor)
        e.consume()
        return true
    }

    private fun handleCtrlInsertShortcut(
        e: KeyEvent,
        editor: Editor,
        state: jp.titze.intellij.helix.state.HelixEditorState,
    ): Boolean = when (e.keyCode) {
        KeyEvent.VK_A -> {
            HelixActionDelegate.executeAction("EditorLineStart", editor)
            true
        }

        KeyEvent.VK_E -> {
            HelixActionDelegate.executeAction("EditorLineEnd", editor)
            true
        }

        KeyEvent.VK_H -> {
            HelixActionDelegate.executeAction("EditorBackSpace", editor)
            true
        }

        KeyEvent.VK_D -> {
            HelixActionDelegate.executeAction("EditorDelete", editor)
            true
        }

        KeyEvent.VK_S -> {
            HelixActions.commitUndoCheckpoint(editor)
            true
        }

        KeyEvent.VK_W -> {
            HelixActions.deleteWordBackward(editor)
            true
        }

        KeyEvent.VK_U -> {
            HelixActions.killToLineStart(editor)
            true
        }

        KeyEvent.VK_K -> {
            HelixActions.killToLineEnd(editor)
            true
        }

        KeyEvent.VK_R -> {
            state.setPendingSequence("C-r")
            HelixWhichKeyPopup.show(editor, "C-r")
            true
        }

        KeyEvent.VK_X -> {
            HelixActionDelegate.executeAction("CodeCompletion", editor)
            true
        }

        KeyEvent.VK_P -> {
            HelixActionDelegate.executeAction("ParameterInfo", editor)
            true
        }

        else -> false
    }

    private fun handleAltInsertShortcut(e: KeyEvent, editor: Editor): Boolean = when {
        e.keyCode == KeyEvent.VK_BACK_SPACE -> {
            HelixActions.deleteWordBackward(editor)
            true
        }

        isAltForwardDelete(e) -> {
            HelixActions.deleteWordForward(editor)
            true
        }

        else -> false
    }

    private fun isAltForwardDelete(e: KeyEvent): Boolean = e.keyCode == KeyEvent.VK_DELETE || isAltD(e)

    private fun isAltD(e: KeyEvent): Boolean = !e.isShiftDown && (e.keyCode == KeyEvent.VK_D || e.keyChar == 'd')

    private fun handleCtrlShortcut(
        e: KeyEvent,
        editor: Editor,
        state: jp.titze.intellij.helix.state.HelixEditorState,
    ): Boolean {
        if (state.pendingSequence == "C-w") {
            val ch = when (e.keyCode) {
                KeyEvent.VK_V -> 'v'
                KeyEvent.VK_S -> 's'
                KeyEvent.VK_H -> 'h'
                KeyEvent.VK_J -> 'j'
                KeyEvent.VK_K -> 'k'
                KeyEvent.VK_L -> 'l'
                KeyEvent.VK_W -> 'w'
                KeyEvent.VK_Q -> 'q'
                KeyEvent.VK_C -> 'c'
                KeyEvent.VK_O -> 'o'
                else -> null
            }
            if (ch != null) {
                HelixWhichKeyPopup.hide()
                state.clearPendingSequence()
                return HelixWindowKeymap.handle(ch, editor)
            }
        }

        return when (e.keyCode) {
            KeyEvent.VK_W -> {
                HelixKeyHandler.startWindowChord(editor)
                true
            }

            KeyEvent.VK_F -> {
                HelixMotions.pageDown(editor, state.takeCount() ?: 1)
                true
            }

            KeyEvent.VK_B -> {
                HelixMotions.pageUp(editor, state.takeCount() ?: 1)
                true
            }

            KeyEvent.VK_D -> {
                HelixMotions.halfPageDown(editor, state.takeCount() ?: 1)
                true
            }

            KeyEvent.VK_U -> {
                HelixMotions.halfPageUp(editor, state.takeCount() ?: 1)
                true
            }

            KeyEvent.VK_C -> {
                HelixActionDelegate.executeAction("CommentByLineComment", editor)
                true
            }

            KeyEvent.VK_O -> {
                val count = state.takeCount() ?: 1
                editor.project?.let { HelixJumpListService.getInstance(it).jumpBackward(editor, count) }
                true
            }

            KeyEvent.VK_I -> {
                val count = state.takeCount() ?: 1
                editor.project?.let { HelixJumpListService.getInstance(it).jumpForward(editor, count) }
                true
            }

            KeyEvent.VK_S -> {
                state.clearCount()
                editor.project?.let { HelixJumpListService.getInstance(it).recordCurrent(editor, force = true) }
                true
            }

            KeyEvent.VK_A -> {
                HelixActions.increment(editor, state.takeCount() ?: 1)
                true
            }

            KeyEvent.VK_X -> {
                HelixActions.decrement(editor, state.takeCount() ?: 1)
                true
            }

            else -> false
        }
    }

    private fun handleNoModifiersShortcut(
        e: KeyEvent,
        editor: Editor,
        state: jp.titze.intellij.helix.state.HelixEditorState,
    ): Boolean = when (e.keyCode) {
        KeyEvent.VK_PAGE_DOWN -> {
            HelixMotions.pageDown(editor, state.takeCount() ?: 1)
            true
        }

        KeyEvent.VK_PAGE_UP -> {
            HelixMotions.pageUp(editor, state.takeCount() ?: 1)
            true
        }

        else -> false
    }

    private fun handleAltShortcut(
        e: KeyEvent,
        editor: Editor,
        state: jp.titze.intellij.helix.state.HelixEditorState,
    ): Boolean {
        if (handleAltShellShortcut(e, editor)) return true
        if (handleAltAstShortcut(e, editor)) return true

        return when {
            e.keyCode == KeyEvent.VK_BACK_QUOTE || e.keyChar == '`' -> {
                HelixActions.toUpperCase(editor, state.takeCount() ?: 1)
                true
            }

            !e.isShiftDown && (e.keyCode == KeyEvent.VK_U || e.keyChar == 'u') -> {
                val count = state.takeCount() ?: 1
                repeat(count) { HelixActionDelegate.executeAction("\$Undo", editor) }
                true
            }

            (e.isShiftDown && e.keyCode == KeyEvent.VK_U) || e.keyChar == 'U' -> {
                val count = state.takeCount() ?: 1
                repeat(count) { HelixActionDelegate.executeAction("\$Redo", editor) }
                true
            }

            !e.isShiftDown && (e.keyCode == KeyEvent.VK_K || e.keyChar == 'k') -> {
                HelixSearchManager.startKeepSelections(editor)
                true
            }

            e.isShiftDown && (e.keyCode == KeyEvent.VK_K || e.keyChar == 'K') -> {
                HelixSearchManager.startRemoveSelections(editor)
                true
            }

            e.keyChar == ':' || (e.isShiftDown && e.keyCode == KeyEvent.VK_SEMICOLON) -> {
                HelixActions.ensureSelectionsForward(editor)
                true
            }

            e.keyCode == KeyEvent.VK_PERIOD || e.keyChar == '.' -> {
                HelixActions.repeatLastMotion(editor, state.takeCount() ?: 1)
                true
            }

            !e.isShiftDown && (e.keyCode == KeyEvent.VK_D || e.keyChar == 'd') -> {
                HelixActions.deleteSelectionNoYank(editor, state.takeCount() ?: 1)
                true
            }

            !e.isShiftDown && (e.keyCode == KeyEvent.VK_C || e.keyChar == 'c') -> {
                HelixActions.changeSelectionNoYank(editor, state.takeCount() ?: 1)
                true
            }

            !e.isShiftDown && (e.keyCode == KeyEvent.VK_MINUS || e.keyChar == '-') -> {
                HelixActions.mergeAllSelections(editor)
                true
            }

            e.keyChar == 'J' || (e.isShiftDown && e.keyCode == KeyEvent.VK_J) -> {
                HelixActions.joinLines(editor, state.takeCount() ?: 1, selectSpace = true)
                true
            }

            e.keyChar == '*' || (e.isShiftDown && e.keyCode == KeyEvent.VK_8) -> {
                HelixKeyHandler.recordJump(editor)
                HelixActions.searchSelection(editor, detectWordBoundaries = false)
                true
            }

            e.keyChar == '_' || (e.isShiftDown && e.keyCode == KeyEvent.VK_MINUS) -> {
                HelixActions.mergeSelections(editor)
                true
            }

            e.keyChar == '(' || (e.isShiftDown && e.keyCode == KeyEvent.VK_9) -> {
                HelixActions.rotateSelectionsContents(editor, forward = false)
                true
            }

            e.keyChar == ')' || (e.isShiftDown && e.keyCode == KeyEvent.VK_0) -> {
                HelixActions.rotateSelectionsContents(editor, forward = true)
                true
            }

            !e.isShiftDown && (e.keyCode == KeyEvent.VK_X || e.keyChar == 'x') -> {
                HelixMotions.shrinkToLineBounds(editor)
                true
            }

            else -> false
        }
    }

    private fun handleAltAstShortcut(e: KeyEvent, editor: Editor): Boolean = when {
        !e.isShiftDown && (e.keyCode == KeyEvent.VK_P || e.keyCode == KeyEvent.VK_LEFT) -> {
            HelixActions.selectPrevSibling(editor)
            true
        }

        isSelectNextSibling(e) -> {
            HelixActions.selectNextSibling(editor)
            true
        }

        !e.isShiftDown && (e.keyCode == KeyEvent.VK_B || e.keyChar == 'b') -> {
            HelixActions.moveParentNodeStart(editor)
            true
        }

        !e.isShiftDown && (e.keyCode == KeyEvent.VK_E || e.keyChar == 'e') -> {
            HelixActions.moveParentNodeEnd(editor)
            true
        }

        !e.isShiftDown && (e.keyCode == KeyEvent.VK_A || e.keyChar == 'a') -> {
            HelixActions.selectAllSiblings(editor)
            true
        }

        e.isShiftDown && (e.keyCode == KeyEvent.VK_I || e.keyChar == 'I') -> {
            HelixActions.selectAllChildren(editor)
            true
        }

        else -> false
    }

    private fun handleAltShellShortcut(e: KeyEvent, editor: Editor): Boolean = when {
        e.keyChar == '!' || (e.isShiftDown && e.keyCode == KeyEvent.VK_1) -> {
            HelixSearchManager.startShellAppend(editor)
            true
        }

        e.keyChar == '|' || (e.isShiftDown && e.keyCode == KeyEvent.VK_BACK_SLASH) -> {
            HelixSearchManager.startShellPipeTo(editor)
            true
        }

        else -> false
    }

    private fun isSelectNextSibling(e: KeyEvent): Boolean =
        !e.isShiftDown && (e.keyCode == KeyEvent.VK_N || e.keyCode == KeyEvent.VK_RIGHT || e.keyChar == 'n')

    private fun findFocusedEditor(e: KeyEvent): Editor? {
        val allEditors = EditorFactory.getInstance().allEditors
        if (allEditors.isEmpty()) return null

        val component = e.component
        if (component != null) {
            val matching = allEditors.firstOrNull {
                it.contentComponent == component || SwingUtilities.isDescendingFrom(component, it.component)
            }
            if (matching != null) return matching
        }

        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner != null) {
            val matching = allEditors.firstOrNull {
                it.contentComponent == focusOwner || SwingUtilities.isDescendingFrom(focusOwner, it.component)
            }
            if (matching != null) return matching
        }

        return allEditors.firstOrNull { it.contentComponent.isFocusOwner }
    }

    companion object {
        private var installed = false

        @Synchronized
        fun install(parentDisposable: Disposable? = null) {
            if (installed) return
            installed = true
            val app = ApplicationManager.getApplication()
            val disposable = parentDisposable ?: app
            if (app != null) {
                IdeEventQueue.getInstance().addDispatcher(HelixEventDispatcher(), disposable)
            }
        }
    }
}
