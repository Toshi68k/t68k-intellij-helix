// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.ui

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.action.HelixSearchActions
import jp.titze.intellij.helix.editor.HelixInsertTracker
import jp.titze.intellij.helix.keymap.HelixCommandRegistry
import jp.titze.intellij.helix.keymap.HelixKeyStroke
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.keymap.HelixKeymapNode
import jp.titze.intellij.helix.register.HelixRegisterEntry
import jp.titze.intellij.helix.register.HelixRegisterItem
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.settings.HelixLineNavigationMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode

data class WhichKeyItem(
    val key: String,
    val label: String,
    val helixCommand: String = "",
    val intelliJAction: String = "",
    val snippet: String? = null,
) {
    val description: String get() = helixCommand
}

object HelixWhichKeyMenus {

    val spaceItems = listOf(
        WhichKeyItem("b", "Buffer / Tab picker", "buffer_picker", "RecentFiles"),
        WhichKeyItem("B", "Recent locations picker", "recent_locations", "RecentLocations"),
        WhichKeyItem("f", "File picker", "file_picker", "GotoFile"),
        WhichKeyItem("F", "Current dir file picker", "file_picker_in_current_directory"),
        WhichKeyItem("e", "File explorer", "file_explorer", "ActivateProjectToolWindow"),
        WhichKeyItem(
            "E",
            "Buffer directory explorer",
            "file_explorer_buffer",
            "SelectInProjectView",
        ),
        WhichKeyItem(".", "Buffer directory explorer", "buffer_dir_picker", "SelectInProjectView"),
        WhichKeyItem("g", "Changed file picker", "changed_file_picker", "ActivateVersionControlToolWindow"),
        WhichKeyItem("/", "Global search", "global_search", "FindInPath"),
        WhichKeyItem("s", "Symbol picker", "symbol_picker", "FileStructurePopup"),
        WhichKeyItem("S", "Workspace symbol picker", "workspace_symbol_picker", "GotoSymbol"),
        WhichKeyItem("d", "Diagnostics picker", "diagnostics_picker", "ShowErrorDescription"),
        WhichKeyItem("D", "Workspace diagnostics", "workspace_diagnostics", "ActivateProblemsViewToolWindow"),
        WhichKeyItem("j", "Jumplist picker", "jumplist_picker"),
        WhichKeyItem("a", "Code action", "code_action", "ShowIntentionActions"),
        WhichKeyItem("r", "Rename symbol", "rename_symbol", "RenameElement"),
        WhichKeyItem("m", "Refactor menu", "refactor_menu"),
        WhichKeyItem("t", "Test menu", "test_menu"),
        WhichKeyItem("v", "VCS / Git menu", "vcs_menu"),
        WhichKeyItem("c", "Toggle line comment", "toggle_comments", "CommentByLineComment"),
        WhichKeyItem("C", "Toggle block comment", "toggle_block_comments", "CommentByBlockComment"),
        WhichKeyItem("h", "Select references", "select_references", "FindUsages"),
        WhichKeyItem("'", "Last picker", "last_picker"),
        WhichKeyItem("w", "Window mode", "window_mode"),
        WhichKeyItem("y", "Yank to clipboard", "yank_to_clipboard"),
        WhichKeyItem("Y", "Yank main selection", "yank_main_selection_to_clipboard"),
        WhichKeyItem("p", "Paste clipboard after", "paste_clipboard_after"),
        WhichKeyItem("P", "Paste clipboard before", "paste_clipboard_before"),
        WhichKeyItem("R", "Replace with clipboard", "replace_clipboard"),
        WhichKeyItem("k", "Hover / Documentation", "hover", "QuickJavaDoc"),
        WhichKeyItem("G", "Debug (DAP) menu", "dap"),
        WhichKeyItem("?", "Command palette", "command_palette", "GotoAction"),
    )

    val gotoItems: List<WhichKeyItem>
        get() {
            val isHelixStandard = HelixSettings.instance.lineNavigationMode == HelixLineNavigationMode.HELIX_STANDARD
            val (jDesc, jCmd) = if (isHelixStandard) {
                "Move down line" to "move_line_down"
            } else {
                "Move down visual line" to "move_visual_line_down"
            }
            val (kDesc, kCmd) = if (isHelixStandard) {
                "Move up line" to "move_line_up"
            } else {
                "Move up visual line" to "move_visual_line_up"
            }
            return listOf(
                WhichKeyItem("d", "Goto definition", "goto_definition", "GotoDeclaration"),
                WhichKeyItem("D", "Goto declaration", "goto_declaration", "GotoDeclarationOnly"),
                WhichKeyItem("i", "Goto implementation", "goto_implementation", "GotoImplementation"),
                WhichKeyItem("y", "Goto type definition", "goto_type_definition", "GotoTypeDeclaration"),
                WhichKeyItem("r", "Goto reference", "goto_reference", "FindUsages"),
                WhichKeyItem("n", "Next buffer / tab", "goto_next_buffer", "NextTab"),
                WhichKeyItem("p", "Previous buffer / tab", "goto_previous_buffer", "PreviousTab"),
                WhichKeyItem(".", "Last edit location", "goto_last_change", "JumpToLastChange"),
                WhichKeyItem("h", "Goto line start", "goto_line_start"),
                WhichKeyItem("l", "Goto line end", "goto_line_end"),
                WhichKeyItem("s", "Goto first non-whitespace", "goto_first_nonwhitespace"),
                WhichKeyItem("t", "Goto window top", "goto_window_top"),
                WhichKeyItem("T", "Toggle test / subject", "goto_test", "GotoTest"),
                WhichKeyItem("c", "Goto window center", "goto_window_center"),
                WhichKeyItem("b", "Goto window bottom", "goto_window_bottom"),
                WhichKeyItem("f", "Goto file at caret", "goto_file"),
                WhichKeyItem("|", "Goto column", "goto_column <count>"),
                WhichKeyItem("w", "Jump to word", "goto_word"),
                WhichKeyItem("g", "Goto line / file start", "goto_line_start_file"),
                WhichKeyItem("e", "Goto file end", "goto_last_line"),
                WhichKeyItem("a", "Last accessed file", "goto_last_accessed_file"),
                WhichKeyItem("m", "Last modified file", "goto_last_modified_file"),
                WhichKeyItem("j", jDesc, jCmd),
                WhichKeyItem("k", kDesc, kCmd),
            )
        }

    val matchItems = listOf(
        WhichKeyItem("s", "Surround add", "surround_add <char>"),
        WhichKeyItem("r", "Surround replace", "surround_replace <from><to>"),
        WhichKeyItem("d", "Surround delete", "surround_delete <char>"),
        WhichKeyItem("m", "Match bracket", "match_brackets"),
        WhichKeyItem("a", "Select around textobject", "select_around_textobject <obj>"),
        WhichKeyItem("i", "Select inside textobject", "select_inside_textobject <obj>"),
    )

    val bracketOpenItems = listOf(
        WhichKeyItem("d", "Previous diagnostic", "goto_prev_diag", "GotoPreviousError"),
        WhichKeyItem("D", "First diagnostic", "goto_first_diag"),
        WhichKeyItem("f", "Previous function", "goto_prev_function", "MethodUp"),
        WhichKeyItem("t", "Previous class", "goto_prev_class"),
        WhichKeyItem("a", "Previous parameter", "goto_prev_parameter"),
        WhichKeyItem("c", "Previous comment", "goto_prev_comment"),
        WhichKeyItem("T", "Previous test", "goto_prev_test"),
        WhichKeyItem("p", "Previous paragraph", "goto_prev_paragraph"),
        WhichKeyItem("g", "Previous change", "goto_prev_change", "VcsShowPrevChangeMarker"),
        WhichKeyItem("G", "First change", "goto_first_change"),
        WhichKeyItem("C", "Previous conflict", "goto_prev_conflict", "PreviousConflict"),
        WhichKeyItem("e", "Previous entry", "goto_prev_entry"),
        WhichKeyItem("s", "Previous spelling error", "goto_prev_spelling_error", "GotoPreviousSpellingError"),
        WhichKeyItem("Space", "Add newline above", "add_newline_above"),
        WhichKeyItem("b", "Previous buffer / tab", "goto_previous_buffer", "PreviousTab"),
        WhichKeyItem("x", "Previous XML element", "goto_prev_xml_element"),
    )

    val bracketCloseItems = listOf(
        WhichKeyItem("d", "Next diagnostic", "goto_next_diag", "GotoNextError"),
        WhichKeyItem("D", "Last diagnostic", "goto_last_diag"),
        WhichKeyItem("f", "Next function", "goto_next_function", "MethodDown"),
        WhichKeyItem("t", "Next class", "goto_next_class"),
        WhichKeyItem("a", "Next parameter", "goto_next_parameter"),
        WhichKeyItem("c", "Next comment", "goto_next_comment"),
        WhichKeyItem("T", "Next test", "goto_next_test"),
        WhichKeyItem("p", "Next paragraph", "goto_next_paragraph"),
        WhichKeyItem("g", "Next change", "goto_next_change", "VcsShowNextChangeMarker"),
        WhichKeyItem("G", "Last change", "goto_last_change"),
        WhichKeyItem("C", "Next conflict", "goto_next_conflict", "NextConflict"),
        WhichKeyItem("e", "Next entry", "goto_next_entry"),
        WhichKeyItem("s", "Next spelling error", "goto_next_spelling_error", "GotoNextSpellingError"),
        WhichKeyItem("Space", "Add newline below", "add_newline_below"),
        WhichKeyItem("b", "Next buffer / tab", "goto_next_buffer", "NextTab"),
        WhichKeyItem("x", "Next XML element", "goto_next_xml_element"),
    )

    val viewItems = listOf(
        WhichKeyItem("z", "Center view", "align_view_center"),
        WhichKeyItem("c", "Center view", "align_view_center"),
        WhichKeyItem("t", "Align view top", "align_view_top"),
        WhichKeyItem("b", "Align view bottom", "align_view_bottom"),
        WhichKeyItem("m", "Align view middle (horiz)", "align_view_middle"),
        WhichKeyItem("j", "Scroll view down", "scroll_down"),
        WhichKeyItem("k", "Scroll view up", "scroll_up"),
        WhichKeyItem("d", "Scroll half page down", "page_cursor_half_down"),
        WhichKeyItem("u", "Scroll half page up", "page_cursor_half_up"),
        WhichKeyItem("f", "Scroll page down", "page_down"),
        WhichKeyItem("F", "Scroll page up", "page_up"),
    )

    val windowItems = listOf(
        WhichKeyItem("v", "Vertical split", "vsplit", "SplitVertically"),
        WhichKeyItem("s", "Horizontal split", "hsplit", "SplitHorizontally"),
        WhichKeyItem("nv", "New vertical scratch split", "vsplit_new"),
        WhichKeyItem("ns", "New horizontal scratch split", "hsplit_new"),
        WhichKeyItem("f", "Goto file (horizontal split)", "goto_file_hsplit", "SplitHorizontally"),
        WhichKeyItem("F", "Goto file (vertical split)", "goto_file_vsplit", "SplitVertically"),
        WhichKeyItem("t", "Transpose split orientation", "transpose_view", "ChangeSplitOrientation"),
        WhichKeyItem("h", "Focus left", "jump_view_left", "PrevSplitter"),
        WhichKeyItem("j", "Focus below", "jump_view_down", "NextSplitter"),
        WhichKeyItem("k", "Focus above", "jump_view_up", "PrevSplitter"),
        WhichKeyItem("l", "Focus right", "jump_view_right", "NextSplitter"),
        WhichKeyItem("w", "Cycle next window", "jump_next_view", "NextSplitter"),
        WhichKeyItem("W", "Cycle previous window", "jump_prev_view", "PrevSplitter"),
        WhichKeyItem("H", "Swap split orientation (left)", "swap_view_left", "ChangeSplitOrientation"),
        WhichKeyItem("J", "Swap split orientation (down)", "swap_view_down", "ChangeSplitOrientation"),
        WhichKeyItem("K", "Swap split orientation (up)", "swap_view_up", "ChangeSplitOrientation"),
        WhichKeyItem("L", "Swap split orientation (right)", "swap_view_right", "ChangeSplitOrientation"),
        WhichKeyItem("q", "Close active split", "wclose", "Unsplit"),
        WhichKeyItem("c", "Close active split", "wclose", "Unsplit"),
        WhichKeyItem("o", "Close other splits", "wonly", "UnsplitAll"),
        WhichKeyItem("m", "Maximize active split", "wmaximize", "MaximizeEditorInSplit"),
    )

    fun getRegisterItems(editor: Editor? = null): List<WhichKeyItem> {
        val items = mutableListOf<WhichKeyItem>()
        addStandardRegisterItems(items, editor)
        addDeleteRegisterItems(items, editor)
        addNamedRegisterItems(items, editor)
        addSpecialRegisterItems(items, editor)
        return items
    }

    private fun addStandardRegisterItems(items: MutableList<WhichKeyItem>, editor: Editor?) {
        val defaultEntry = HelixRegisterManager.get('"', editor)
        items.add(
            WhichKeyItem(
                key = "\"",
                label = "Default register",
                helixCommand = "default_register",
                snippet = HelixRegisterItem.formatSnippet(defaultEntry),
            ),
        )

        val yankEntry = HelixRegisterManager.get('0', editor)
        items.add(
            WhichKeyItem(
                key = "0",
                label = "Last yank",
                helixCommand = "yank_register",
                snippet = HelixRegisterItem.formatSnippet(yankEntry),
            ),
        )
    }

    private fun addDeleteRegisterItems(items: MutableList<WhichKeyItem>, editor: Editor?) {
        var hasDeleteRegs = false
        for (i in 1..9) {
            val entry = HelixRegisterManager.get(('0'.code + i).toChar(), editor)
            if (entry != null) {
                hasDeleteRegs = true
                items.add(
                    WhichKeyItem(
                        key = i.toString(),
                        label = "Delete $i",
                        helixCommand = "delete_register_$i",
                        snippet = HelixRegisterItem.formatSnippet(entry),
                    ),
                )
            }
        }
        if (!hasDeleteRegs) {
            items.add(
                WhichKeyItem(
                    key = "1-9",
                    label = "Numbered registers",
                    helixCommand = "numbered_registers",
                    snippet = "(empty)",
                ),
            )
        }
    }

    private fun addNamedRegisterItems(items: MutableList<WhichKeyItem>, editor: Editor?) {
        var hasNamedRegs = false
        for (c in 'a'..'z') {
            val entry = HelixRegisterManager.get(c, editor)
            if (entry != null) {
                hasNamedRegs = true
                items.add(
                    WhichKeyItem(
                        key = c.toString(),
                        label = "Named '$c'",
                        helixCommand = "named_register_$c",
                        snippet = HelixRegisterItem.formatSnippet(entry),
                    ),
                )
            }
        }
        if (!hasNamedRegs) {
            items.add(
                WhichKeyItem(
                    key = "a-z",
                    label = "Named registers",
                    helixCommand = "named_registers",
                    snippet = "(empty)",
                ),
            )
        }
    }

    private fun addSpecialRegisterItems(items: MutableList<WhichKeyItem>, editor: Editor?) {
        val clipEntry = HelixRegisterManager.getFromClipboard()
        items.add(
            WhichKeyItem(
                key = "+",
                label = "System clipboard",
                helixCommand = "system_clipboard",
                snippet = HelixRegisterItem.formatSnippet(clipEntry),
            ),
        )
        items.add(
            WhichKeyItem(
                key = "*",
                label = "Primary selection",
                helixCommand = "primary_selection",
                snippet = HelixRegisterItem.formatSnippet(clipEntry),
            ),
        )

        val searchPattern = HelixSearchActions.lastSearchPattern
        val searchEntry = searchPattern?.let { HelixRegisterEntry(text = it) }
        items.add(
            WhichKeyItem(
                key = "/",
                label = "Search register",
                helixCommand = "search_register",
                snippet = HelixRegisterItem.formatSnippet(searchEntry),
            ),
        )

        val bufName = editor?.virtualFile?.name
        items.add(
            WhichKeyItem(
                key = "%",
                label = "Buffer name",
                helixCommand = "buffer_name_register",
                snippet = bufName ?: "(none)",
            ),
        )

        val lastInsert = HelixInsertTracker.lastInsertedText
        val insertEntry = lastInsert?.let { HelixRegisterEntry(text = it) }
        items.add(
            WhichKeyItem(
                key = ".",
                label = "Last inserted text",
                helixCommand = "last_insert_register",
                snippet = HelixRegisterItem.formatSnippet(insertEntry),
            ),
        )

        val caretCount = editor?.caretModel?.caretCount ?: 1
        items.add(
            WhichKeyItem(
                key = "#",
                label = "Selection index",
                helixCommand = "selection_index_register",
                snippet = "$caretCount sel",
            ),
        )

        items.add(
            WhichKeyItem(
                key = "_",
                label = "Black hole",
                helixCommand = "black_hole",
                snippet = "(discard)",
            ),
        )
    }

    val textObjectItems = listOf(
        WhichKeyItem("w", "Word", "word_textobject"),
        WhichKeyItem("W", "WORD", "big_word_textobject"),
        WhichKeyItem("p", "Paragraph", "paragraph_textobject"),
        WhichKeyItem("c", "Comment", "comment_textobject"),
        WhichKeyItem("f", "Function", "function_textobject"),
        WhichKeyItem("t", "Type / Class", "type_textobject"),
        WhichKeyItem("T", "Test", "test_textobject"),
        WhichKeyItem("a", "Argument", "argument_textobject"),
        WhichKeyItem("x", "XML/HTML element", "element_textobject"),
        WhichKeyItem("g", "VCS change", "diff_hunk_textobject"),
        WhichKeyItem("i", "Indentation", "indent_textobject"),
        WhichKeyItem("e", "Entire buffer", "buffer_textobject"),
        WhichKeyItem("m", "Closest pair", "enclosing_pair_textobject"),
    )

    val debugItems = listOf(
        WhichKeyItem("b", "Toggle breakpoint", "dap_toggle_breakpoint", "ToggleLineBreakpoint"),
        WhichKeyItem("c", "Continue / Resume", "dap_continue", "Resume"),
        WhichKeyItem("s", "Step in", "dap_step_in", "StepInto"),
        WhichKeyItem("n", "Step over (next)", "dap_next", "StepOver"),
        WhichKeyItem("o", "Step out", "dap_step_out", "StepOut"),
        WhichKeyItem("p", "Pause", "dap_pause", "Pause"),
        WhichKeyItem("l", "Launch debug", "dap_launch", "Debug"),
        WhichKeyItem("r", "Restart", "dap_restart", "Rerun"),
        WhichKeyItem("R", "Run without debugger", "run", "RunClass"),
        WhichKeyItem("t", "Terminate", "dap_terminate", "Stop"),
        WhichKeyItem("v", "Variables / Debug window", "dap_variables", "ActivateDebugToolWindow"),
        WhichKeyItem("k", "Evaluate expression", "dap_evaluate", "EvaluateExpression"),
        WhichKeyItem("e", "Edit breakpoint", "dap_edit_condition", "EditBreakpoint"),
        WhichKeyItem("B", "View breakpoints", "dap_view_breakpoints", "ViewBreakpoints"),
    )

    val refactorItems = listOf(
        WhichKeyItem("v", "Extract variable", "extract_variable", "IntroduceVariable"),
        WhichKeyItem("m", "Extract method", "extract_method", "ExtractMethod"),
        WhichKeyItem("c", "Extract constant", "extract_constant", "IntroduceConstant"),
        WhichKeyItem("f", "Extract field", "extract_field", "IntroduceField"),
        WhichKeyItem("p", "Extract parameter", "extract_parameter", "IntroduceParameter"),
        WhichKeyItem("i", "Inline symbol", "inline", "Inline"),
        WhichKeyItem("s", "Change signature", "change_signature", "ChangeSignature"),
        WhichKeyItem("r", "Rename symbol", "rename_symbol", "RenameElement"),
        WhichKeyItem("M", "Move symbol / file", "move", "Move"),
        WhichKeyItem("w", "Surround with template", "surround_with", "SurroundWith"),
        WhichKeyItem("o", "Override methods", "override_methods", "OverrideMethods"),
        WhichKeyItem("I", "Implement methods", "implement_methods", "ImplementMethods"),
        WhichKeyItem("g", "Generate code", "generate", "Generate"),
        WhichKeyItem("d", "Safe delete", "safe_delete", "SafeDelete"),
        WhichKeyItem("u", "Pull members up", "pull_up", "MembersPullUp"),
        WhichKeyItem("D", "Push members down", "push_down", "PushMembersDown"),
        WhichKeyItem("?", "Refactor this menu", "refactor_this", "Refactorings.QuickListPopupAction"),
    )

    val testItems = listOf(
        WhichKeyItem("t", "Run test at caret", "test_run", "RunClass"),
        WhichKeyItem("r", "Run test at caret", "test_run", "RunClass"),
        WhichKeyItem("d", "Debug test at caret", "test_debug", "DebugClass"),
        WhichKeyItem("l", "Rerun last execution", "test_rerun", "Rerun"),
        WhichKeyItem("f", "Rerun failed tests", "test_rerun_failed", "RerunFailedTests"),
        WhichKeyItem("c", "Run with coverage", "test_coverage", "RunCoverage"),
        WhichKeyItem("a", "Toggle test / subject", "goto_test", "GotoTest"),
    )

    val vcsItems = listOf(
        WhichKeyItem("r", "Rollback change hunk", "vcs_rollback_hunk", "Vcs.RollbackChangedLines"),
        WhichKeyItem("d", "Show diff", "vcs_diff", "Compare.SameVersion"),
        WhichKeyItem("h", "File history", "vcs_file_history", "Vcs.ShowTabbedFileHistory"),
        WhichKeyItem("s", "Git stash changes", "vcs_stash", "Git.Stash"),
        WhichKeyItem("u", "Git unstash changes", "vcs_unstash", "Git.Unstash"),
        WhichKeyItem("b", "Git blame / annotate", "vcs_blame", "Annotate"),
        WhichKeyItem("c", "Commit changes", "vcs_commit", "CheckinProject"),
        WhichKeyItem("p", "Push commits", "vcs_push", "Vcs.Push"),
        WhichKeyItem("P", "Pull / update project", "vcs_pull", "Vcs.UpdateProject"),
        WhichKeyItem("l", "VCS / Git log", "vcs_log", "Vcs.Show.Log"),
        WhichKeyItem("B", "Git branches", "vcs_branches", "Git.Branches"),
        WhichKeyItem("?", "VCS operations popup", "vcs_menu", "Vcs.QuickListPopupAction"),
    )

    fun getMenu(prefix: String, editor: Editor? = null): Pair<String, List<WhichKeyItem>>? =
        getMenu(prefix, HelixMode.NORMAL, editor)

    fun getMenu(prefix: String, mode: HelixMode, editor: Editor? = null): Pair<String, List<WhichKeyItem>>? {
        val base = getBaseMenu(prefix, editor)
        val branchChildren = HelixKeymapManager.getBranchChildren(mode, prefix)

        if (base == null && branchChildren == null) {
            return null
        }

        val title = base?.first ?: "${prefix.trim().uppercase()} MENU"
        val baseItems = base?.second ?: emptyList()

        if (branchChildren == null) {
            return title to baseItems
        }

        return title to mergeMenuItems(baseItems, branchChildren)
    }

    private fun getBaseMenu(prefix: String, editor: Editor? = null): Pair<String, List<WhichKeyItem>>? = when (prefix) {
        " " -> HelixBundle.message("whichKey.menu.space") to spaceItems
        " G", "space G", "G", "dap" -> HelixBundle.message("whichKey.menu.debug") to debugItems
        " m", "space m", "refactor" -> HelixBundle.message("whichKey.menu.refactor") to refactorItems
        " t", "space t", "test" -> HelixBundle.message("whichKey.menu.test") to testItems
        " v", "space v", "vcs" -> HelixBundle.message("whichKey.menu.vcs") to vcsItems
        "g" -> HelixBundle.message("whichKey.menu.goto") to gotoItems
        "m" -> HelixBundle.message("whichKey.menu.match") to matchItems
        "ma" -> HelixBundle.message("whichKey.menu.selectAround") to textObjectItems
        "mi" -> HelixBundle.message("whichKey.menu.selectInside") to textObjectItems
        "[" -> HelixBundle.message("whichKey.menu.jumpBack") to bracketOpenItems
        "]" -> HelixBundle.message("whichKey.menu.jumpForward") to bracketCloseItems
        "z" -> HelixBundle.message("whichKey.menu.view") to viewItems
        "Z" -> HelixBundle.message("whichKey.menu.stickyView") to viewItems
        "C-w", "Ctrl+w", "\u0017", " w", "space w" -> HelixBundle.message("whichKey.menu.window") to windowItems
        "\"" -> HelixBundle.message("whichKey.menu.registers") to getRegisterItems(editor)
        "C-r", "Ctrl+r", "\u0012" -> HelixBundle.message("whichKey.menu.insertRegister") to getRegisterItems(editor)
        else -> null
    }

    private fun mergeMenuItems(
        baseItems: List<WhichKeyItem>,
        branchChildren: Map<HelixKeyStroke, HelixKeymapNode>,
    ): List<WhichKeyItem> {
        val result = baseItems.toMutableList()
        for ((stroke, child) in branchChildren) {
            val keyStr = stroke.toDisplayString()
            val action = child.action ?: ""
            val label = child.label ?: HelixCommandRegistry.get(action)?.description ?: action
            val intelliJ = child.intelliJAction
                ?: HelixCommandRegistry.get(action)?.intelliJAction
                ?: extractIntelliJAction(action)

            val existingIndex = result.indexOfFirst { it.key == keyStr }
            if (existingIndex >= 0) {
                val baseItem = result[existingIndex]
                if (child.action != null && action != baseItem.helixCommand) {
                    result[existingIndex] = WhichKeyItem(keyStr, label, action, intelliJ)
                }
            } else if (label.isNotEmpty() || action.isNotEmpty()) {
                result.add(WhichKeyItem(keyStr, label, action, intelliJ))
            }
        }
        return result
    }

    private fun extractIntelliJAction(action: String): String =
        if (action.startsWith(":action ")) action.removePrefix(":action ").trim() else ""
}
