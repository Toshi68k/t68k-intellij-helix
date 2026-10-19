// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.editor.HelixInsertTracker
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.motion.HelixFileNavigation
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.motion.HelixViewMotions
import jp.titze.intellij.helix.settings.HelixLineNavigationMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.ui.HelixDirectoryFilePickerPopup
import jp.titze.intellij.helix.ui.HelixJumplistPopup
import jp.titze.intellij.helix.ui.HelixSearchManager
import java.util.concurrent.ConcurrentHashMap

data class HelixRegisteredCommand(
    val id: String,
    val description: String,
    val intelliJAction: String? = null,
    val action: (Editor, count: Int) -> Boolean,
)

/**
 * Registry of canonical Helix commands mapping identifiers to executable actions.
 */
object HelixCommandRegistry {

    private val commands = ConcurrentHashMap<String, HelixRegisteredCommand>()

    init {
        registerBuiltins()
    }

    fun register(
        id: String,
        description: String,
        intelliJAction: String? = null,
        action: (Editor, count: Int) -> Boolean,
    ) {
        commands[id] = HelixRegisteredCommand(id, description, intelliJAction, action)
    }

    fun get(id: String): HelixRegisteredCommand? = commands[id]

    fun getAll(): Collection<HelixRegisteredCommand> = commands.values

    fun execute(commandId: String, editor: Editor, count: Int = 1): Boolean {
        val trimmed = commandId.trim()
        if (trimmed == "no_op") return true

        if (trimmed.startsWith(":action ")) {
            val actionId = trimmed.removePrefix(":action ").trim()
            var success = false
            repeat(count) {
                if (HelixActionDelegate.executeAction(actionId, editor)) success = true
            }
            return success
        }

        if (trimmed.startsWith(":")) {
            HelixCommands.execute(trimmed.removePrefix(":").trim(), editor)
            return true
        }

        val registered = commands[trimmed]
        if (registered != null) {
            return registered.action(editor, count)
        }

        // Fallback: check if the string itself is an IntelliJ action ID
        return HelixActionDelegate.executeAction(trimmed, editor)
    }

    fun executeList(commandIds: List<String>, editor: Editor, count: Int = 1): Boolean {
        var allSuccess = true
        for (cmd in commandIds) {
            if (!execute(cmd, editor, count)) {
                allSuccess = false
            }
        }
        return allSuccess
    }

    private fun registerBuiltins() {
        registerMotions()
        registerEdits()
        registerSelectAndHistory()
        registerPickersAndLeader()
        registerViewAndWindow()
    }

    private fun registerMotions() {
        register("move_char_left", "Move left") { ed, cnt ->
            HelixMotions.moveLeft(ed, cnt)
            true
        }
        register("move_char_right", "Move right") { ed, cnt ->
            HelixMotions.moveRight(ed, cnt)
            true
        }
        register("move_line_down", "Move down") { ed, cnt ->
            if (HelixSettings.instance.lineNavigationMode == HelixLineNavigationMode.HELIX_STANDARD) {
                HelixMotions.moveVisualDown(ed, cnt)
            } else {
                HelixMotions.moveDown(ed, cnt)
            }
            true
        }
        register("move_line_up", "Move up") { ed, cnt ->
            if (HelixSettings.instance.lineNavigationMode == HelixLineNavigationMode.HELIX_STANDARD) {
                HelixMotions.moveVisualUp(ed, cnt)
            } else {
                HelixMotions.moveUp(ed, cnt)
            }
            true
        }
        register("move_visual_line_down", "Move visual line down") { ed, cnt ->
            HelixMotions.moveVisualDown(ed, cnt)
            true
        }
        register("move_visual_line_up", "Move visual line up") { ed, cnt ->
            HelixMotions.moveVisualUp(ed, cnt)
            true
        }
        register("move_next_word_start", "Move to next word start") { ed, cnt ->
            HelixMotions.moveNextWordStart(ed, cnt)
            true
        }
        register("move_prev_word_start", "Move to previous word start") { ed, cnt ->
            HelixMotions.movePrevWordStart(ed, cnt)
            true
        }
        register("move_next_word_end", "Move to next word end") { ed, cnt ->
            HelixMotions.moveWordEnd(ed, cnt)
            true
        }
        register("move_next_long_word_start", "Move next long word start") { ed, cnt ->
            HelixMotions.moveNextBigWordStart(ed, cnt)
            true
        }
        register("move_prev_long_word_start", "Move previous long word start") { ed, cnt ->
            HelixMotions.movePrevBigWordStart(ed, cnt)
            true
        }
        register("move_next_long_word_end", "Move next long word end") { ed, cnt ->
            HelixMotions.moveBigWordEnd(ed, cnt)
            true
        }
        register("extend_line", "Select current line") { ed, cnt ->
            HelixMotions.selectLine(ed, cnt)
            true
        }
        register("extend_to_line_bounds", "Extend to line bounds") { ed, cnt ->
            HelixMotions.extendToLineBounds(ed, cnt)
            true
        }
        register("select_all", "Select entire buffer") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.selectAll(ed)
            true
        }
        register("goto_line_start", "Goto line start") { ed, _ ->
            HelixMotions.moveLineStart(ed)
            true
        }
        register("goto_first_nonwhitespace", "Goto first non-whitespace") { ed, _ ->
            HelixMotions.moveLineFirstNonWhitespace(ed)
            true
        }
        register("goto_line_end", "Goto line end") { ed, _ ->
            HelixMotions.moveLineEnd(ed)
            true
        }
        register("goto_file_start", "Goto file start") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveFileStart(ed, cnt)
            true
        }
        register("goto_last_line", "Goto last line") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveFileEnd(ed)
            true
        }
        register("page_down", "Page down") { ed, cnt ->
            HelixMotions.pageDown(ed, cnt)
            true
        }
        register("page_up", "Page up") { ed, cnt ->
            HelixMotions.pageUp(ed, cnt)
            true
        }
        register("half_page_down", "Half page down") { ed, cnt ->
            HelixMotions.halfPageDown(ed, cnt)
            true
        }
        register("half_page_up", "Half page up") { ed, cnt ->
            HelixMotions.halfPageUp(ed, cnt)
            true
        }
    }

    private fun registerEdits() {
        register("delete_selection", "Delete selection") { ed, cnt ->
            HelixActions.deleteSelection(ed, count = cnt)
            true
        }
        register("change_selection", "Change selection") { ed, cnt ->
            HelixActions.deleteSelection(ed, enterInsert = true, count = cnt)
            true
        }
        register("yank", "Yank selection") { ed, cnt ->
            HelixActions.yankSelection(ed, count = cnt)
            true
        }
        register("yank_to_clipboard", "Yank to system clipboard") { ed, _ ->
            HelixActions.yankSelection(ed, register = '+')
            true
        }
        register("yank_main_selection_to_clipboard", "Yank primary selection to clipboard") { ed, _ ->
            HelixActions.yankMainSelection(ed, register = '+')
            true
        }
        register("paste_after", "Paste after") { ed, _ ->
            HelixActions.paste(ed, after = true)
            true
        }
        register("paste_before", "Paste before") { ed, _ ->
            HelixActions.paste(ed, after = false)
            true
        }
        register("paste_clipboard_after", "Paste clipboard after") { ed, _ ->
            HelixActions.paste(ed, after = true, register = '+')
            true
        }
        register("paste_clipboard_before", "Paste clipboard before") { ed, _ ->
            HelixActions.paste(ed, after = false, register = '+')
            true
        }
        register("replace_with_yanked", "Replace with yanked") { ed, _ ->
            HelixActions.replaceWithClipboard(ed)
            true
        }
        register("replace_clipboard", "Replace with clipboard") { ed, _ ->
            HelixActions.replaceWithClipboard(ed, register = '+')
            true
        }
        register("join_lines", "Join lines") { ed, cnt ->
            HelixActions.joinLines(ed, cnt)
            true
        }
        register("insert_mode", "Enter insert mode") { ed, _ ->
            HelixActions.enterInsert(ed)
            true
        }
        register("append_mode", "Enter insert mode (append)") { ed, _ ->
            HelixActions.enterInsertAppend(ed)
            true
        }
        register("insert_at_line_start", "Insert at line start") { ed, _ ->
            HelixActions.enterInsertLineStart(ed)
            true
        }
        register("insert_at_line_end", "Insert at line end") { ed, _ ->
            HelixActions.enterInsertLineEnd(ed)
            true
        }
        register("open_below", "Open new line below") { ed, _ ->
            HelixActions.openNewLineBelow(ed)
            true
        }
        register("open_above", "Open new line above") { ed, _ ->
            HelixActions.openNewLineAbove(ed)
            true
        }
        register("repeat_last_insert", "Repeat last insert") { ed, cnt ->
            HelixInsertTracker.repeatLastInsert(ed, cnt)
            true
        }
        register("toggle_case", "Toggle case") { ed, cnt ->
            HelixActions.toggleCase(ed, cnt)
            true
        }
        register("switch_to_lowercase", "Switch to lowercase") { ed, cnt ->
            HelixActions.toLowerCase(ed, cnt)
            true
        }
        register("trim_selections", "Trim whitespace from selections") { ed, _ ->
            HelixActions.trimSelections(ed)
            true
        }
        register("align_selections", "Align selections into columns") { ed, _ ->
            HelixActions.alignSelections(ed)
            true
        }
        register("indent", "Indent line/selection", "EditorIndentLineOrSelection") { ed, cnt ->
            repeat(cnt) { HelixActionDelegate.executeAction("EditorIndentLineOrSelection", ed) }
            true
        }
        register("unindent", "Unindent selection", "EditorUnindentSelection") { ed, cnt ->
            repeat(cnt) { HelixActionDelegate.executeAction("EditorUnindentSelection", ed) }
            true
        }
        register("delete_selection_noyank", "Delete selection without yanking") { ed, cnt ->
            HelixActions.deleteSelectionNoYank(ed, count = cnt)
            true
        }
        register("change_selection_noyank", "Change selection without yanking") { ed, cnt ->
            HelixActions.changeSelectionNoYank(ed, count = cnt)
            true
        }
        register("repeat_last_motion", "Repeat last motion") { ed, cnt ->
            HelixActions.repeatLastMotion(ed, count = cnt)
        }
        register("format_selections", "Format selections", "ReformatCode") { ed, _ ->
            HelixActionDelegate.executeAction("ReformatCode", ed)
        }
    }

    private fun registerSelectAndHistory() {
        register("record_macro", "Record macro", "StartStopMacroRecording") { ed, _ ->
            val success = HelixActionDelegate.executeAction("StartStopMacroRecording", ed)
            jp.titze.intellij.helix.state.HelixStateManager.getOrCreate(ed).notifyListeners()
            success
        }
        register("replay_macro", "Replay macro", "PlaybackLastMacro") { ed, cnt ->
            repeat(cnt) { HelixActionDelegate.executeAction("PlaybackLastMacro", ed) }
            true
        }
        register("select_mode", "Toggle select mode") { ed, _ ->
            HelixActions.toggleSelectMode(ed)
            true
        }
        register("collapse_selection", "Collapse selection to cursor") { ed, _ ->
            HelixMotions.collapseSelection(ed)
            true
        }
        register("keep_primary_selection", "Keep only primary selection") { ed, _ ->
            HelixMotions.keepOnlyPrimaryCaret(ed)
            true
        }
        register("rotate_selections_backward", "Rotate selections backward") { ed, _ ->
            HelixMotions.rotateSelections(ed, forward = false)
            true
        }
        register("rotate_selections_forward", "Rotate selections forward") { ed, _ ->
            HelixMotions.rotateSelections(ed, forward = true)
            true
        }
        register("copy_selection_on_next_line", "Copy selection on next line") { ed, cnt ->
            HelixMotions.copySelectionOnNextLine(ed, cnt)
            true
        }
        register("undo", "Undo", "\$Undo") { ed, cnt ->
            repeat(cnt) { HelixActionDelegate.executeAction("\$Undo", ed) }
            true
        }
        register("redo", "Redo", "\$Redo") { ed, cnt ->
            repeat(cnt) { HelixActionDelegate.executeAction("\$Redo", ed) }
            true
        }
        register("jump_backward", "Jump backward in jumplist") { ed, cnt ->
            ed.project?.let { HelixJumpListService.getInstance(it).jumpBackward(ed, cnt) }
            true
        }
        register("jump_forward", "Jump forward in jumplist") { ed, cnt ->
            ed.project?.let { HelixJumpListService.getInstance(it).jumpForward(ed, cnt) }
            true
        }
        register("save_selection", "Save jump location") { ed, _ ->
            ed.project?.let { HelixJumpListService.getInstance(it).recordCurrent(ed, force = true) }
            true
        }
        register("keep_selections", "Keep selections matching regex") { ed, _ ->
            HelixSearchManager.startKeepSelections(ed)
            true
        }
        register("select_regex", "Select regex matches inside selections") { ed, _ ->
            HelixSearchManager.startSelect(ed, isSplit = false)
            true
        }
        register("split_regex", "Split selections on regex") { ed, _ ->
            HelixSearchManager.startSelect(ed, isSplit = true)
            true
        }
        register("search", "Search forward") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixSearchManager.startSearch(ed, backward = false, count = cnt)
            true
        }
        register("rsearch", "Search backward") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixSearchManager.startSearch(ed, backward = true, count = cnt)
            true
        }
        register("search_next", "Next search match") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixActions.searchNext(ed, cnt)
            true
        }
        register("search_prev", "Previous search match") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixActions.searchPrev(ed, cnt)
            true
        }
        register("search_selection", "Search current selection") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixActions.searchSelection(ed)
            true
        }
        register("shell_pipe", "Pipe selections through shell") { ed, _ ->
            HelixSearchManager.startShellPipe(ed)
            true
        }
        register("shell_insert", "Insert output of shell command") { ed, _ ->
            HelixSearchManager.startShellInsert(ed)
            true
        }
        register("shell_keep_pipe", "Filter selections through shell") { ed, _ ->
            HelixSearchManager.startShellKeepPipe(ed)
            true
        }
    }

    private fun registerPickersAndLeader() {
        register("file_picker", "File picker", "GotoFile") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = 'f'
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("GotoFile", ed) ||
                HelixActionDelegate.executeAction("SearchEverywhere", ed)
        }
        register("file_picker_in_current_directory", "File picker in current directory") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = 'F'
            HelixKeyHandler.recordJump(ed)
            HelixDirectoryFilePickerPopup.show(ed)
            true
        }
        register("buffer_picker", "Buffer / Tab picker", "RecentFiles") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = 'b'
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("RecentFiles", ed)
        }
        register("global_search", "Global search", "FindInPath") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = '/'
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("FindInPath", ed)
        }
        register("jumplist_picker", "Jumplist picker") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = 'j'
            HelixJumplistPopup.show(ed)
            true
        }
        register("symbol_picker", "Symbol picker", "FileStructurePopup") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = 's'
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("FileStructurePopup", ed)
        }
        register("workspace_symbol_picker", "Workspace symbol picker", "GotoSymbol") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = 'S'
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("GotoSymbol", ed)
        }
        register("diagnostics_picker", "Diagnostics picker", "ShowErrorDescription") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = 'd'
            HelixActionDelegate.executeAction("ShowErrorDescription", ed)
        }
        register("workspace_diagnostics", "Workspace diagnostics", "ActivateProblemsViewToolWindow") { ed, _ ->
            HelixSpaceKeymap.lastPickerChar = 'D'
            HelixActionDelegate.executeAction("ActivateProblemsViewToolWindow", ed)
        }
        register("file_explorer", "File explorer", "ActivateProjectToolWindow") { ed, _ ->
            HelixActionDelegate.executeAction("ActivateProjectToolWindow", ed)
        }
        register("file_explorer_buffer", "Buffer directory explorer", "SelectInProjectView") { ed, _ ->
            HelixActionDelegate.executeAction("SelectInProjectView", ed)
        }
        register("changed_file_picker", "Changed file picker", "ActivateVersionControlToolWindow") { ed, _ ->
            HelixActionDelegate.executeAction("ActivateVersionControlToolWindow", ed) ||
                HelixActionDelegate.executeAction("Vcs.Show.Local.Changes", ed)
        }
        register("toggle_comments", "Toggle line comment", "CommentByLineComment") { ed, _ ->
            HelixActionDelegate.executeAction("CommentByLineComment", ed)
        }
        register("toggle_block_comments", "Toggle block comment", "CommentByBlockComment") { ed, _ ->
            HelixActionDelegate.executeAction("CommentByBlockComment", ed)
        }
        register("hover", "Hover / Documentation", "QuickJavaDoc") { ed, _ ->
            HelixActionDelegate.executeAction("QuickJavaDoc", ed)
        }
        register("code_action", "Code action", "ShowIntentionActions") { ed, _ ->
            HelixActionDelegate.executeAction("ShowIntentionActions", ed)
        }
        register("rename_symbol", "Rename symbol", "RenameElement") { ed, _ ->
            HelixActionDelegate.executeAction("RenameElement", ed)
        }
        register("command_palette", "Command palette", "GotoAction") { ed, _ ->
            HelixActionDelegate.executeAction("GotoAction", ed)
        }
    }

    private fun registerViewAndWindow() {
        register("align_view_center", "Center view") { ed, _ ->
            HelixViewMotions.alignViewCenter(ed)
            true
        }
        register("align_view_top", "Align view top") { ed, _ ->
            HelixViewMotions.alignViewTop(ed)
            true
        }
        register("align_view_bottom", "Align view bottom") { ed, _ ->
            HelixViewMotions.alignViewBottom(ed)
            true
        }
        register("align_view_middle", "Align view middle (horiz)") { ed, _ ->
            HelixViewMotions.alignViewMiddle(ed)
            true
        }
        register("scroll_down", "Scroll view down") { ed, cnt ->
            HelixViewMotions.scrollViewDown(ed, cnt)
            true
        }
        register("scroll_up", "Scroll view up") { ed, cnt ->
            HelixViewMotions.scrollViewUp(ed, cnt)
            true
        }
        register("goto_window_top", "Goto window top") { ed, _ ->
            HelixViewMotions.gotoWindowTop(ed)
            true
        }
        register("goto_window_center", "Goto window center") { ed, _ ->
            HelixViewMotions.gotoWindowCenter(ed)
            true
        }
        register("goto_window_bottom", "Goto window bottom") { ed, _ ->
            HelixViewMotions.gotoWindowBottom(ed)
            true
        }

        register("vsplit", "Vertical split", "SplitVertically") { ed, _ ->
            HelixActionDelegate.executeAction("SplitVertically", ed)
        }
        register("hsplit", "Horizontal split", "SplitHorizontally") { ed, _ ->
            HelixActionDelegate.executeAction("SplitHorizontally", ed)
        }
        register("wclose", "Close active split", "Unsplit") { ed, _ ->
            HelixActionDelegate.executeAction("Unsplit", ed) || HelixActionDelegate.executeAction("CloseContent", ed)
        }
        register("wonly", "Close other splits", "UnsplitAll") { ed, _ ->
            HelixActionDelegate.executeAction("UnsplitAll", ed)
        }
        register("jump_view_left", "Focus left split", "PrevSplitter") { ed, _ ->
            HelixActionDelegate.executeAction("PrevSplitter", ed)
        }
        register("jump_view_right", "Focus right split", "NextSplitter") { ed, _ ->
            HelixActionDelegate.executeAction("NextSplitter", ed)
        }
        register("jump_view_up", "Focus above split", "PrevSplitter") { ed, _ ->
            HelixActionDelegate.executeAction("PrevSplitter", ed)
        }
        register("jump_view_down", "Focus below split", "NextSplitter") { ed, _ ->
            HelixActionDelegate.executeAction("NextSplitter", ed)
        }
        register("transpose_view", "Transpose split", "ChangeSplitOrientation") { ed, _ ->
            HelixActionDelegate.executeAction("ChangeSplitOrientation", ed)
        }
        register("goto_definition", "Goto definition", "GotoDeclaration") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("GotoDeclaration", ed)
        }
        register("goto_declaration", "Goto declaration", "GotoDeclarationOnly") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("GotoDeclarationOnly", ed) ||
                HelixActionDelegate.executeAction("GotoDeclaration", ed)
        }
        register("goto_implementation", "Goto implementation", "GotoImplementation") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("GotoImplementation", ed)
        }
        register("goto_type_definition", "Goto type definition", "GotoTypeDeclaration") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("GotoTypeDeclaration", ed)
        }
        register("goto_reference", "Goto reference", "FindUsages") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("FindUsages", ed)
        }
        register("goto_next_buffer", "Next buffer / tab", "NextTab") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            repeat(cnt) { HelixActionDelegate.executeAction("NextTab", ed) }
            true
        }
        register("goto_previous_buffer", "Previous buffer / tab", "PreviousTab") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            repeat(cnt) { HelixActionDelegate.executeAction("PreviousTab", ed) }
            true
        }
        register("goto_last_change", "Jump to last change", "JumpToLastChange") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("JumpToLastChange", ed)
        }
        register("goto_last_accessed_file", "Last accessed file") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixFileNavigation.gotoLastAccessedFile(ed)
        }
        register("goto_last_modified_file", "Last modified file") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixFileNavigation.gotoLastModifiedFile(ed)
        }
        register("goto_file", "Goto file at caret") { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixFileNavigation.gotoFileAtCaret(ed)
        }
    }
}
