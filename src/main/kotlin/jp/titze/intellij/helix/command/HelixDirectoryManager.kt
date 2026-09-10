package jp.titze.intellij.helix.command

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.wm.WindowManager
import java.io.File

object HelixDirectoryManager {

    @Volatile
    private var currentDirectory: String? = null

    @Volatile
    private var previousDirectory: String? = null

    fun reset() {
        currentDirectory = null
        previousDirectory = null
    }

    fun getCurrentDirectory(editor: Editor): String = currentDirectory
        ?: editor.project?.basePath
        ?: System.getProperty("user.dir")

    fun printWorkingDirectory(editor: Editor): String {
        val cwd = getCurrentDirectory(editor)
        val msg = "Working directory: $cwd"
        setStatus(editor, msg)
        return msg
    }

    fun changeDirectory(pathArg: String, editor: Editor): String {
        val trimmed = pathArg.trim()
        val defaultDir = editor.project?.basePath ?: System.getProperty("user.home")

        if (trimmed.isEmpty()) {
            return applyDirectoryChange(defaultDir, editor)
        }

        if (trimmed == "-") {
            val prev = previousDirectory
            if (prev == null) {
                val msg = "No previous directory"
                setStatus(editor, msg)
                return msg
            }
            return applyDirectoryChange(prev, editor)
        }

        val resolved = resolvePath(trimmed, getCurrentDirectory(editor))
        val target = File(resolved)
        if (!target.exists() || !target.isDirectory) {
            val msg = "Directory not found: $trimmed"
            setStatus(editor, msg)
            return msg
        }

        return applyDirectoryChange(target.canonicalPath, editor)
    }

    private fun resolvePath(input: String, cwd: String): String {
        val home = System.getProperty("user.home")
        val expanded = when {
            input == "~" -> home
            input.startsWith("~" + File.separator) -> home + input.substring(1)
            input.startsWith("~/") -> home + input.substring(1)
            else -> input
        }
        val file = File(expanded)
        return if (file.isAbsolute) {
            file.canonicalPath
        } else {
            File(cwd, expanded).canonicalPath
        }
    }

    private fun applyDirectoryChange(canonicalPath: String, editor: Editor): String {
        val current = getCurrentDirectory(editor)
        previousDirectory = current
        currentDirectory = canonicalPath
        val msg = "Working directory changed to: $canonicalPath"
        setStatus(editor, msg)
        return msg
    }

    private fun setStatus(editor: Editor, message: String) {
        val project = editor.project ?: return
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        statusBar?.info = message
    }
}
