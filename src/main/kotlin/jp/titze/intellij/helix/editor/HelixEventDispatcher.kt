package jp.titze.intellij.helix.editor

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.AWTEvent
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

class HelixEventDispatcher : IdeEventQueue.EventDispatcher {

    override fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent || e.id != KeyEvent.KEY_PRESSED) {
            return false
        }

        val editor = findFocusedEditor(e) ?: return false
        if (editor.isOneLineMode || editor.isViewer) return false

        val isCtrl = e.isControlDown && !e.isMetaDown && !e.isAltDown
        val isAlt = e.isAltDown && !e.isControlDown && !e.isMetaDown
        val isNoModifiers = !e.isControlDown && !e.isMetaDown && !e.isAltDown && !e.isShiftDown

        val isEscape = (isNoModifiers && e.keyCode == KeyEvent.VK_ESCAPE) ||
            (isCtrl && e.keyCode == KeyEvent.VK_OPEN_BRACKET)

        if (isEscape) {
            val lookup = com.intellij.codeInsight.lookup.LookupManager.getActiveLookup(editor)
            if (lookup != null) return false
            HelixEscapeHandler.handleEscape(editor)
            e.consume()
            return true
        }

        val state = HelixStateManager.getOrCreate(editor)

        if (isCtrl && e.keyCode == KeyEvent.VK_S && state.mode.isInsertable) {
            HelixActions.commitUndoCheckpoint(editor)
            e.consume()
            return true
        }

        if (state.mode.isInsertable) return false

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

    private fun handleCtrlShortcut(
        e: KeyEvent,
        editor: Editor,
        state: jp.titze.intellij.helix.state.HelixEditorState,
    ): Boolean = when (e.keyCode) {
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
    ): Boolean = when {
        e.keyCode == KeyEvent.VK_BACK_QUOTE || e.keyChar == '`' -> {
            HelixActions.toUpperCase(editor, state.takeCount() ?: 1)
            true
        }

        else -> false
    }

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
