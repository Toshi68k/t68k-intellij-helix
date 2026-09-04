package jp.titze.intellij.helix.editor

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import jp.titze.intellij.helix.state.HelixStateManager

class HelixEditorListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        HelixTypedActionHandler.install()
        HelixEditorActionHandler.install()
        HelixEventDispatcher.install()
        HelixStateManager.getOrCreate(editor)
    }
}
