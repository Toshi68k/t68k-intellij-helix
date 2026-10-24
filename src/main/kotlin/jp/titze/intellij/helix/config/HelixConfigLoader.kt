// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.config

import com.intellij.openapi.application.PathManager
import jp.titze.intellij.helix.keymap.HelixCommandRegistry
import jp.titze.intellij.helix.keymap.HelixKeymapConflictDetector
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.settings.HelixSettings
import java.io.File

data class HelixConfigResult(val file: File?, val bindingCount: Int, val errors: List<String>)

object HelixConfigLoader {

    const val INTELLIJ_CONFIG_FILE = "intellij.toml"
    const val CLI_CONFIG_FILE = "config.toml"

    fun isCliConfigFile(file: File): Boolean = file.name.equals(CLI_CONFIG_FILE, ignoreCase = true)

    fun findConfigFile(): File? {
        val customPath = HelixSettings.instance.customConfigPath.trim()
        if (customPath.isNotEmpty()) {
            val customFile = File(customPath)
            if (customFile.exists() && customFile.isFile) {
                return customFile
            }
        }

        val userHome = System.getProperty("user.home")
        if (!userHome.isNullOrEmpty()) {
            val intellijFile = File(userHome, ".config/helix/$INTELLIJ_CONFIG_FILE")
            if (intellijFile.exists() && intellijFile.isFile) {
                return intellijFile
            }
            val canonicalFile = File(userHome, ".config/helix/$CLI_CONFIG_FILE")
            if (canonicalFile.exists() && canonicalFile.isFile) {
                return canonicalFile
            }
        }

        val ideConfig = PathManager.getConfigPath()
        val ideIntellijFile = File(ideConfig, "helix/$INTELLIJ_CONFIG_FILE")
        if (ideIntellijFile.exists() && ideIntellijFile.isFile) {
            return ideIntellijFile
        }
        val ideConfigFile = File(ideConfig, "helix/$CLI_CONFIG_FILE")
        if (ideConfigFile.exists() && ideConfigFile.isFile) {
            return ideConfigFile
        }

        return null
    }

    fun getTargetConfigFile(): File {
        val customPath = HelixSettings.instance.customConfigPath.trim()
        if (customPath.isNotEmpty()) {
            return File(customPath)
        }
        val userHome = System.getProperty("user.home")
        return if (!userHome.isNullOrEmpty()) {
            File(userHome, ".config/helix/$INTELLIJ_CONFIG_FILE")
        } else {
            val ideConfig = PathManager.getConfigPath()
            File(ideConfig, "helix/$INTELLIJ_CONFIG_FILE")
        }
    }

    fun openOrCreateConfigFile(project: com.intellij.openapi.project.Project?): Boolean {
        val fileToOpen = getTargetConfigFile()
        try {
            fileToOpen.parentFile?.mkdirs()
            if (!fileToOpen.exists()) {
                fileToOpen.writeText(getStarterConfigTemplate())
            }
        } catch (_: java.io.IOException) {
            // Ignore creation failure
        }
        val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileToOpen)
            ?: return false
        if (project != null && !project.isDisposed) {
            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, vf).navigate(true)
            return true
        }
        return false
    }

    fun isValidActionName(actionName: String): Boolean {
        val trimmed = actionName.trim()
        if (trimmed == "no_op" || trimmed.isEmpty()) return true
        if (trimmed.startsWith(":action ") || trimmed.startsWith(":")) return true
        if (HelixCommandRegistry.get(trimmed) != null) return true
        val app = com.intellij.openapi.application.ApplicationManager.getApplication() ?: return true
        val am = app.getServiceIfCreated(com.intellij.openapi.actionSystem.ActionManager::class.java) ?: return true
        return am.getAction(trimmed) != null
    }

    fun validateConfig(content: String): HelixConfigResult {
        val parseResult = HelixTomlParser.parse(content)
        val diagnostics = mutableListOf<String>()
        diagnostics.addAll(parseResult.errors)
        for (binding in parseResult.bindings) {
            validateBinding(binding, diagnostics)
        }
        val conflicts = HelixKeymapConflictDetector.detectConflictsInBindings(parseResult.bindings)
        for (conflict in conflicts) {
            diagnostics.add("Conflict: ${conflict.description}")
        }
        return HelixConfigResult(null, parseResult.bindings.size, diagnostics)
    }

    private fun validateBinding(binding: HelixParsedBinding, diagnostics: MutableList<String>) {
        val action = binding.action
        if (action is String) {
            if (!isValidActionName(action)) {
                diagnostics.add("Unknown command '$action' for key '${binding.sequence}'")
            }
        } else if (action is List<*>) {
            for (item in action) {
                if (item is String && !isValidActionName(item)) {
                    diagnostics.add("Unknown command '$item' in macro for key '${binding.sequence}'")
                }
            }
        }
    }

    fun loadConfig(explicitFile: File? = null): HelixConfigResult {
        val file = explicitFile ?: findConfigFile()
        if (file == null || !file.exists()) {
            HelixKeymapManager.resetToDefaults()
            return HelixConfigResult(null, 0, emptyList())
        }

        val content = try {
            file.readText()
        } catch (ex: java.io.IOException) {
            return HelixConfigResult(file, 0, listOf("Failed to read file: ${ex.message}"))
        }

        val validated = validateConfig(content)
        HelixKeymapManager.resetToDefaults()

        val parseResult = HelixTomlParser.parse(content)
        var boundCount = 0
        for (binding in parseResult.bindings) {
            applyBinding(binding)
            boundCount++
        }

        return HelixConfigResult(file, boundCount, validated.errors)
    }

    private fun applyBinding(binding: HelixParsedBinding) {
        val action = binding.action
        if (action is String) {
            HelixKeymapManager.bind(binding.mode, binding.sequence, action)
        } else if (action is List<*>) {
            val actionList = action.filterIsInstance<String>()
            val macroId = "macro:${binding.sequence}"
            HelixCommandRegistry.register(macroId, "Macro (${binding.sequence})") { ed, cnt ->
                HelixCommandRegistry.executeList(actionList, ed, cnt)
            }
            HelixKeymapManager.bind(binding.mode, binding.sequence, macroId)
        }
    }

    fun getStarterConfigTemplate(): String = """
        # IntelliJ Helix Keymap Configuration
        # Location: ~/.config/helix/intellij.toml
        #
        # Dedicated to IntelliJ Helix and safe from CLI Helix conflicts!
        # You can freely bind native IDE actions via ':action <ActionId>'.

        [keys.normal]
        "C-s" = ":w"
        "C-/" = "toggle_comments"

        # Leader shortcuts (Space)
        [keys.space]
        t = ":action ActivateTerminalToolWindow"

        # Multi-command macros
        [keys.normal]
        "C-d" = ["half_page_down", "align_view_center"]

        [keys.insert]
        "C-h" = "delete_char_backward"
    """.trimIndent()
}
