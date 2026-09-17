package jp.titze.intellij.helix.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixShellActions
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixDirectoryFilePickerPopup
import jp.titze.intellij.helix.ui.HelixJumplistPopup
import jp.titze.intellij.helix.ui.HelixSearchManager
import java.io.File

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
        HelixCommandItem("unsplit", listOf("only"), "Close all other splits") { editor ->
            HelixActionDelegate.executeAction("UnsplitAll", editor)
        },
        HelixCommandItem("close-split", listOf("close", "clo"), "Close active split") { editor ->
            HelixActionDelegate.executeAction("Unsplit", editor) ||
                HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem(
            "swap-split",
            listOf("change-split-orientation"),
            "Swap current split view direction (ChangeSplitOrientation)",
        ) { editor ->
            HelixActionDelegate.executeAction("ChangeSplitOrientation", editor)
        },
        HelixCommandItem("format", emptyList(), "Format buffer using IDE code formatter") { editor ->
            HelixActionDelegate.executeAction("ReformatCode", editor)
        },
        HelixCommandItem("fold", listOf("collapse-region"), "Fold block under cursor (CollapseRegion)") { editor ->
            HelixActionDelegate.executeAction("CollapseRegion", editor)
        },
        HelixCommandItem("unfold", listOf("expand-region"), "Unfold block under cursor (ExpandRegion)") { editor ->
            HelixActionDelegate.executeAction("ExpandRegion", editor)
        },
        HelixCommandItem(
            "fold-all",
            listOf("fold_all", "collapse-all-regions"),
            "Fold all methods and classes in file (CollapseAllRegions)",
        ) { editor ->
            HelixActionDelegate.executeAction("CollapseAllRegions", editor)
        },
        HelixCommandItem(
            "unfold-all",
            listOf("unfold_all", "expand-all-regions"),
            "Expand all folds in file (ExpandAllRegions)",
        ) { editor ->
            HelixActionDelegate.executeAction("ExpandAllRegions", editor)
        },
        HelixCommandItem(
            "optimize-imports",
            listOf("optimize_imports", "oi"),
            "Optimize and clean imports in current file",
        ) { editor -> HelixActionDelegate.executeAction("OptimizeImports", editor) },
        HelixCommandItem(
            "goto-test",
            listOf("goto_test", "test"),
            "Navigate to or create test for current class (GotoTest)",
        ) { editor -> HelixActionDelegate.executeAction("GotoTest", editor) },
        HelixCommandItem(
            "goto-declaration",
            listOf("goto_declaration", "declaration"),
            "Jump to declaration (GotoDeclarationOnly)",
        ) { editor ->
            HelixActionDelegate.executeAction("GotoDeclarationOnly", editor) ||
                HelixActionDelegate.executeAction("GotoDeclaration", editor)
        },
        HelixCommandItem(
            "goto-definition",
            listOf("goto_definition", "definition"),
            "Jump to definition or declaration (GotoDeclaration)",
        ) { editor ->
            HelixActionDelegate.executeAction("GotoDeclaration", editor)
        },
        HelixCommandItem("earlier", listOf("undo-earlier"), "Undo earlier changes (Alt+u)") { editor ->
            val count = HelixStateManager.getOrCreate(editor).takeCount() ?: 1
            repeat(count) { HelixActionDelegate.executeAction("\$Undo", editor) }
        },
        HelixCommandItem("later", listOf("redo-later"), "Redo later changes (Alt+U)") { editor ->
            val count = HelixStateManager.getOrCreate(editor).takeCount() ?: 1
            repeat(count) { HelixActionDelegate.executeAction("\$Redo", editor) }
        },
        HelixCommandItem(
            "commit-undo-checkpoint",
            listOf("checkpoint", "commit_undo_checkpoint"),
            "Commit an undo checkpoint",
        ) { editor ->
            HelixActions.commitUndoCheckpoint(editor)
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
        HelixCommandItem("registers", listOf("reg"), "Open registers picker popup") { editor ->
            jp.titze.intellij.helix.ui.HelixRegistersPopup.show(editor)
        },
        HelixCommandItem("switch-case", listOf("switch_case"), "Switch case of selected text (~)") { editor ->
            HelixActions.toggleCase(editor)
        },
        HelixCommandItem("switch-to-lowercase", listOf("switch_to_lowercase"), "Set selected text to lower case (`)") {
            HelixActions.toLowerCase(it)
        },
        HelixCommandItem(
            "switch-to-uppercase",
            listOf("switch_to_uppercase"),
            "Set selected text to upper case (Alt+`)",
        ) { editor ->
            HelixActions.toUpperCase(editor)
        },
        HelixCommandItem(
            "increment",
            listOf("inc"),
            "Increment integer under cursor or selection (Ctrl+a)",
        ) { editor ->
            val state = HelixStateManager.getOrCreate(editor)
            val count = state.takeCount() ?: 1
            HelixActions.increment(editor, count)
        },
        HelixCommandItem(
            "decrement",
            listOf("dec"),
            "Decrement integer under cursor or selection (Ctrl+x)",
        ) { editor ->
            val state = HelixStateManager.getOrCreate(editor)
            val count = state.takeCount() ?: 1
            HelixActions.decrement(editor, count)
        },
        HelixCommandItem(
            "trim-selections",
            listOf("trim_selections"),
            "Trim whitespace from selections (_)",
        ) { editor -> HelixActions.trimSelections(editor) },
        HelixCommandItem(
            "align-selections",
            listOf("align_selections"),
            "Align selections by padding with whitespace (&)",
        ) { editor -> HelixActions.alignSelections(editor) },
        HelixCommandItem(
            "keep-selections",
            listOf("keep_selections"),
            "Filter selections by regex, keeping matching (Alt+k)",
        ) { editor -> jp.titze.intellij.helix.ui.HelixSearchManager.startKeepSelections(editor) },
        HelixCommandItem(
            "remove-selections",
            listOf("remove_selections"),
            "Filter selections by regex, removing matching (Alt+K)",
        ) { editor -> jp.titze.intellij.helix.ui.HelixSearchManager.startRemoveSelections(editor) },
        HelixCommandItem(
            "ensure-selections-forward",
            listOf("ensure_selections_forward"),
            "Flip backward selections forward (Alt+:)",
        ) { editor -> HelixActions.ensureSelectionsForward(editor) },
        HelixCommandItem(
            "merge-selections",
            listOf("merge_selections", "merge_consecutive_selections"),
            "Merge contiguous or overlapping selections (Alt+_)",
        ) { editor -> HelixActions.mergeSelections(editor) },
        HelixCommandItem(
            "merge-all-selections",
            listOf("merge_all_selections"),
            "Merge all selections into single span (Alt+-)",
        ) { editor -> HelixActions.mergeAllSelections(editor) },
        HelixCommandItem(
            "join-selections-space",
            listOf("join_selections_space"),
            "Join lines and select inserted space (Alt+J)",
        ) { editor -> HelixActions.joinLines(editor, selectSpace = true) },
        HelixCommandItem(
            "delete-noyank",
            listOf("delete_noyank"),
            "Delete selection without yanking (Alt+d)",
        ) { editor -> HelixActions.deleteSelectionNoYank(editor) },
        HelixCommandItem(
            "change-noyank",
            listOf("change_noyank"),
            "Change selection without yanking (Alt+c)",
        ) { editor -> HelixActions.changeSelectionNoYank(editor) },
        HelixCommandItem(
            "repeat-last-motion",
            listOf("repeat_last_motion"),
            "Repeat last motion (Alt+.)",
        ) { editor -> HelixActions.repeatLastMotion(editor) },
        HelixCommandItem(
            "rotate-selection-contents-forward",
            listOf("rotate_selections_contents_forward"),
            "Cycle text contents forward without moving carets (Alt+))",
        ) { editor -> HelixActions.rotateSelectionsContents(editor, forward = true) },
        HelixCommandItem(
            "rotate-selection-contents-backward",
            listOf("rotate_selections_contents_backward"),
            "Cycle text contents backward without moving carets (Alt+()",
        ) { editor -> HelixActions.rotateSelectionsContents(editor, forward = false) },
        HelixCommandItem(
            "extend-to-line-bounds",
            listOf("extend_to_line_bounds", "extend_line_below"),
            "Extend selection to whole line bounds (X)",
        ) { editor -> HelixMotions.extendToLineBounds(editor) },
        HelixCommandItem(
            "shrink-to-line-bounds",
            listOf("shrink_to_line_bounds"),
            "Shrink selection to line bounds excluding line breaks (Alt+x)",
        ) { editor -> HelixMotions.shrinkToLineBounds(editor) },
        HelixCommandItem(
            "record-macro",
            listOf("macro-record"),
            "Start or stop recording a keyboard macro (Q)",
        ) { editor ->
            HelixActionDelegate.executeAction("StartStopMacroRecording", editor)
            HelixStateManager.getOrCreate(editor).notifyListeners()
        },
        HelixCommandItem(
            "replay-macro",
            listOf("macro-play", "playback-macro"),
            "Replay the last recorded keyboard macro (q)",
        ) { editor ->
            HelixActionDelegate.executeAction("PlaybackLastMacro", editor)
        },
        HelixCommandItem("sort", emptyList(), "Sort selected lines alphabetically") { editor ->
            HelixActions.sortLines(editor, reverse = false)
        },
        HelixCommandItem("sort-reverse", listOf("sort -r"), "Sort selected lines in reverse") { editor ->
            HelixActions.sortLines(editor, reverse = true)
        },
        HelixCommandItem(
            "terminal",
            listOf("sh"),
            "Open / toggle built-in terminal (ActivateTerminalToolWindow)",
        ) { editor ->
            HelixActionDelegate.executeAction("ActivateTerminalToolWindow", editor)
        },
        HelixCommandItem("pwd", emptyList(), "Display current working directory") { editor ->
            HelixDirectoryManager.printWorkingDirectory(editor)
        },
        HelixCommandItem("cd", emptyList(), "Change working directory") { editor ->
            HelixDirectoryManager.changeDirectory("", editor)
        },
        HelixCommandItem(
            "goto-line-start",
            listOf("goto_line_start", "line-start"),
            "Move cursor to line start (Ctrl+a, gh)",
        ) { editor -> HelixActionDelegate.executeAction("EditorLineStart", editor) },
        HelixCommandItem(
            "goto-line-end",
            listOf("goto_line_end", "line-end"),
            "Move cursor to line end (Ctrl+e, gl)",
        ) { editor -> HelixActionDelegate.executeAction("EditorLineEnd", editor) },
        HelixCommandItem(
            "delete-char-backward",
            listOf("delete_char_backward"),
            "Delete character backward (Ctrl+h, Backspace)",
        ) { editor -> HelixActionDelegate.executeAction("EditorBackSpace", editor) },
        HelixCommandItem(
            "delete-char-forward",
            listOf("delete_char_forward"),
            "Delete character forward (Ctrl+d, Delete)",
        ) { editor -> HelixActionDelegate.executeAction("EditorDelete", editor) },
        HelixCommandItem(
            "delete-word-backward",
            listOf("delete_word_backward"),
            "Delete previous word (Ctrl+w, Alt+Backspace)",
        ) { editor -> HelixActions.deleteWordBackward(editor) },
        HelixCommandItem(
            "delete-word-forward",
            listOf("delete_word_forward"),
            "Delete next word (Alt+d, Alt+Delete)",
        ) { editor -> HelixActions.deleteWordForward(editor) },
        HelixCommandItem(
            "kill-to-line-start",
            listOf("kill_to_line_start"),
            "Delete from cursor to line start (Ctrl+u)",
        ) { editor -> HelixActions.killToLineStart(editor) },
        HelixCommandItem(
            "kill-to-line-end",
            listOf("kill_to_line_end"),
            "Delete from cursor to line end (Ctrl+k)",
        ) { editor -> HelixActions.killToLineEnd(editor) },
        HelixCommandItem(
            "completion",
            listOf("complete"),
            "Trigger code completion menu (Ctrl+x)",
        ) { editor -> HelixActionDelegate.executeAction("CodeCompletion", editor) },
        HelixCommandItem(
            "signature-help",
            listOf("signature_help", "param-info", "parameter-info"),
            "Show signature help / parameter info (Ctrl+p)",
        ) { editor -> HelixActionDelegate.executeAction("ParameterInfo", editor) },
        HelixCommandItem(
            "select_prev_sibling",
            listOf("select-prev-sibling"),
            "Select previous sibling AST element (Alt+p, Alt+Left)",
        ) { editor -> HelixActions.selectPrevSibling(editor) },
        HelixCommandItem(
            "select_next_sibling",
            listOf("select-next-sibling"),
            "Select next sibling AST element (Alt+n, Alt+Right)",
        ) { editor -> HelixActions.selectNextSibling(editor) },
        HelixCommandItem(
            "move_parent_node_start",
            listOf("move-parent-node-start"),
            "Move or extend to start of parent AST node (Alt+b)",
        ) { editor -> HelixActions.moveParentNodeStart(editor) },
        HelixCommandItem(
            "move_parent_node_end",
            listOf("move-parent-node-end"),
            "Move or extend to end of parent AST node (Alt+e)",
        ) { editor -> HelixActions.moveParentNodeEnd(editor) },
        HelixCommandItem(
            "select_all_siblings",
            listOf("select-all-siblings"),
            "Select all sibling AST elements (Alt+a)",
        ) { editor -> HelixActions.selectAllSiblings(editor) },
        HelixCommandItem(
            "select_all_children",
            listOf("select-all-children"),
            "Select all direct children nodes (Alt+I)",
        ) { editor -> HelixActions.selectAllChildren(editor) },
        HelixCommandItem(
            "open",
            listOf("edit", "e", "file-picker", "file_picker"),
            "Open file picker or open file by path",
        ) { editor ->
            HelixActionDelegate.executeAction("GotoFile", editor) ||
                HelixActionDelegate.executeAction("SearchEverywhere", editor)
        },
        HelixCommandItem(
            "file-picker-in-current-directory",
            listOf("file_picker_in_current_directory"),
            "Open file picker in current directory",
        ) { editor ->
            HelixKeyHandler.recordJump(editor)
            HelixDirectoryFilePickerPopup.show(editor)
        },
        HelixCommandItem("buffer", listOf("b"), "Open buffer switcher (RecentFiles)") { editor ->
            HelixActionDelegate.executeAction("RecentFiles", editor)
        },
        HelixCommandItem("find", emptyList(), "Find in project files (FindInPath)") { editor ->
            HelixActionDelegate.executeAction("FindInPath", editor)
        },
        HelixCommandItem("buffer-close", listOf("bc", "bclose"), "Close active editor tab") { editor ->
            HelixActionDelegate.executeAction("CloseContent", editor)
        },
        HelixCommandItem(
            "buffer-close-others",
            listOf("bco", "bcloseother"),
            "Close all other editor tabs",
        ) { editor ->
            HelixActionDelegate.executeAction("CloseAllEditorsButActive", editor)
        },
        HelixCommandItem("buffer-close-all", listOf("bca", "bcloseall"), "Close all editor tabs") { editor ->
            HelixActionDelegate.executeAction("CloseAllEditors", editor)
        },
        HelixCommandItem("buffer-next", listOf("bn"), "Switch to next editor tab") { editor ->
            HelixActionDelegate.executeAction("NextTab", editor)
        },
        HelixCommandItem("buffer-previous", listOf("bp"), "Switch to previous editor tab") { editor ->
            HelixActionDelegate.executeAction("PreviousTab", editor)
        },
        HelixCommandItem("new", listOf("n"), "Create new scratch file / buffer") { editor ->
            HelixActionDelegate.executeAction("NewScratchFile", editor)
        },
        HelixCommandItem(
            "pipe",
            listOf("shell-pipe", "shell_pipe"),
            "Pipe selections into shell command and replace",
        ) { editor -> HelixSearchManager.startShellPipe(editor) },
        HelixCommandItem(
            "pipe-to",
            listOf("shell-pipe-to", "shell_pipe_to"),
            "Pipe selections into shell command ignoring output",
        ) { editor -> HelixSearchManager.startShellPipeTo(editor) },
        HelixCommandItem(
            "insert-output",
            listOf("insert_output", "shell_insert_output"),
            "Insert shell output before selections",
        ) { editor -> HelixSearchManager.startShellInsert(editor) },
        HelixCommandItem(
            "append-output",
            listOf("append_output", "shell_append_output"),
            "Append shell output after selections",
        ) { editor -> HelixSearchManager.startShellAppend(editor) },
        HelixCommandItem(
            "keep-pipe",
            listOf("keep_pipe", "shell_keep_pipe"),
            "Filter selections through shell command exit code",
        ) { editor -> HelixSearchManager.startShellKeepPipe(editor) },
        HelixCommandItem(
            "run-shell-command",
            listOf("run_shell_command"),
            "Run a shell command asynchronously",
        ) { editor -> HelixActionDelegate.executeAction("ActivateTerminalToolWindow", editor) },
        HelixCommandItem(
            "dap-toggle-breakpoint",
            listOf("dap_toggle_breakpoint", "breakpoint", "toggle-breakpoint"),
            "Toggle line breakpoint (space + G b)",
        ) { editor -> HelixActionDelegate.executeAction("ToggleLineBreakpoint", editor) },
        HelixCommandItem(
            "dap-continue",
            listOf("dap_continue", "continue", "resume"),
            "Continue / resume program execution (space + G c)",
        ) { editor -> HelixActionDelegate.executeAction("Resume", editor) },
        HelixCommandItem(
            "dap-step-in",
            listOf("dap_step_in", "step-in"),
            "Step into function / method (space + G s)",
        ) { editor -> HelixActionDelegate.executeAction("StepInto", editor) },
        HelixCommandItem(
            "dap-next",
            listOf("dap_next", "step-over", "next"),
            "Step over next statement (space + G n)",
        ) { editor -> HelixActionDelegate.executeAction("StepOver", editor) },
        HelixCommandItem(
            "dap-step-out",
            listOf("dap_step_out", "step-out"),
            "Step out of current stack frame (space + G o)",
        ) { editor -> HelixActionDelegate.executeAction("StepOut", editor) },
        HelixCommandItem(
            "dap-terminate",
            listOf("dap_terminate", "stop", "terminate"),
            "Terminate debug session (space + G t)",
        ) { editor -> HelixActionDelegate.executeAction("Stop", editor) },
        HelixCommandItem(
            "dap-restart",
            listOf("dap_restart", "restart", "rerun"),
            "Restart debugging session (space + G r)",
        ) { editor -> HelixActionDelegate.executeAction("Rerun", editor) },
        HelixCommandItem(
            "dap-pause",
            listOf("dap_pause", "pause"),
            "Pause program execution (space + G p)",
        ) { editor -> HelixActionDelegate.executeAction("Pause", editor) },
        HelixCommandItem(
            "dap-launch",
            listOf("dap_launch", "debug"),
            "Launch active debug target (space + G l)",
        ) { editor -> HelixActionDelegate.executeAction("Debug", editor) },
        HelixCommandItem(
            "dap-variables",
            listOf("dap_variables", "variables", "dap-ui"),
            "Show debug panel and variables (space + G v)",
        ) { editor -> HelixActionDelegate.executeAction("ActivateDebugToolWindow", editor) },
        HelixCommandItem(
            "dap-evaluate",
            listOf("dap_evaluate", "evaluate", "eval"),
            "Evaluate expression popup (space + G k)",
        ) { editor -> HelixActionDelegate.executeAction("EvaluateExpression", editor) },
        HelixCommandItem(
            "dap-edit-condition",
            listOf("dap_edit_condition", "edit-breakpoint"),
            "Edit breakpoint condition and log (space + G e)",
        ) { editor -> HelixActionDelegate.executeAction("EditBreakpoint", editor) },
        HelixCommandItem(
            "dap-view-breakpoints",
            listOf("dap_view_breakpoints", "breakpoints", "list-breakpoints"),
            "View all breakpoints (space + G B)",
        ) { editor -> HelixActionDelegate.executeAction("ViewBreakpoints", editor) },
    )

    fun execute(cmd: String, editor: Editor) {
        val cleanCmd = cmd.trim().removePrefix(":")

        val parts = cleanCmd.split(Regex("\\s+"), limit = 2)
        val baseCmd = parts[0].lowercase()
        val arg = if (parts.size > 1) parts[1].trim() else ""

        if (executeSpecialCommand(cleanCmd, baseCmd, arg, editor)) {
            return
        }

        if (baseCmd == "sort") {
            val isReverse = arg == "-r" || arg == "--reverse" || arg == "reverse"
            HelixActions.sortLines(editor, isReverse)
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
        when (cleanCmd) {
            "jumps" -> HelixJumplistPopup.show(editor)

            "reg", "registers" -> jp.titze.intellij.helix.ui.HelixRegistersPopup.show(editor)

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

            "unsplit", "only" -> HelixActionDelegate.executeAction("UnsplitAll", editor)

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
            val descriptor = OpenFileDescriptor(project, virtualFile)
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
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
        return executeShellCommand(baseCmd, arg, editor)
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
