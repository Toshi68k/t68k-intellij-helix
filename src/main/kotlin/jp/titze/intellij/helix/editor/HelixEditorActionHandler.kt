package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixStateManager

class HelixEditorActionHandler(
    private val actionId: String,
    private val originalHandler: EditorActionHandler?
) : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (editor.isOneLineMode || editor.isViewer) {
            originalHandler?.execute(editor, caret, dataContext)
            return
        }

        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            originalHandler?.execute(editor, caret, dataContext)
            return
        }

        // Non-insertable mode: do NOT modify buffer text
        when (actionId) {
            IdeActions.ACTION_EDITOR_BACKSPACE -> {
                if (state.hasCount) {
                    state.removeLastCountDigit()
                }
            }
            IdeActions.ACTION_EDITOR_ENTER -> {
                // In Helix, <ret> collapses selection and resets multi-caret
                HelixMotions.collapseSelection(editor)
                HelixMotions.keepOnlyPrimaryCaret(editor)
            }
            // For Delete or other editing actions in normal mode, do nothing
        }
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
        if (editor.isOneLineMode || editor.isViewer) {
            return originalHandler?.isEnabled(editor, caret, dataContext) ?: true
        }

        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode.isInsertable) {
            return originalHandler?.isEnabled(editor, caret, dataContext) ?: true
        }

        return when (actionId) {
            IdeActions.ACTION_EDITOR_BACKSPACE -> state.hasCount
            IdeActions.ACTION_EDITOR_ENTER -> true
            IdeActions.ACTION_EDITOR_DELETE -> false
            else -> false
        }
    }

    companion object {
        private var installed = false

        @Synchronized
        fun install() {
            if (installed) return
            installed = true

            val actionManager = EditorActionManager.getInstance()
            val actionsToWrap = listOf(
                IdeActions.ACTION_EDITOR_BACKSPACE,
                IdeActions.ACTION_EDITOR_DELETE,
                IdeActions.ACTION_EDITOR_ENTER
            )

            for (actionId in actionsToWrap) {
                val orig = actionManager.getActionHandler(actionId)
                actionManager.setActionHandler(actionId, HelixEditorActionHandler(actionId, orig))
            }
        }
    }
}
