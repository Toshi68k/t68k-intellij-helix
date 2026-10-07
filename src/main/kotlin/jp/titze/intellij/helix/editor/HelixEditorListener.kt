package jp.titze.intellij.helix.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import jp.titze.intellij.helix.state.HelixStateManager

class HelixEditorListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        HelixTypedActionHandler.install()
        HelixEditorActionHandler.install()
        val lifecycle = ApplicationManager.getApplication()?.getService(HelixPluginLifecycle::class.java)
        HelixEventDispatcher.install(lifecycle)
        if (HelixEditorEligibility.isActive(editor)) {
            HelixStateManager.getOrCreate(editor)
        }
    }
}
