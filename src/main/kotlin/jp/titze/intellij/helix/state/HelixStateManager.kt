package jp.titze.intellij.helix.state

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

object HelixStateManager {
    private val HELIX_STATE_KEY = Key.create<HelixEditorState>("HELIX_STATE_KEY")

    fun get(editor: Editor): HelixEditorState? = editor.getUserData(HELIX_STATE_KEY)

    fun getOrCreate(editor: Editor): HelixEditorState {
        var state = editor.getUserData(HELIX_STATE_KEY)
        if (state == null) {
            state = HelixEditorState(editor)
            editor.putUserData(HELIX_STATE_KEY, state)
            state.addListener { updateCursor(editor, it.mode) }
            updateCursor(editor, state.mode)
        }
        return state
    }

    fun updateCursor(editor: Editor, mode: HelixMode) {
        val settings = editor.settings
        when (mode) {
            HelixMode.NORMAL, HelixMode.SELECT -> {
                settings.isBlockCursor = true
            }

            HelixMode.INSERT -> {
                settings.isBlockCursor = false
            }
        }
    }
}
