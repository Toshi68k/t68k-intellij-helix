package jp.titze.intellij.helix.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.ui.HelixJumplistPopup

data class HelixCommandItem(
    val name: String,
    val aliases: List<String>,
    val description: String,
    val action: (Editor) -> Unit,
) {
    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        if (name.lowercase().contains(q)) return true
        if (aliases.any { it.lowercase().contains(q) }) return true
        if (description.lowercase().contains(q)) return true
        return false
    }

    val displayCommand: String
        get() = if (aliases.isNotEmpty()) ":$name (:${aliases.joinToString(", :")})" else ":$name"
}

object HelixCommands {

    val COMMANDS = listOf(
        HelixCommandItem("write", listOf("w"), "Save all modified files") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
        },
        HelixCommandItem("quit", listOf("q"), "Close active editor tab") { editor ->
            HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem("write-quit", listOf("wq", "x"), "Save all and close tab") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
            HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem("write-all", listOf("wa"), "Save all modified files") { editor ->
            HelixActionDelegate.executeAction("SaveAll", editor)
        },
        HelixCommandItem("quit-all", listOf("qa"), "Close all editor tabs") { editor ->
            HelixActionDelegate.executeAction("CloseAllEditors", editor)
        },
        HelixCommandItem("cquit", listOf("cq"), "Close active editor tab") { editor ->
            HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem("vsplit", listOf("vsp"), "Split editor vertically") { editor ->
            HelixActionDelegate.executeAction("SplitVertically", editor)
        },
        HelixCommandItem("hsplit", listOf("sp"), "Split editor horizontally") { editor ->
            HelixActionDelegate.executeAction("SplitHorizontally", editor)
        },
        HelixCommandItem("format", emptyList(), "Format buffer using IDE code formatter") { editor ->
            HelixActionDelegate.executeAction("ReformatCode", editor)
        },
        HelixCommandItem("earlier", emptyList(), "Undo earlier changes") { editor ->
            HelixActionDelegate.executeAction("\$Undo", editor)
        },
        HelixCommandItem("later", emptyList(), "Redo later changes") { editor ->
            HelixActionDelegate.executeAction("\$Redo", editor)
        },
        HelixCommandItem("reload", emptyList(), "Synchronize / reload buffer from disk") { editor ->
            HelixActionDelegate.executeAction("Synchronize", editor)
        },
        HelixCommandItem("config-reload", emptyList(), "Reload Helix configuration") { _ ->
        },
        HelixCommandItem("toggle-search-ui", emptyList(), "Toggle search UI (Stock Helix / Popup)") { _ ->
            HelixSettings.instance.searchUiMode =
                if (HelixSettings.instance.searchUiMode == HelixSearchUiMode.STOCK_HELIX) {
                    HelixSearchUiMode.POPUP
                } else {
                    HelixSearchUiMode.STOCK_HELIX
                }
        },
        HelixCommandItem("set-search-ui-stock", emptyList(), "Set search UI to Stock Helix inline bar") { _ ->
            HelixSettings.instance.searchUiMode = HelixSearchUiMode.STOCK_HELIX
        },
        HelixCommandItem("set-search-ui-popup", emptyList(), "Set search UI to Popup dialog") { _ ->
            HelixSettings.instance.searchUiMode = HelixSearchUiMode.POPUP
        },
        HelixCommandItem("jumps", emptyList(), "Open jumplist picker") { editor ->
            HelixJumplistPopup.show(editor)
        },
    )

    fun execute(cmd: String, editor: Editor) {
        val cleanCmd = cmd.trim().removePrefix(":")
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
        when (cleanCmd) {
            "jumps" -> HelixJumplistPopup.show(editor)

            "w", "write" -> HelixActionDelegate.executeAction("SaveAll", editor)

            "q", "quit" -> HelixActionDelegate.executeAction("CloseContent", editor)

            "wq", "x" -> {
                HelixActionDelegate.executeAction("SaveAll", editor)
                ApplicationManager.getApplication().invokeLater {
                    HelixActionDelegate.executeAction("CloseContent", editor)
                }
            }

            "wa" -> HelixActionDelegate.executeAction("SaveAll", editor)

            "qa" -> HelixActionDelegate.executeAction("CloseAllEditors", editor)

            "vsp" -> HelixActionDelegate.executeAction("SplitVertically", editor)

            "sp" -> HelixActionDelegate.executeAction("SplitHorizontally", editor)

            "format" -> HelixActionDelegate.executeAction("ReformatCode", editor)

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
        }
    }
}
