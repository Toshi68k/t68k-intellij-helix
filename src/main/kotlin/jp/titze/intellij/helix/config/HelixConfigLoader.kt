// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.config

import com.intellij.openapi.application.PathManager
import jp.titze.intellij.helix.keymap.HelixCommandRegistry
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.settings.HelixSettings
import java.io.File

data class HelixConfigResult(val file: File?, val bindingCount: Int, val errors: List<String>)

object HelixConfigLoader {

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
            val canonicalFile = File(userHome, ".config/helix/config.toml")
            if (canonicalFile.exists() && canonicalFile.isFile) {
                return canonicalFile
            }
        }

        val ideConfig = PathManager.getConfigPath()
        val ideConfigFile = File(ideConfig, "helix/config.toml")
        if (ideConfigFile.exists() && ideConfigFile.isFile) {
            return ideConfigFile
        }

        return null
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

        val parseResult = HelixTomlParser.parse(content)
        HelixKeymapManager.resetToDefaults()

        var boundCount = 0
        for (binding in parseResult.bindings) {
            applyBinding(binding)
            boundCount++
        }

        return HelixConfigResult(file, boundCount, parseResult.errors)
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
        # Helix Keymap Configuration for IntelliJ
        # Location: ~/.config/helix/config.toml

        [keys.normal]
        "C-s" = ":w"
        "C-/" = "toggle_comments"
        
        # Leader shortcuts (Space)
        [keys.normal.space]
        t = ":action ActivateTerminalToolWindow"
        
        # Multi-command macros
        [keys.normal]
        "C-d" = ["half_page_down", "align_view_center"]

        [keys.insert]
        "C-h" = "delete_char_backward"
    """.trimIndent()
}
