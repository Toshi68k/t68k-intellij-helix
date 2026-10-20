// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import jp.titze.intellij.helix.state.HelixMode

object HelixDefaultKeymap {

    fun buildDefaultTrie(mode: HelixMode): HelixKeymapTrie {
        val trie = HelixKeymapTrie()
        when (mode) {
            HelixMode.NORMAL, HelixMode.SELECT -> populateNormalAndSelect(trie)
            HelixMode.INSERT -> populateInsert(trie)
        }
        return trie
    }

    private fun populateNormalAndSelect(trie: HelixKeymapTrie) {
        populateSingleKeyMotions(trie)
        populateSingleKeyActions(trie)
        populateSpaceChords(trie)
        populateGotoChords(trie)
        populateViewChords(trie)
        populateWindowChords(trie)
    }

    private fun bind(trie: HelixKeymapTrie, seq: String, cmd: String, label: String? = null) {
        val strokes = parseKeySequence(seq)
        trie.bind(strokes, cmd, label)
    }

    private fun parseKeySequence(seq: String): List<HelixKeyStroke> {
        val parts = seq.trim().split("\\s+".toRegex())
        return parts.map { HelixKeyStroke.fromString(it) }
    }

    private fun populateSingleKeyMotions(trie: HelixKeymapTrie) {
        bind(trie, "h", "move_char_left", "Move left")
        bind(trie, "j", "move_line_down", "Move down")
        bind(trie, "k", "move_line_up", "Move up")
        bind(trie, "l", "move_char_right", "Move right")
        bind(trie, "w", "move_next_word_start", "Next word start")
        bind(trie, "b", "move_prev_word_start", "Previous word start")
        bind(trie, "e", "move_next_word_end", "Next word end")
        bind(trie, "W", "move_next_long_word_start", "Next long word start")
        bind(trie, "B", "move_prev_long_word_start", "Previous long word start")
        bind(trie, "E", "move_next_long_word_end", "Next long word end")
        bind(trie, "x", "extend_line", "Select line")
        bind(trie, "X", "extend_to_line_bounds", "Extend to line bounds")
        bind(trie, "%", "select_all", "Select all")
        bind(trie, ";", "collapse_selection", "Collapse selection")
        bind(trie, ",", "keep_primary_selection", "Keep primary selection")
        bind(trie, "(", "rotate_selections_backward", "Rotate selections backward")
        bind(trie, ")", "rotate_selections_forward", "Rotate selections forward")
        bind(trie, "C", "copy_selection_on_next_line", "Copy selection on next line")
        bind(trie, "C-f", "page_down", "Page down")
        bind(trie, "C-b", "page_up", "Page up")
        bind(trie, "C-d", "half_page_down", "Half page down")
        bind(trie, "C-u", "half_page_up", "Half page up")
        bind(trie, "C-o", "jump_backward", "Jump backward")
        bind(trie, "C-s", "save_selection", "Save jump location")
        bind(trie, "C-a", "increment", "Increment number")
        bind(trie, "C-x", "decrement", "Decrement number")
    }

    private fun populateSingleKeyActions(trie: HelixKeymapTrie) {
        bind(trie, "d", "delete_selection", "Delete selection")
        bind(trie, "c", "change_selection", "Change selection")
        bind(trie, "y", "yank", "Yank selection")
        bind(trie, "p", "paste_after", "Paste after")
        bind(trie, "P", "paste_before", "Paste before")
        bind(trie, "R", "replace_with_yanked", "Replace with yanked")
        bind(trie, "J", "join_lines", "Join lines")
        bind(trie, "i", "insert_mode", "Insert mode")
        bind(trie, "a", "append_mode", "Append mode")
        bind(trie, "I", "insert_at_line_start", "Insert line start")
        bind(trie, "A", "insert_at_line_end", "Insert line end")
        bind(trie, "o", "open_below", "Open line below")
        bind(trie, "O", "open_above", "Open line above")
        bind(trie, "v", "select_mode", "Select mode")
        bind(trie, "u", "undo", "Undo")
        bind(trie, "U", "redo", "Redo")
        bind(trie, "K", "keep_selections", "Keep matching selections")
        bind(trie, "=", "format_selections", "Format selections")
        bind(trie, ">", "indent", "Indent")
        bind(trie, "<", "unindent", "Unindent")
        bind(trie, "~", "toggle_case", "Toggle case")
        bind(trie, "`", "switch_to_lowercase", "Switch to lowercase")
        bind(trie, "_", "trim_selections", "Trim whitespace")
        bind(trie, "&", "align_selections", "Align selections")
        bind(trie, "s", "select_regex", "Select regex")
        bind(trie, "S", "split_regex", "Split regex")
        bind(trie, "/", "search", "Search forward")
        bind(trie, "?", "rsearch", "Search backward")
        bind(trie, "n", "search_next", "Next match")
        bind(trie, "N", "search_prev", "Previous match")
        bind(trie, "*", "search_selection", "Search selection")
        bind(trie, ".", "repeat_last_insert", "Repeat insert")
        bind(trie, "|", "shell_pipe", "Pipe through shell")
        bind(trie, "!", "shell_insert", "Insert from shell")
        bind(trie, "$", "shell_keep_pipe", "Filter through shell")
    }

    private fun populateSpaceChords(trie: HelixKeymapTrie) {
        bind(trie, "space f", "file_picker", "File picker")
        bind(trie, "space F", "file_picker_in_current_directory", "File picker in current directory")
        bind(trie, "space b", "buffer_picker", "Buffer / tab picker")
        bind(trie, "space /", "global_search", "Global search")
        bind(trie, "space j", "jumplist_picker", "Jumplist picker")
        bind(trie, "space s", "symbol_picker", "Symbol picker")
        bind(trie, "space S", "workspace_symbol_picker", "Workspace symbol picker")
        bind(trie, "space d", "diagnostics_picker", "Diagnostics picker")
        bind(trie, "space D", "workspace_diagnostics", "Workspace diagnostics")
        bind(trie, "space e", "file_explorer", "File explorer")
        bind(trie, "space E", "file_explorer_buffer", "Buffer directory explorer")
        bind(trie, "space .", "file_explorer_buffer", "Buffer directory explorer")
        bind(trie, "space g", "changed_file_picker", "Changed file picker")
        bind(trie, "space c", "toggle_comments", "Toggle line comment")
        bind(trie, "space C", "toggle_block_comments", "Toggle block comment")
        bind(trie, "space a", "code_action", "Code action")
        bind(trie, "space r", "rename_symbol", "Rename symbol")
        bind(trie, "space y", "yank_to_clipboard", "Yank to clipboard")
        bind(trie, "space Y", "yank_main_selection_to_clipboard", "Yank primary selection")
        bind(trie, "space p", "paste_clipboard_after", "Paste clipboard after")
        bind(trie, "space P", "paste_clipboard_before", "Paste clipboard before")
        bind(trie, "space R", "replace_clipboard", "Replace with clipboard")
        bind(trie, "space k", "hover", "Hover / Documentation")
        bind(trie, "space ?", "command_palette", "Command palette")
    }

    private fun populateGotoChords(trie: HelixKeymapTrie) {
        bind(trie, "g d", "goto_definition", "Goto definition")
        bind(trie, "g D", "goto_declaration", "Goto declaration")
        bind(trie, "g i", "goto_implementation", "Goto implementation")
        bind(trie, "g y", "goto_type_definition", "Goto type definition")
        bind(trie, "g r", "goto_reference", "Goto reference")
        bind(trie, "g n", "goto_next_buffer", "Next buffer")
        bind(trie, "g p", "goto_previous_buffer", "Previous buffer")
        bind(trie, "g .", "goto_last_change", "Jump to last change")
        bind(trie, "g h", "goto_line_start", "Goto line start")
        bind(trie, "g l", "goto_line_end", "Goto line end")
        bind(trie, "g s", "goto_first_nonwhitespace", "Goto first non-whitespace")
        bind(trie, "g t", "goto_window_top", "Goto window top")
        bind(trie, "g c", "goto_window_center", "Goto window center")
        bind(trie, "g b", "goto_window_bottom", "Goto window bottom")
        bind(trie, "g f", "goto_file", "Goto file at caret")
        bind(trie, "g g", "goto_file_start", "Goto file start")
        bind(trie, "g e", "goto_last_line", "Goto last line")
        bind(trie, "g a", "goto_last_accessed_file", "Last accessed file")
        bind(trie, "g m", "goto_last_modified_file", "Last modified file")
    }

    private fun populateViewChords(trie: HelixKeymapTrie) {
        bind(trie, "z z", "align_view_center", "Center view")
        bind(trie, "z c", "align_view_center", "Center view")
        bind(trie, "z t", "align_view_top", "Align view top")
        bind(trie, "z b", "align_view_bottom", "Align view bottom")
        bind(trie, "z m", "align_view_middle", "Align view middle")
        bind(trie, "z j", "scroll_down", "Scroll view down")
        bind(trie, "z k", "scroll_up", "Scroll view up")
    }

    private fun populateWindowChords(trie: HelixKeymapTrie) {
        bind(trie, "C-w v", "vsplit", "Vertical split")
        bind(trie, "C-w s", "hsplit", "Horizontal split")
        bind(trie, "C-w t", "transpose_view", "Transpose split")
        bind(trie, "C-w h", "jump_view_left", "Focus left split")
        bind(trie, "C-w j", "jump_view_down", "Focus below split")
        bind(trie, "C-w k", "jump_view_up", "Focus above split")
        bind(trie, "C-w l", "jump_view_right", "Focus right split")
        bind(trie, "C-w w", "jump_next_view", "Next split")
        bind(trie, "C-w W", "jump_prev_view", "Previous split")
        bind(trie, "C-w q", "wclose", "Close split")
        bind(trie, "C-w c", "wclose", "Close split")
        bind(trie, "C-w o", "wonly", "Close other splits")
    }

    private fun populateInsert(trie: HelixKeymapTrie) {
        bind(trie, "C-s", ":w", "Save")
        bind(trie, "C-h", "delete_char_backward", "Delete char backward")
        bind(trie, "C-d", "delete_char_forward", "Delete char forward")
        bind(trie, "C-w", "delete_word_backward", "Delete word backward")
        bind(trie, "C-u", "kill_to_line_start", "Kill to line start")
        bind(trie, "C-k", "kill_to_line_end", "Kill to line end")
        bind(trie, "C-r", "insert_register", "Insert register")
        bind(trie, "C-p", "signature_help", "Signature help")
        bind(trie, "C-x", "completion", "Completion")
    }
}
