package jp.titze.intellij.helix.editor

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import jp.titze.intellij.helix.action.HelixActionDelegate
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
        val isNoModifiers = !e.isControlDown && !e.isMetaDown && !e.isAltDown && !e.isShiftDown

        // Escape or Ctrl+[ should always work to exit insert/select mode or cancel pending actions
        val isEscape = (isNoModifiers && e.keyCode == KeyEvent.VK_ESCAPE) ||
                (isCtrl && e.keyCode == KeyEvent.VK_OPEN_BRACKET)

        if (isEscape) {
            val lookup = com.intellij.codeInsight.lookup.LookupManager.getActiveLookup(editor)
            if (lookup != null) {
                return false // Let IntelliJ close the completion popup first
            }
            HelixEscapeHandler.handleEscape(editor)
            e.consume()
            return true
        }

        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) return false

        val handled = when {
            isCtrl && e.keyCode == KeyEvent.VK_F -> {
                val count = state.takeCount() ?: 1
                HelixMotions.pageDown(editor, count)
                true
            }

            isCtrl && e.keyCode == KeyEvent.VK_B -> {
                val count = state.takeCount() ?: 1
                HelixMotions.pageUp(editor, count)
                true
            }

            isCtrl && e.keyCode == KeyEvent.VK_D -> {
                val count = state.takeCount() ?: 1
                HelixMotions.halfPageDown(editor, count)
                true
            }

            isCtrl && e.keyCode == KeyEvent.VK_U -> {
                val count = state.takeCount() ?: 1
                HelixMotions.halfPageUp(editor, count)
                true
            }

            isCtrl && e.keyCode == KeyEvent.VK_C -> {
                HelixActionDelegate.executeAction("CommentByLineComment", editor)
                true
            }

            isCtrl && e.keyCode == KeyEvent.VK_O -> {
                val count = state.takeCount() ?: 1
                val project = editor.project
                if (project != null) {
                    HelixJumpListService.getInstance(project).jumpBackward(editor, count)
                }
                true
            }

            isCtrl && e.keyCode == KeyEvent.VK_I -> {
                val count = state.takeCount() ?: 1
                val project = editor.project
                if (project != null) {
                    HelixJumpListService.getInstance(project).jumpForward(editor, count)
                }
                true
            }

            isCtrl && e.keyCode == KeyEvent.VK_S -> {
                state.clearCount()
                val project = editor.project
                if (project != null) {
                    HelixJumpListService.getInstance(project).recordCurrent(editor, force = true)
                }
                true
            }

            isNoModifiers && e.keyCode == KeyEvent.VK_PAGE_DOWN -> {
                val count = state.takeCount() ?: 1
                HelixMotions.pageDown(editor, count)
                true
            }

            isNoModifiers && e.keyCode == KeyEvent.VK_PAGE_UP -> {
                val count = state.takeCount() ?: 1
                HelixMotions.pageUp(editor, count)
                true
            }

            else -> false
        }

        if (handled) {
            e.consume()
            return true
        }

        return false
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
