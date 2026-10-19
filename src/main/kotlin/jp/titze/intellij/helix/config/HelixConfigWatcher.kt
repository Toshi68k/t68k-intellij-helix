// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.WindowManager
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.settings.HelixSettings
import java.io.File

/**
 * Listens to VFS changes and automatically reloads keymaps when config.toml changes.
 */
class HelixConfigWatcher : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        if (!isEnabled) return

        val hasRelevantChange = events.any { isRelevantEvent(it) }
        if (!hasRelevantChange) return

        val now = System.currentTimeMillis()
        if (now - lastReloadTime < DEBOUNCE_MS) return
        lastReloadTime = now

        val result = HelixConfigLoader.loadConfig()
        notifyReloadResult(result)
        onReloadCallback?.invoke(result)
    }

    companion object {
        private const val DEBOUNCE_MS = 200L
        private const val CONFIG_FILE_NAME = "config.toml"

        @Volatile
        var isEnabled: Boolean = true

        @Volatile
        var explicitWatchedFile: File? = null

        @Volatile
        var lastReloadTime: Long = 0L

        @Volatile
        var onReloadCallback: ((HelixConfigResult) -> Unit)? = null

        fun isRelevantEvent(event: VFileEvent): Boolean = isMatchingConfigPath(event.path)

        fun isMatchingConfigPath(path: String): Boolean {
            if (!path.endsWith(CONFIG_FILE_NAME)) return false

            val candidate = canonicalOrAbsolute(File(path))

            val explicit = explicitWatchedFile
            if (explicit != null && candidate == canonicalOrAbsolute(explicit)) {
                return true
            }

            val custom = HelixSettings.instance.customConfigPath.trim()
            if (matchesFile(candidate, custom)) return true

            val userHome = System.getProperty("user.home")
            if (!userHome.isNullOrEmpty() && matchesFile(candidate, "$userHome/.config/helix/$CONFIG_FILE_NAME")) {
                return true
            }

            val ideConfig = PathManager.getConfigPath()
            return matchesFile(candidate, "$ideConfig/helix/$CONFIG_FILE_NAME")
        }

        private fun matchesFile(candidate: File, targetPath: String): Boolean {
            if (targetPath.isEmpty()) return false
            return candidate == canonicalOrAbsolute(File(targetPath))
        }

        private fun canonicalOrAbsolute(file: File): File = try {
            file.canonicalFile
        } catch (_: Exception) {
            file.absoluteFile
        }

        fun notifyReloadResult(result: HelixConfigResult) {
            val message = if (result.errors.isNotEmpty()) {
                HelixBundle.message("config.reloaded.error", result.errors.first())
            } else if (result.file != null) {
                HelixBundle.message("config.reloaded.success", result.bindingCount, result.file.name)
            } else {
                HelixBundle.message("config.reloaded.reset")
            }

            val app = ApplicationManager.getApplication() ?: return
            if (app.isDisposed) return

            app.invokeLater {
                val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
                if (project != null) {
                    WindowManager.getInstance().getStatusBar(project)?.info = message
                }
            }
        }
    }
}
