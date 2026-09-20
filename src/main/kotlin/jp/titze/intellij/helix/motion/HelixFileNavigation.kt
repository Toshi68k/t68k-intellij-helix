package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import java.util.concurrent.ConcurrentHashMap

object HelixFileNavigation {

    private val alternateFileMap = ConcurrentHashMap<Project, VirtualFile>()
    private val recentFilesMap = ConcurrentHashMap<Project, MutableList<VirtualFile>>()
    private val modifiedFilesMap = ConcurrentHashMap<Project, MutableList<VirtualFile>>()

    fun reset() {
        alternateFileMap.clear()
        recentFilesMap.clear()
        modifiedFilesMap.clear()
    }

    fun recordFileAccess(project: Project, oldFile: VirtualFile?, newFile: VirtualFile?) {
        if (isValidAlternate(oldFile, newFile)) {
            alternateFileMap[project] = oldFile!!
        }

        val list = recentFilesMap.computeIfAbsent(project) { mutableListOf() }
        synchronized(list) {
            if (newFile != null && newFile.isValid) {
                list.remove(newFile)
                list.add(0, newFile)
            }
            if (oldFile != null && oldFile.isValid && !list.contains(oldFile)) {
                list.add(oldFile)
            }
        }
    }

    fun recordFileModified(project: Project, file: VirtualFile) {
        if (!file.isValid) return
        val list = modifiedFilesMap.computeIfAbsent(project) { mutableListOf() }
        synchronized(list) {
            list.remove(file)
            list.add(0, file)
        }
    }

    fun gotoLastAccessedFile(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val currentDoc = editor.document
        val currentFile = FileDocumentManager.getInstance().getFile(currentDoc)

        val target = resolveAlternateFile(project, currentFile)
        if (target == null) {
            showStatus(project, "No alternate file found")
            return false
        }

        if (currentFile != null && currentFile.isValid) {
            alternateFileMap[project] = currentFile
        }

        return openFile(project, target)
    }

    private fun resolveAlternateFile(project: Project, currentFile: VirtualFile?): VirtualFile? {
        val directAlt = alternateFileMap[project]
        if (directAlt != null && directAlt.isValid && directAlt != currentFile) {
            return directAlt
        }

        val recentList = recentFilesMap[project]
        if (recentList != null) {
            synchronized(recentList) {
                val candidate = recentList.firstOrNull { it.isValid && it != currentFile }
                if (candidate != null) return candidate
            }
        }

        val openFiles = FileEditorManager.getInstance(project).openFiles
        return openFiles.firstOrNull { it.isValid && it != currentFile }
    }

    fun gotoLastModifiedFile(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val currentDoc = editor.document
        val currentFile = FileDocumentManager.getInstance().getFile(currentDoc)

        val target = resolveLastModifiedFile(project, currentFile)
        if (target == null) {
            showStatus(project, "No modified file found")
            return false
        }

        return openFile(project, target)
    }

    private fun resolveLastModifiedFile(project: Project, currentFile: VirtualFile?): VirtualFile? {
        val sessionList = modifiedFilesMap[project]
        if (sessionList != null) {
            synchronized(sessionList) {
                val candidate = sessionList.firstOrNull { it.isValid && it != currentFile }
                if (candidate != null) return candidate
            }
        }

        val vcsTarget = queryVcsModifiedFile(project, currentFile)
        if (vcsTarget != null) return vcsTarget

        val openFiles = FileEditorManager.getInstance(project).openFiles
        val docManager = FileDocumentManager.getInstance()
        val unsaved = openFiles.firstOrNull { it.isValid && it != currentFile && docManager.isFileModified(it) }
        if (unsaved != null) return unsaved

        return openFiles.filter { it.isValid && it != currentFile }.maxByOrNull { it.timeStamp }
    }

    private fun queryVcsModifiedFile(project: Project, currentFile: VirtualFile?): VirtualFile? = try {
        val clm = ChangeListManager.getInstance(project)
        val affected = clm.affectedFiles
        affected.filter { it.isValid && it != currentFile }.maxByOrNull { it.timeStamp }
    } catch (_: Exception) {
        null
    }

    private fun openFile(project: Project, virtualFile: VirtualFile): Boolean {
        if (!virtualFile.isValid) return false
        val descriptor = OpenFileDescriptor(project, virtualFile)
        val opened = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        return opened != null
    }

    private fun showStatus(project: Project, message: String) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        statusBar?.info = message
    }

    private fun isValidAlternate(oldFile: VirtualFile?, newFile: VirtualFile?): Boolean {
        if (oldFile == null || newFile == null) return false
        return oldFile != newFile && oldFile.isValid
    }

    private fun isWordChar(c: Char): Boolean = !c.isWhitespace() && c !in "\"'`()<>{}[],"

    fun extractTargetText(editor: Editor): String? {
        val primary = editor.caretModel.primaryCaret
        if (primary.hasSelection()) {
            return primary.selectedText?.trim()
        }
        val doc = editor.document
        val text = doc.charsSequence
        val offset = primary.offset.coerceIn(0, (text.length - 1).coerceAtLeast(0))
        if (text.isEmpty()) return null

        var start = offset
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = offset
        while (end < text.length && isWordChar(text[end])) end++
        return if (start < end) text.substring(start, end) else null
    }

    fun gotoFileAtCaret(editor: Editor, explicitTarget: String? = null): Boolean {
        val project = editor.project ?: return false
        val textToFind = explicitTarget ?: extractTargetText(editor)

        if (!textToFind.isNullOrEmpty()) {
            val fileName = textToFind.substringAfterLast('/')
            val found = com.intellij.psi.search.FilenameIndex.getVirtualFilesByName(
                fileName,
                com.intellij.psi.search.GlobalSearchScope.projectScope(project),
            ).firstOrNull { it.path.endsWith(textToFind) || it.name == fileName }

            if (found != null && found.isValid) {
                return openFile(project, found)
            }
        }

        return HelixActionDelegate.executeAction("GotoDeclaration", editor)
    }

    fun gotoFileInSplit(editor: Editor, vertical: Boolean): Boolean {
        HelixKeyHandler.recordJump(editor)
        val targetText = extractTargetText(editor)
        val splitAction = if (vertical) "SplitVertically" else "SplitHorizontally"
        val splitSuccess = HelixActionDelegate.executeAction(splitAction, editor)

        if (HelixActionDelegate.actionExecutor != null) {
            val navSuccess = gotoFileAtCaret(editor, targetText)
            return splitSuccess || navSuccess
        }

        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val project = editor.project ?: return@invokeLater
            val currentEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: editor
            gotoFileAtCaret(currentEditor, targetText)
        }
        return splitSuccess
    }
}
