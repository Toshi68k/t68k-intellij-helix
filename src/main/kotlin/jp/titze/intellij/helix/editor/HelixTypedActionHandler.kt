package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixTypedActionHandler(private val originalHandler: TypedActionHandler?) : TypedActionHandler {

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        if (!HelixEditorEligibility.isActive(editor)) {
            originalHandler?.execute(editor, charTyped, dataContext)
            return
        }

        val state = HelixStateManager.getOrCreate(editor)

        if (!state.mode.isInsertable) {
            HelixKeyHandler.handleKey(charTyped, editor)
            return
        }

        if (state.pendingSequence == "C-r") {
            state.clearPendingSequence()
            HelixWhichKeyPopup.hide()
            HelixActions.insertRegister(editor, charTyped)
            return
        }

        HelixInsertTracker.recordChar(charTyped)
        originalHandler?.execute(editor, charTyped, dataContext)
    }

    companion object {
        private var installed = false
        private var originalRawHandler: TypedActionHandler? = null

        @Synchronized
        fun install() {
            if (installed) return
            installed = true
            val typedAction = TypedAction.getInstance()
            originalRawHandler = typedAction.rawHandler
            typedAction.setupRawHandler(HelixTypedActionHandler(originalRawHandler))
        }

        @Synchronized
        fun uninstall() {
            if (!installed) return
            val typedAction = TypedAction.getInstance()
            val current = typedAction.rawHandler
            if (current is HelixTypedActionHandler) {
                val target = originalRawHandler ?: current.originalHandler
                if (target != null) {
                    typedAction.setupRawHandler(target)
                }
            }
            originalRawHandler = null
            installed = false
        }
    }
}
