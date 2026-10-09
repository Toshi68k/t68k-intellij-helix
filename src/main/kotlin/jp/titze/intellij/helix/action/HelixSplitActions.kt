package jp.titze.intellij.helix.action

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import jp.titze.intellij.helix.settings.HelixScratchSplitMode
import jp.titze.intellij.helix.settings.HelixSettings
import java.util.concurrent.atomic.AtomicBoolean

object HelixSplitActions {

    fun getScratchAction(): String = when (HelixSettings.instance.scratchSplitMode) {
        HelixScratchSplitMode.INTERACTIVE -> "NewScratchFile"
        HelixScratchSplitMode.EMPTY_BUFFER -> "NewScratchBuffer"
    }

    fun openScratchSplit(editor: Editor, vertical: Boolean): Boolean {
        val project = editor.project ?: return false
        val originalFile = FileDocumentManager.getInstance().getFile(editor.document)
        val splitAction = if (vertical) "SplitVertically" else "SplitHorizontally"
        val scratchAction = getScratchAction()

        val splitSuccess = HelixActionDelegate.executeAction(splitAction, editor)
        if (!splitSuccess) return false

        if (HelixActionDelegate.actionExecutor != null) {
            HelixActionDelegate.executeAction(scratchAction, editor)
            return true
        }

        if (originalFile == null) {
            HelixActionDelegate.executeAction(scratchAction, editor)
            return true
        }

        scheduleScratchOpenAndCleanup(project, originalFile, editor, scratchAction)
        return true
    }

    internal fun scheduleScratchOpenAndCleanup(
        project: Project,
        originalFile: VirtualFile,
        editor: Editor,
        scratchAction: String = getScratchAction(),
    ) {
        val cleanedUp = AtomicBoolean(false)
        val connection = project.messageBus.connect()

        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    handleFileOpened(event.newFile)
                }

                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    handleFileOpened(file)
                }

                private fun handleFileOpened(newFile: VirtualFile?) {
                    if (newFile != null && newFile != originalFile && !cleanedUp.getAndSet(true)) {
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                val manager = FileEditorManagerEx.getInstanceEx(project)
                                val window = manager.currentWindow
                                window?.closeFile(originalFile)
                            } finally {
                                connection.disconnect()
                            }
                        }
                    }
                }
            },
        )

        ApplicationManager.getApplication().invokeLater {
            HelixActionDelegate.executeAction(scratchAction, editor)
        }
    }
}
