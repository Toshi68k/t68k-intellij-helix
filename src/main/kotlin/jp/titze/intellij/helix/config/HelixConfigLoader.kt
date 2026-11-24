// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.config

import com.intellij.openapi.application.PathManager
import jp.titze.intellij.helix.action.HelixWriteIntentAction
import jp.titze.intellij.helix.keymap.HelixCommandRegistry
import jp.titze.intellij.helix.keymap.HelixKeymapConflictDetector
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.settings.HelixCliConfigFallbackMode
import jp.titze.intellij.helix.settings.HelixSettings
import java.io.File

data class HelixConfigResult(val file: File?, val bindingCount: Int, val errors: List<String>)

object HelixConfigLoader {

    const val INTELLIJ_CONFIG_FILE = "intellij.toml"
    const val CLI_CONFIG_FILE = "config.toml"
    const val PROJECT_CONFIG_DIR = ".helix"

    fun isCliConfigFile(file: File): Boolean = file.name.equals(CLI_CONFIG_FILE, ignoreCase = true)

    fun findProjectConfigFile(
        project: com.intellij.openapi.project.Project?,
        fallbackMode: HelixCliConfigFallbackMode = HelixSettings.instance.cliConfigFallbackMode,
    ): File? {
        if (project == null || project.isDisposed) return null
        val basePath = project.basePath ?: return null
        val helixDir = File(basePath, PROJECT_CONFIG_DIR)
        val intellijFile = File(helixDir, INTELLIJ_CONFIG_FILE)
        if (intellijFile.exists() && intellijFile.isFile) {
            return intellijFile
        }
        if (fallbackMode != HelixCliConfigFallbackMode.DISABLED) {
            val canonicalFile = File(helixDir, CLI_CONFIG_FILE)
            if (canonicalFile.exists() && canonicalFile.isFile) {
                return canonicalFile
            }
        }
        return null
    }

    fun getTargetProjectConfigFile(project: com.intellij.openapi.project.Project): File? {
        val basePath = project.basePath ?: return null
        return File(File(basePath, PROJECT_CONFIG_DIR), INTELLIJ_CONFIG_FILE)
    }

    fun openOrCreateProjectConfigFile(project: com.intellij.openapi.project.Project?): Boolean {
        if (project == null || project.isDisposed) return false
        val fileToOpen = getTargetProjectConfigFile(project) ?: return false
        return HelixWriteIntentAction.compute {
            try {
                fileToOpen.parentFile?.mkdirs()
                if (!fileToOpen.exists()) {
                    fileToOpen.writeText(getStarterProjectConfigTemplate())
                }
            } catch (_: java.io.IOException) {
                // Ignore creation failure
            }
            val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileToOpen)
                ?: return@compute false
            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, vf).navigate(true)
            true
        }
    }

    fun findConfigFile(
        fallbackMode: HelixCliConfigFallbackMode = HelixSettings.instance.cliConfigFallbackMode,
    ): File? {
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
            if (fallbackMode != HelixCliConfigFallbackMode.DISABLED) {
                val canonicalFile = File(userHome, ".config/helix/$CLI_CONFIG_FILE")
                if (canonicalFile.exists() && canonicalFile.isFile) {
                    return canonicalFile
                }
            }
        }

        val ideConfig = PathManager.getConfigPath()
        val ideIntellijFile = File(ideConfig, "helix/$INTELLIJ_CONFIG_FILE")
        if (ideIntellijFile.exists() && ideIntellijFile.isFile) {
            return ideIntellijFile
        }
        if (fallbackMode != HelixCliConfigFallbackMode.DISABLED) {
            val ideConfigFile = File(ideConfig, "helix/$CLI_CONFIG_FILE")
            if (ideConfigFile.exists() && ideConfigFile.isFile) {
                return ideConfigFile
            }
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
        return HelixWriteIntentAction.compute {
            try {
                fileToOpen.parentFile?.mkdirs()
                if (!fileToOpen.exists()) {
                    fileToOpen.writeText(getStarterConfigTemplate())
                }
            } catch (_: java.io.IOException) {
                // Ignore creation failure
            }
            val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileToOpen)
                ?: return@compute false
            if (project != null && !project.isDisposed) {
                com.intellij.openapi.fileEditor.OpenFileDescriptor(project, vf).navigate(true)
                true
            } else {
                false
            }
        }
    }

    fun isValidActionName(actionName: String): Boolean {
        val trimmed = actionName.trim()
        if (trimmed == "no_op" || trimmed.isEmpty()) return true
        if (trimmed.startsWith("@")) return true
        if (trimmed.startsWith(":action ") || trimmed.startsWith(":")) return true
        if (HelixCommandRegistry.get(trimmed) != null) return true
        val app = com.intellij.openapi.application.ApplicationManager.getApplication() ?: return true
        val am = app.getServiceIfCreated(com.intellij.openapi.actionSystem.ActionManager::class.java) ?: return true
        return am.getAction(trimmed) != null
    }

    fun validateConfig(content: String, allowEditorOptions: Boolean = true): HelixConfigResult {
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
        if (allowEditorOptions) {
            val overrides = HelixEditorOptions.evaluateTomlOptions(parseResult.editorOptions, null)
            for (override in overrides.values) {
                if (override.status == OptionStatus.INVALID) {
                    val msg = override.diagnosticMessage
                        ?: "Invalid value '${override.rawValueString}' for '${override.key}'"
                    diagnostics.add("Line ${override.lineNumber}: $msg")
                } else if (override.status == OptionStatus.IGNORED) {
                    diagnostics.add("Line ${override.lineNumber}: Unknown [editor] option '${override.key}' (ignored)")
                }
            }
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
            jp.titze.intellij.helix.settings.HelixEffectiveSettings.clearTomlOverrides()
            HelixKeymapManager.resetToDefaults()
            loadAllProjectConfigs()
            jp.titze.intellij.helix.settings.HelixEffectiveSettings.applyAllToAllEditors()
            jp.titze.intellij.helix.editor.HelixEditorEligibility.refreshAllEditors()
            return HelixConfigResult(null, 0, emptyList())
        }

        val content = try {
            file.readText()
        } catch (ex: java.io.IOException) {
            return HelixConfigResult(file, 0, listOf("Failed to read file: ${ex.message}"))
        }

        val shouldApplyEditorOptions = !isCliConfigFile(file) ||
            HelixSettings.instance.cliConfigFallbackMode == HelixCliConfigFallbackMode.FULL
        val validated = validateConfig(content, allowEditorOptions = shouldApplyEditorOptions)
        HelixKeymapManager.resetToDefaults()

        val parseResult = HelixTomlParser.parse(content)
        val overrides = if (shouldApplyEditorOptions) {
            HelixEditorOptions.evaluateTomlOptions(parseResult.editorOptions, file)
        } else {
            emptyMap()
        }
        jp.titze.intellij.helix.settings.HelixEffectiveSettings.setTomlOverrides(overrides)

        var boundCount = 0
        for (binding in parseResult.bindings) {
            applyBinding(binding)
            boundCount++
        }

        loadAllProjectConfigs()
        jp.titze.intellij.helix.settings.HelixEffectiveSettings.applyAllToAllEditors()
        jp.titze.intellij.helix.editor.HelixEditorEligibility.refreshAllEditors()

        return HelixConfigResult(file, boundCount, validated.errors)
    }

    fun isProjectTrusted(project: com.intellij.openapi.project.Project): Boolean = try {
        val clazz = Class.forName("com.intellij.ide.impl.TrustedProjects")
        val method = clazz.getMethod("isTrusted", com.intellij.openapi.project.Project::class.java)
        method.invoke(null, project) as? Boolean ?: true
    } catch (_: Throwable) {
        true
    }

    fun loadProjectConfig(
        project: com.intellij.openapi.project.Project,
        explicitFile: File? = null,
    ): HelixConfigResult {
        if (project.isDisposed || !isProjectTrusted(project)) {
            unloadProjectConfig(project)
            return HelixConfigResult(null, 0, emptyList())
        }

        val file = explicitFile ?: findProjectConfigFile(project)
        if (file == null || !file.exists()) {
            unloadProjectConfig(project)
            jp.titze.intellij.helix.settings.HelixEffectiveSettings.applyAllToProjectEditors(project)
            jp.titze.intellij.helix.editor.HelixEditorEligibility.refreshProjectEditors(project)
            return HelixConfigResult(null, 0, emptyList())
        }

        val content = try {
            file.readText()
        } catch (ex: java.io.IOException) {
            return HelixConfigResult(file, 0, listOf("Failed to read file: ${ex.message}"))
        }

        val shouldApplyEditorOptions = !isCliConfigFile(file) ||
            HelixSettings.instance.cliConfigFallbackMode == HelixCliConfigFallbackMode.FULL
        val validated = validateConfig(content, allowEditorOptions = shouldApplyEditorOptions)

        HelixKeymapManager.initProjectKeymap(project)

        val parseResult = HelixTomlParser.parse(content)
        val overrides = if (shouldApplyEditorOptions) {
            HelixEditorOptions.evaluateTomlOptions(parseResult.editorOptions, file)
        } else {
            emptyMap()
        }
        jp.titze.intellij.helix.settings.HelixEffectiveSettings.setProjectTomlOverrides(project, overrides)

        var boundCount = 0
        for (binding in parseResult.bindings) {
            applyBinding(binding, project)
            boundCount++
        }

        jp.titze.intellij.helix.settings.HelixEffectiveSettings.applyAllToProjectEditors(project)
        jp.titze.intellij.helix.editor.HelixEditorEligibility.refreshProjectEditors(project)

        return HelixConfigResult(file, boundCount, validated.errors)
    }

    fun unloadProjectConfig(project: com.intellij.openapi.project.Project) {
        HelixKeymapManager.clearProject(project)
        jp.titze.intellij.helix.settings.HelixEffectiveSettings.clearProjectOverrides(project)
    }

    fun loadAllProjectConfigs() {
        val projects = com.intellij.openapi.project.ProjectManager.getInstance().openProjects
        for (project in projects) {
            if (!project.isDisposed) {
                loadProjectConfig(project)
            }
        }
    }

    private fun applyBinding(binding: HelixParsedBinding, project: com.intellij.openapi.project.Project? = null) {
        val action = binding.action
        if (action is String) {
            HelixKeymapManager.bind(binding.mode, binding.sequence, action, project = project)
        } else if (action is List<*>) {
            val actionList = action.filterIsInstance<String>()
            val projectPrefix = if (project != null) "${project.name}:" else ""
            val macroId = "macro:$projectPrefix${binding.sequence}"
            HelixCommandRegistry.register(macroId, "Macro ($projectPrefix${binding.sequence})") { ed, cnt ->
                HelixCommandRegistry.executeList(actionList, ed, cnt)
            }
            HelixKeymapManager.bind(binding.mode, binding.sequence, macroId, project = project)
        }
    }

    fun getStarterProjectConfigTemplate(): String = """
        # Project-local Helix Keymap Configuration
        # Location: .helix/intellij.toml
        #
        # Dedicated to IntelliJ Helix and scoped to this repository.
        # Overrides global configuration (~/.config/helix/intellij.toml).

        # [editor]
        # rulers = [80]
        # soft-wrap.enable = true

        # [keys.normal]
        # "C-s" = ":action SaveAll"

        # [keys.space.t]
        # t = ":action Gradle.ExecuteTask"
    """.trimIndent()

    fun getStarterConfigTemplate(): String = """
        # IntelliJ Helix Keymap Configuration
        # Location: ~/.config/helix/intellij.toml
        #
        # Dedicated to IntelliJ Helix and safe from CLI Helix conflicts!
        # You can freely bind native IDE actions via ':action <ActionId>'.

        # Editor options
        # [editor]
        # line-number = "relative"
        # scrolloff = 5
        # cursorline = true
        # rulers = [80, 120]
        # color-modes = true
        #
        # [editor.cursor-shape]
        # normal = "block"
        # insert = "bar"
        # select = "underline"
        #
        # [editor.search]
        # smart-case = true
        # wrap-around = true

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
