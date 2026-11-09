// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixShellActions
import jp.titze.intellij.helix.action.HelixVisualToggleActions
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.settings.HelixLineNavigationMode
import jp.titze.intellij.helix.settings.HelixScratchSplitMode
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.ui.HelixJumplistPopup
import jp.titze.intellij.helix.ui.HelixSearchManager
import java.io.File

data class HelixCommandItem(
    val name: String,
    val aliases: List<String>,
    val description: String,
    val action: (Editor) -> Unit,
) {
    val nameLower: String = name.lowercase()
    val aliasesLower: List<String> = aliases.map { it.lowercase() }
    val descriptionLower: String = description.lowercase()
    val nameWords: List<String> = nameLower.split('-', '_')
    val aliasWords: List<List<String>> = aliasesLower.map { it.split('-', '_') }
    val descriptionWords: List<String> = descriptionLower
        .split(' ', '(', ')', '/', ',', '.')
        .filter { it.isNotEmpty() }

    fun matches(query: String): Boolean = matchesNormalized(query.lowercase())

    internal fun matchesNormalized(q: String): Boolean {
        if (q.isEmpty()) return true
        if (nameLower.contains(q)) return true
        if (aliasesLower.any { it.contains(q) }) return true
        if (descriptionLower.contains(q)) return true
        return false
    }

    fun matchScore(query: String): Int = matchScoreNormalized(query.lowercase())

    internal fun matchScoreNormalized(q: String): Int {
        if (q.isEmpty()) return 0
        return scoreExactOrPrefix(q)
            ?: scoreWordMatch(q)
            ?: scoreSubstringMatch(q)
            ?: scoreDescriptionMatch(q)
            ?: Int.MAX_VALUE
    }

    private fun scoreExactOrPrefix(q: String): Int? {
        if (nameLower == q) return SCORE_EXACT_NAME
        if (aliasesLower.contains(q)) return SCORE_EXACT_ALIAS
        if (nameLower.startsWith(q)) return SCORE_PREFIX_NAME + name.length
        val prefixAlias = aliasesLower.firstOrNull { it.startsWith(q) }
        if (prefixAlias != null) return SCORE_PREFIX_ALIAS + prefixAlias.length
        return null
    }

    private fun scoreWordMatch(q: String): Int? {
        if (nameWords.any { it.startsWith(q) }) return SCORE_WORD_NAME + name.length
        val aliasIndex = aliasWords.indexOfFirst { words -> words.any { it.startsWith(q) } }
        if (aliasIndex >= 0) return SCORE_WORD_ALIAS + aliases[aliasIndex].length
        return null
    }

    private fun scoreSubstringMatch(q: String): Int? {
        val nameIndex = nameLower.indexOf(q)
        if (nameIndex >= 0) return SCORE_SUBSTRING_NAME + nameIndex + name.length
        var minIndex: Int? = null
        for (alias in aliasesLower) {
            val idx = alias.indexOf(q)
            if (idx >= 0) {
                minIndex = minOf(minIndex ?: Int.MAX_VALUE, idx)
            }
        }
        if (minIndex != null) return SCORE_SUBSTRING_ALIAS + minIndex
        return null
    }

    private fun scoreDescriptionMatch(q: String): Int? {
        if (descriptionWords.any { it.startsWith(q) }) return SCORE_DESC_WORD
        if (descriptionLower.contains(q)) return SCORE_DESC_SUBSTRING
        return null
    }

    val displayCommand: String
        get() = if (aliases.isNotEmpty()) ":$name (:${aliases.joinToString(", :")})" else ":$name"

    companion object {
        private const val SCORE_EXACT_NAME = 0
        private const val SCORE_EXACT_ALIAS = 10
        private const val SCORE_PREFIX_NAME = 20
        private const val SCORE_PREFIX_ALIAS = 30
        private const val SCORE_WORD_NAME = 40
        private const val SCORE_WORD_ALIAS = 50
        private const val SCORE_SUBSTRING_NAME = 60
        private const val SCORE_SUBSTRING_ALIAS = 70
        private const val SCORE_DESC_WORD = 80
        private const val SCORE_DESC_SUBSTRING = 90
    }
}

object HelixCommands {

    val COMMANDS = HelixBuiltinCommands.LIST

    fun filterCommands(query: String): List<HelixCommandItem> {
        val q = query.trim().removePrefix(":")
        if (q.isEmpty()) return COMMANDS
        val qLower = q.lowercase()
        return COMMANDS
            .mapNotNull { cmd ->
                if (!cmd.matchesNormalized(qLower)) return@mapNotNull null
                val score = cmd.matchScoreNormalized(qLower)
                if (score != Int.MAX_VALUE) cmd to score else null
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    fun execute(cmd: String, editor: Editor) {
        val cleanCmd = cmd.trim().removePrefix(":")

        val parts = cleanCmd.split(Regex("\\s+"), limit = 2)
        val baseCmd = parts[0].lowercase()
        val arg = if (parts.size > 1) parts[1].trim() else ""

        if (executeSpecialCommand(cleanCmd, baseCmd, arg, editor)) {
            return
        }

        if (baseCmd == "sort") {
            val tokens = arg.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val isReverse = tokens.any { it in listOf("-r", "--reverse", "reverse", "-rn", "-nr") }
            val isNumeric = tokens.any { it in listOf("-n", "--numeric", "numeric", "-rn", "-nr") }
            HelixActions.sortLines(editor, reverse = isReverse, numeric = isNumeric)
            return
        }
        if (baseCmd == "terminal") {
            HelixActionDelegate.executeAction("ActivateTerminalToolWindow", editor)
            return
        }
        if (baseCmd in listOf("open", "edit", "e", "file-picker", "file_picker")) {
            if (arg.isEmpty()) {
                HelixActionDelegate.executeAction("GotoFile", editor) ||
                    HelixActionDelegate.executeAction("SearchEverywhere", editor)
            } else {
                openFile(arg, editor)
            }
            return
        }
        if (baseCmd == "bn" || baseCmd == "buffer-next") {
            val count = arg.toIntOrNull() ?: 1
            repeat(count) { HelixActionDelegate.executeAction("NextTab", editor) }
            return
        }
        if (baseCmd == "bp" || baseCmd == "buffer-previous") {
            val count = arg.toIntOrNull() ?: 1
            repeat(count) { HelixActionDelegate.executeAction("PreviousTab", editor) }
            return
        }

        val matched = COMMANDS.firstOrNull {
            it.name.equals(cleanCmd, ignoreCase = true) || it.aliases.any { a -> a.equals(cleanCmd, ignoreCase = true) }
        }
        if (matched != null) {
            matched.action(editor)
            return
        }

        val targetLine = cleanCmd.toIntOrNull()
        if (targetLine != null && targetLine > 0) {
            val project = editor.project
            if (project != null) {
                HelixJumpListService.getInstance(project).recordCurrent(editor)
            }
            HelixMotions.moveFileStart(editor, targetLine)
            return
        }

        // Fallbacks for standard vim/helix commands
        executeFallbackCommand(cleanCmd, editor)
    }

    private fun executeFallbackCommand(cleanCmd: String, editor: Editor) {
        when (cleanCmd) {
            "jumps" -> HelixJumplistPopup.show(editor)

            "reg", "registers" -> jp.titze.intellij.helix.ui.HelixRegistersPopup.show(editor)

            "macro", "macros" -> jp.titze.intellij.helix.ui.HelixMacrosPopup.show(editor)

            "w", "write" -> HelixActionDelegate.executeAction("SaveAll", editor)

            "q", "quit" -> HelixActionDelegate.executeAction("CloseContent", editor)

            "wq", "x", "exit", "xit" -> {
                HelixActionDelegate.executeAction("SaveAll", editor)
                ApplicationManager.getApplication().invokeLater {
                    HelixActionDelegate.executeAction("CloseContent", editor)
                }
            }

            "u", "update" -> HelixActionDelegate.executeAction("SaveAll", editor)

            "yank-join", "yank_join" -> HelixActions.yankJoined(editor)

            "wa" -> HelixActionDelegate.executeAction("SaveAll", editor)

            "qa" -> HelixActionDelegate.executeAction("CloseAllEditors", editor)

            "vsp" -> HelixActionDelegate.executeAction("SplitVertically", editor)

            "sp" -> HelixActionDelegate.executeAction("SplitHorizontally", editor)

            "unsplit", "only" -> HelixActionDelegate.executeAction("UnsplitAll", editor)

            "wmaximize", "maximize" -> {
                HelixActionDelegate.executeAction("MaximizeEditorInSplit", editor) ||
                    HelixActionDelegate.executeAction("HideAllWindows", editor)
            }

            "close-split", "close", "clo" -> {
                HelixActionDelegate.executeAction("Unsplit", editor) ||
                    HelixActionDelegate.executeAction("CloseContent", editor)
            }

            "swap-split", "change-split-orientation" -> {
                HelixActionDelegate.executeAction("ChangeSplitOrientation", editor)
            }

            "format" -> HelixActionDelegate.executeAction("ReformatCode", editor)

            "fold" -> HelixActionDelegate.executeAction("CollapseRegion", editor)

            "unfold" -> HelixActionDelegate.executeAction("ExpandRegion", editor)

            "fold-all", "fold_all" -> HelixActionDelegate.executeAction("CollapseAllRegions", editor)

            "unfold-all", "unfold_all" -> HelixActionDelegate.executeAction("ExpandAllRegions", editor)

            "b", "buffer" -> HelixActionDelegate.executeAction("RecentFiles", editor)

            "find" -> HelixActionDelegate.executeAction("FindInPath", editor)

            "bc", "bclose", "buffer-close" -> HelixActionDelegate.executeAction("CloseContent", editor)

            "bco", "bcloseother", "buffer-close-others" ->
                HelixActionDelegate.executeAction("CloseAllEditorsButActive", editor)

            "bca", "bcloseall", "buffer-close-all" -> HelixActionDelegate.executeAction("CloseAllEditors", editor)

            "bn", "buffer-next" -> HelixActionDelegate.executeAction("NextTab", editor)

            "bp", "buffer-previous" -> HelixActionDelegate.executeAction("PreviousTab", editor)

            "n", "new" -> HelixActionDelegate.executeAction("NewScratchFile", editor)

            "set search-ui=inline", "set search-ui=stock" -> {
                HelixSettings.instance.searchUiMode = HelixSearchUiMode.STOCK_HELIX
            }

            "set search-ui=popup" -> {
                HelixSettings.instance.searchUiMode = HelixSearchUiMode.POPUP
            }

            "toggle-search-ui", "search-ui" -> {
                val current = HelixSettings.instance.searchUiMode
                val next =
                    if (current ==
                        HelixSearchUiMode.STOCK_HELIX
                    ) {
                        HelixSearchUiMode.POPUP
                    } else {
                        HelixSearchUiMode.STOCK_HELIX
                    }
                HelixSettings.instance.searchUiMode = next
            }

            "set line-nav=helix", "set line-nav=stock" -> {
                HelixSettings.instance.lineNavigationMode = HelixLineNavigationMode.HELIX_STANDARD
            }

            "set line-nav=vim", "set line-nav=physical" -> {
                HelixSettings.instance.lineNavigationMode = HelixLineNavigationMode.VIM_STANDARD
            }

            "toggle-line-nav", "toggle-line-navigation", "line-nav" -> {
                val current = HelixSettings.instance.lineNavigationMode
                val next = if (current == HelixLineNavigationMode.HELIX_STANDARD) {
                    HelixLineNavigationMode.VIM_STANDARD
                } else {
                    HelixLineNavigationMode.HELIX_STANDARD
                }
                HelixSettings.instance.lineNavigationMode = next
            }

            "set scratch-mode=interactive", "set scratch-mode=file" -> {
                HelixSettings.instance.scratchSplitMode = HelixScratchSplitMode.INTERACTIVE
            }

            "set scratch-mode=buffer", "set scratch-mode=plain",
            "set scratch-mode=helix", "set scratch-mode=empty",
            -> {
                HelixSettings.instance.scratchSplitMode = HelixScratchSplitMode.EMPTY_BUFFER
            }

            "toggle-scratch-mode", "toggle-scratch", "scratch-mode" -> {
                val current = HelixSettings.instance.scratchSplitMode
                val next = if (current == HelixScratchSplitMode.INTERACTIVE) {
                    HelixScratchSplitMode.EMPTY_BUFFER
                } else {
                    HelixScratchSplitMode.INTERACTIVE
                }
                HelixSettings.instance.scratchSplitMode = next
            }

            "diffget", "diffg", "reset-diff-change", "reset_diff_change" -> {
                HelixActionDelegate.executeAction("Vcs.RollbackChangedLines", editor)
            }

            "reflow", "reflow-paragraph", "fill-paragraph" -> {
                HelixActionDelegate.executeAction("FillParagraph", editor)
            }

            "file-explorer-buffer", "file_explorer_buffer" -> {
                HelixActionDelegate.executeAction("SelectInProjectView", editor)
            }

            "yank-diagnostic", "yank_diagnostic", "yank-diag", "yank_diag" -> {
                HelixActions.yankDiagnostic(editor)
            }

            "reverse-selection-contents", "reverse_selection_contents" -> {
                HelixActions.reverseSelectionsContents(editor)
            }
        }
    }

    private fun openFile(path: String, editor: Editor) {
        val project = editor.project ?: return
        val resolved = HelixDirectoryManager.resolvePath(
            path,
            HelixDirectoryManager.getCurrentDirectory(editor),
        )
        val targetFile = File(resolved)
        if (!targetFile.exists()) {
            try {
                targetFile.parentFile?.mkdirs()
                targetFile.createNewFile()
            } catch (_: Exception) {
                // Ignore creation failure and let virtual file system handle
            }
        }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)
        if (virtualFile != null) {
            OpenFileDescriptor(project, virtualFile).navigate(true)
        }
    }

    private fun executeSpecialCommand(cleanCmd: String, baseCmd: String, arg: String, editor: Editor): Boolean {
        if (cleanCmd.startsWith("!")) {
            val shellCmd = cleanCmd.removePrefix("!").trim()
            if (shellCmd.isEmpty()) {
                HelixSearchManager.startShellInsert(editor)
            } else {
                HelixShellActions.runShellCommand(editor, shellCmd)
            }
            return true
        }
        if (baseCmd == "cd") {
            HelixDirectoryManager.changeDirectory(arg, editor)
            return true
        }
        if (baseCmd == "pwd") {
            HelixDirectoryManager.printWorkingDirectory(editor)
            return true
        }
        if (baseCmd == "clear-register" || baseCmd == "clear_register") {
            val regChar = if (arg.isNotEmpty()) arg.first() else null
            HelixRegisterManager.clear(regChar)
            return true
        }
        if (baseCmd in listOf("set-option", "set") && arg.isNotEmpty()) {
            executeSetOption(arg, editor)
            return true
        }
        if (baseCmd in listOf("toggle-option", "toggle") && arg.isNotEmpty()) {
            executeToggleOption(arg, editor)
            return true
        }
        return executeShellCommand(baseCmd, arg, editor)
    }

    private fun executeSetOption(arg: String, editor: Editor): Boolean {
        val (key, value) = parseOptionKeyValue(arg) ?: return false
        if (HelixVisualToggleActions.handleSetOption(key, value, editor)) {
            return true
        }
        return executeSettingOption(key, value)
    }

    private fun executeToggleOption(arg: String, editor: Editor): Boolean {
        val key = arg.trim()
        if (HelixVisualToggleActions.handleToggleOption(key, editor)) {
            return true
        }
        return executeSettingToggle(key)
    }

    private fun parseOptionKeyValue(rawArg: String): Pair<String, String>? {
        val trimmed = rawArg.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.contains('=')) {
            val parts = trimmed.split('=', limit = 2)
            parts[0].trim() to parts[1].trim()
        } else {
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
    }

    private fun executeSettingOption(key: String, value: String): Boolean {
        val normKey = key.lowercase().replace('_', '-')
        val normVal = value.lowercase()
        return when (normKey) {
            "search-ui", "search-ui.mode", "search.ui" -> {
                if (normVal in listOf("inline", "stock")) {
                    HelixSettings.instance.searchUiMode = HelixSearchUiMode.STOCK_HELIX
                    true
                } else if (normVal == "popup") {
                    HelixSettings.instance.searchUiMode = HelixSearchUiMode.POPUP
                    true
                } else {
                    false
                }
            }

            "line-nav", "line-navigation", "line-navigation.mode" -> {
                if (normVal in listOf("helix", "stock")) {
                    HelixSettings.instance.lineNavigationMode = HelixLineNavigationMode.HELIX_STANDARD
                    true
                } else if (normVal in listOf("vim", "physical")) {
                    HelixSettings.instance.lineNavigationMode = HelixLineNavigationMode.VIM_STANDARD
                    true
                } else {
                    false
                }
            }

            "scratch-mode", "scratch.mode" -> {
                if (normVal in listOf("interactive", "file")) {
                    HelixSettings.instance.scratchSplitMode = HelixScratchSplitMode.INTERACTIVE
                    true
                } else if (normVal in listOf("buffer", "plain", "helix", "empty")) {
                    HelixSettings.instance.scratchSplitMode = HelixScratchSplitMode.EMPTY_BUFFER
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    private fun executeSettingToggle(key: String): Boolean {
        val normKey = key.lowercase().replace('_', '-')
        return when (normKey) {
            "search-ui", "search-ui.mode", "search.ui" -> {
                val current = HelixSettings.instance.searchUiMode
                HelixSettings.instance.searchUiMode = if (current == HelixSearchUiMode.STOCK_HELIX) {
                    HelixSearchUiMode.POPUP
                } else {
                    HelixSearchUiMode.STOCK_HELIX
                }
                true
            }

            "line-nav", "line-navigation", "line-navigation.mode" -> {
                val current = HelixSettings.instance.lineNavigationMode
                HelixSettings.instance.lineNavigationMode = if (current == HelixLineNavigationMode.HELIX_STANDARD) {
                    HelixLineNavigationMode.VIM_STANDARD
                } else {
                    HelixLineNavigationMode.HELIX_STANDARD
                }
                true
            }

            "scratch-mode", "scratch.mode" -> {
                val current = HelixSettings.instance.scratchSplitMode
                HelixSettings.instance.scratchSplitMode = if (current == HelixScratchSplitMode.INTERACTIVE) {
                    HelixScratchSplitMode.EMPTY_BUFFER
                } else {
                    HelixScratchSplitMode.INTERACTIVE
                }
                true
            }

            else -> false
        }
    }

    private fun executeShellCommand(baseCmd: String, arg: String, editor: Editor): Boolean = when (baseCmd) {
        "pipe", "shell-pipe", "shell_pipe" -> {
            if (arg.isEmpty()) {
                HelixSearchManager.startShellPipe(editor)
            } else {
                HelixShellActions.pipeSelections(editor, arg)
            }
            true
        }

        "pipe-to", "shell-pipe-to", "shell_pipe_to" -> {
            if (arg.isEmpty()) {
                HelixSearchManager.startShellPipeTo(editor)
            } else {
                HelixShellActions.pipeToSelections(editor, arg)
            }
            true
        }

        "insert-output", "insert_output", "shell_insert_output" -> {
            if (arg.isEmpty()) {
                HelixSearchManager.startShellInsert(editor)
            } else {
                HelixShellActions.insertOutput(editor, arg, append = false)
            }
            true
        }

        "append-output", "append_output", "shell_append_output" -> {
            if (arg.isEmpty()) {
                HelixSearchManager.startShellAppend(editor)
            } else {
                HelixShellActions.insertOutput(editor, arg, append = true)
            }
            true
        }

        "keep-pipe", "keep_pipe", "shell_keep_pipe" -> {
            if (arg.isEmpty()) {
                HelixSearchManager.startShellKeepPipe(editor)
            } else {
                HelixShellActions.keepPipeSelections(editor, arg)
            }
            true
        }

        "sh", "run-shell-command", "run_shell_command" -> {
            if (arg.isEmpty()) {
                HelixActionDelegate.executeAction("ActivateTerminalToolWindow", editor)
            } else {
                HelixShellActions.runShellCommand(editor, arg)
            }
            true
        }

        else -> false
    }
}
