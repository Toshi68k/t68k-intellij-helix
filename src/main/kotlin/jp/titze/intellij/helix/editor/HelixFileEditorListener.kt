package jp.titze.intellij.helix.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.motion.HelixFileNavigation
import jp.titze.intellij.helix.motion.HelixJumpToWord
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixFileEditorListener : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        HelixWhichKeyPopup.hide()

        HelixFileNavigation.recordFileAccess(event.manager.project, event.oldFile, event.newFile)

        (event.oldEditor as? TextEditor)?.editor?.let { oldEditor ->
            HelixJumpToWord.cancel(oldEditor)
            HelixPromptBar.cancelActivePrompt(oldEditor)
        }

        val newEditor = (event.newEditor as? TextEditor)?.editor ?: return
        handleEditorActivated(newEditor)
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        HelixFileNavigation.recordFileAccess(source.project, null, file)

        val editor = (source.getSelectedEditor(file) as? TextEditor)?.editor
            ?: source.getEditors(file).filterIsInstance<TextEditor>().firstOrNull()?.editor
            ?: return
        handleEditorActivated(editor)
    }

    private fun handleEditorActivated(editor: com.intellij.openapi.editor.Editor) {
        if (editor.isOneLineMode || editor.isViewer) return

        if (HelixSettings.instance.resetToNormalOnTabSwitch) {
            val state = HelixStateManager.getOrCreate(editor)
            if (state.mode != HelixMode.NORMAL) {
                HelixActions.enterNormalMode(editor)
            }
        }

        val state = HelixStateManager.getOrCreate(editor)
        HelixStateManager.updateCursor(editor, state.mode)
    }
}
