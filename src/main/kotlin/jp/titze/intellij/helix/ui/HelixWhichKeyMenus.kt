package jp.titze.intellij.helix.ui

data class WhichKeyItem(
    val key: String,
    val label: String,
    val helixCommand: String = "",
    val intelliJAction: String = "",
) {
    val description: String get() = helixCommand
}

object HelixWhichKeyMenus {

    val spaceItems = listOf(
        WhichKeyItem("b", "Buffer / Tab picker", "buffer_picker", "RecentFiles"),
        WhichKeyItem("f", "File picker", "file_picker", "GotoFile"),
        WhichKeyItem("e", "File explorer", "file_explorer", "ActivateProjectToolWindow"),
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
        WhichKeyItem("c", "Toggle line comment", "toggle_comments", "CommentByLineComment"),
        WhichKeyItem("C", "Toggle block comment", "toggle_block_comments", "CommentByBlockComment"),
        WhichKeyItem("h", "Select references", "select_references", "FindUsages"),
        WhichKeyItem("'", "Last picker", "last_picker"),
        WhichKeyItem("w", "Save", "w", "SaveAll"),
        WhichKeyItem("y", "Yank main selection", "yank_to_clipboard"),
        WhichKeyItem("p", "Paste clipboard after", "paste_clipboard_after"),
        WhichKeyItem("P", "Paste clipboard before", "paste_clipboard_before"),
        WhichKeyItem("R", "Replace with clipboard", "replace_clipboard"),
        WhichKeyItem("k", "Hover / Documentation", "hover", "QuickJavaDoc"),
        WhichKeyItem("?", "Command palette", "command_palette", "GotoAction"),
    )

    val gotoItems = listOf(
        WhichKeyItem("d", "Goto definition", "goto_definition", "GotoDeclaration"),
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
        WhichKeyItem("c", "Goto window center", "goto_window_center"),
        WhichKeyItem("b", "Goto window bottom", "goto_window_bottom"),
        WhichKeyItem("f", "Goto file at caret", "goto_file"),
        WhichKeyItem("|", "Goto column", "goto_column <count>"),
        WhichKeyItem("w", "Jump to word", "goto_word"),
        WhichKeyItem("g", "Goto line / file start", "goto_line_start_file"),
        WhichKeyItem("e", "Goto file end", "goto_last_line"),
        WhichKeyItem("a", "Last accessed file", "goto_last_accessed_file"),
        WhichKeyItem("m", "Last modified file", "goto_last_modified_file"),
        WhichKeyItem("j", "Move down visual line", "move_visual_line_down"),
        WhichKeyItem("k", "Move up visual line", "move_visual_line_up"),
    )

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
        WhichKeyItem("Space", "Add newline above", "add_newline_above"),
        WhichKeyItem("b", "Previous buffer / tab", "goto_previous_buffer", "PreviousTab"),
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
        WhichKeyItem("Space", "Add newline below", "add_newline_below"),
        WhichKeyItem("b", "Next buffer / tab", "goto_next_buffer", "NextTab"),
    )

    val viewItems = listOf(
        WhichKeyItem("c", "Center view", "align_view_center"),
        WhichKeyItem("t", "Align view top", "align_view_top"),
        WhichKeyItem("b", "Align view bottom", "align_view_bottom"),
        WhichKeyItem("m", "Align view middle (horiz)", "align_view_middle"),
        WhichKeyItem("j", "Scroll view down", "scroll_down"),
        WhichKeyItem("k", "Scroll view up", "scroll_up"),
        WhichKeyItem("d", "Scroll half page down", "half_page_down"),
        WhichKeyItem("u", "Scroll half page up", "half_page_up"),
        WhichKeyItem("f", "Scroll page down", "page_down"),
        WhichKeyItem("F", "Scroll page up", "page_up"),
        WhichKeyItem("z", "Center view", "align_view_center"),
    )

    val windowItems = listOf(
        WhichKeyItem("v", "Vertical split", "vsplit", "SplitVertically"),
        WhichKeyItem("s", "Horizontal split", "hsplit", "SplitHorizontally"),
        WhichKeyItem("h", "Focus left", "jump_view_left", "PrevSplitter"),
        WhichKeyItem("j", "Focus below", "jump_view_down", "NextSplitter"),
        WhichKeyItem("k", "Focus above", "jump_view_up", "PrevSplitter"),
        WhichKeyItem("l", "Focus right", "jump_view_right", "NextSplitter"),
        WhichKeyItem("w", "Cycle next window", "jump_next_view", "NextSplitter"),
        WhichKeyItem("q", "Close active split", "wclose", "Unsplit"),
        WhichKeyItem("c", "Close active split", "wclose", "Unsplit"),
        WhichKeyItem("o", "Close other splits", "wonly", "UnsplitAll"),
    )

    val registerItems = listOf(
        WhichKeyItem("_", "Black hole", "black_hole"),
        WhichKeyItem("\"", "Default register", "default_register"),
        WhichKeyItem("+", "System clipboard", "system_clipboard"),
        WhichKeyItem("*", "Primary selection", "primary_selection"),
        WhichKeyItem("0-9", "Numbered registers", "numbered_registers"),
        WhichKeyItem("a-z", "Named registers", "named_registers"),
        WhichKeyItem("/", "Search register", "search_register"),
        WhichKeyItem("%", "Buffer name", "buffer_name_register"),
    )

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

    fun getMenu(prefix: String): Pair<String, List<WhichKeyItem>>? = when (prefix) {
        " " -> "SPACE MENU" to spaceItems
        "g" -> "GOTO MENU" to gotoItems
        "m" -> "MATCH MENU" to matchItems
        "ma" -> "SELECT AROUND" to textObjectItems
        "mi" -> "SELECT INSIDE" to textObjectItems
        "[" -> "JUMP BACK MENU" to bracketOpenItems
        "]" -> "JUMP FORWARD MENU" to bracketCloseItems
        "z" -> "VIEW MENU" to viewItems
        "Z" -> "STICKY VIEW MENU" to viewItems
        "C-w", "Ctrl+w", "\u0017" -> "WINDOW MENU" to windowItems
        "\"" -> "REGISTERS" to registerItems
        "C-r", "Ctrl+r", "\u0012" -> "INSERT REGISTER" to registerItems
        else -> null
    }
}
