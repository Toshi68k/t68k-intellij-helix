package jp.titze.intellij.helix.editor

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.state.HelixStateManager

class HelixTypedActionHandler(private val originalHandler: TypedActionHandler?) : TypedActionHandler {

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        if (editor.isOneLineMode || editor.isViewer) {
            originalHandler?.execute(editor, charTyped, dataContext)
            return
        }

        val state = HelixStateManager.getOrCreate(editor)

        if (!state.mode.isInsertable) {
            HelixKeyHandler.handleKey(charTyped, editor)
            return
        }

        originalHandler?.execute(editor, charTyped, dataContext)
    }

    companion object {
        private var installed = false

        @Synchronized
        fun install() {
            if (installed) return
            installed = true
            val typedAction = TypedAction.getInstance()
            val original = typedAction.rawHandler
            typedAction.setupRawHandler(HelixTypedActionHandler(original))
        }
    }
}
