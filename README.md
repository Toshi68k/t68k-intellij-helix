# Helix Keymap for IntelliJ IDEA

[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](https://github.com/t68k/t68k-intellij-helix)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2024.2+-000000.svg?logo=intellij-idea&logoColor=white)](https://plugins.jetbrains.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.4-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/Java-17%20%7C%2021+-ED8B00.svg?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Helix](https://img.shields.io/badge/Modal-Helix-03C7D3.svg)](https://helix-editor.com)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A modal editing plugin for JetBrains IDEs implementing the [Helix](https://helix-editor.com) editor's **selection-first paradigm**, deeply integrated with IntelliJ's native IDE intelligence, AST capabilities, and refactoring engines.

The goal of this plugin is to provide a more complete and polished Helix-like experience than what is currently available with the [Vim](https://www.vim.org) plugin. This plugin attempts to provide an experience that is as close to the original Helix editor as possible, while still being deeply integrated with IntelliJ's native IDE capabilities. It is currently under development and is not yet complete.

- **Plugin ID**: `jp.titze.intellij.helix`
- **Vendor**: [Thorsten Titze](https://github.com/Toshi68k)
- **Target IDE**: IntelliJ IDEA 2024.2+ (Community & Ultimate) and JetBrains IDEs

---

## Key Features

- **Selection-First Paradigm**: In Helix, motions first create active selections, and actions operate directly on those selections.
- **Native Multi-Caret**: All motions and actions natively operate across all active carets simultaneously using IntelliJ's `CaretModel` and `SelectionModel`.
- **Deep IDE Intelligence**: No bespoke reimplementations—all code navigation, symbol search, hover documentation, diagnostics, and refactoring directly invoke native IntelliJ actions via `ActionManager`.
- **Modal Editing with Visual Feedback**:
  - **Normal** (`NOR`): Block cursor, selection motions, action triggers.
  - **Insert** (`INS`): Standard bar cursor, raw typing delegated to IntelliJ.
  - **Select** (`SEL`): Block cursor, motions extend selections from anchor.
  - **Status Bar Widget**: Displays active mode and pending key chords (e.g., `NOR`, `NOR g-`, `NOR  -`).
- **Command Palette (`:`)**: Lightweight command prompt supporting standard Helix buffer commands (`:w`, `:q`, `:wq`, `:wa`, `:qa`, `:vsp`, `:sp`, `:format`, `:increment`, `:decrement`).

---

## Helix Keymap Reference

### Motions (Create / Extend Selections)

| Key | Description |
|-----|-------------|
| `f<char>` | Move to next occurrence of `<char>` (inclusive, searches across lines) |
| `t<char>` | Move till next occurrence of `<char>` (exclusive, stops before `<char>`, supports `<Enter>`) |
| `F<char>` | Move to previous occurrence of `<char>` (backward, inclusive) |
| `T<char>` | Move till previous occurrence of `<char>` (backward, exclusive, stops after `<char>`) |
| `Alt+.` | Repeat last motion (`f`, `t`, `F`, `T`, `mm`, `[`/`]`) (`repeat_last_motion`) |
| `*` | Search for selection or word under cursor with word boundaries (`search_selection`) |
| `Alt+*` | Search for selection or word under cursor without word boundaries |
| `w` | Advance to the start of the next word |
| `b` | Move backward to the start of the previous word |
| `e` | Advance to the end of the current/next word |
| `W` | Advance to the start of the next WORD (non-whitespace chunk) |
| `B` | Move backward to the start of the previous WORD |
| `E` | Advance to the end of the current/next WORD |
| `ge` | Move backward to the end of the previous word |
| `x` | Select current line (including newline); pressing `x` again extends to the next line (`extend_line_below`) |
| `X` | Extend selection to whole line bounds including trailing newline (`extend_to_line_bounds`) |
| `Alt+x` | Shrink selection to line bounds excluding trailing line breaks (`shrink_to_line_bounds`) |
| `%` | Select entire buffer |
| `h` / `j` / `k` / `l` | Move left / down / up / right (mode-aware selection update) |
| `gh` | Move to line start (actual first character) |
| `gs` | Move to first non-whitespace character of line |
| `gl` | Move to line end |
| `gw` | Jump to visible word with 2-letter badge overlays (`goto_word`) |
| `gg` | Move to the top of the buffer |
| `ge` *(in `g` menu)* | Move to the end of the buffer |
| `gt` | Move to top line of visible window viewport (`goto_window_top`) |
| `gc` | Move to center line of visible window viewport (`goto_window_center`) |
| `gb` | Move to bottom line of visible window viewport (`goto_window_bottom`) |
| `g\|` / `<count>\|` | Move to column within line (1-indexed, default start) (`goto_column`) |
| `Ctrl+f` / `PageDown` | Move page down |
| `Ctrl+b` / `PageUp` | Move page up |
| `Ctrl+d` | Move half page down |
| `Ctrl+u` | Move half page up |

### Jumplist Navigation

| Key | Action | Description |
|---|---|---|
| `Ctrl+o` | `HelixJumpBackward` | Jump backward in the jump list (supports count) |
| `Ctrl+i` | `HelixJumpForward` | Jump forward in the jump list (supports count) |
| `Ctrl+s` | `HelixSaveJump` | Save current selection to the jump list manually (Normal/Select mode) |
| `space + j` | `HelixJumplistPopup` | Open interactive jumplist picker popup |

### Selection Manipulation

| Key | Description |
|-----|-------------|
| `_` | Trim leading and trailing whitespace from every active selection (`trim_selections`) |
| `&` | Align multi-caret selections into vertical columns by inserting padding whitespace (`align_selections`) |
| `Alt+k` | Prompt for regex pattern and keep only matching selections (`keep_selections`) |
| `Alt+K` | Prompt for regex pattern and remove matching selections (`remove_selections`) |
| `Alt+:` | Ensure selections are oriented forward with anchor $\le$ cursor (`ensure_selections_forward`) |
| `Alt+_` | Merge contiguous (touching) or overlapping selections into single spans (`merge_consecutive_selections`) |
| `Alt+-` | Merge all active selections from earliest to latest into a single selection (`merge_all_selections`) |
| `Alt+J` | Join lines inside selection and select the joined space (`join_selections_space`) |
| `Alt+)` | Rotate text contents forward between multi-carets without moving caret positions (`rotate_selections_contents_forward`) |
| `Alt+(` | Rotate text contents backward between multi-carets without moving caret positions (`rotate_selections_contents_backward`) |
| `C` | Copy selection to next line (duplicate selection and add caret below) |
| `Alt+C` | Copy selection to previous line (duplicate selection and add caret above) |
| `;` | Collapse selection to a single cursor at caret |
| `Alt+;` | Flip selection anchor and cursor |
| `,` | Remove secondary carets, keeping only primary caret |
| `Alt+,` | Remove primary caret, keeping secondary carets |
| `(` | Rotate main selection backward |
| `)` | Rotate main selection forward |
| `Alt+s` | Split selection on newlines |
| `s` | Select all regex matches inside selections |
| `S` | Split selection into subselections on regex matches |
| `v` | Toggle between `Normal` and `Select` mode |

### Shell Filtering & Piping

| Key | Helix Action | Command | Description |
|---|---|---|---|
| `\|` | `shell_pipe` | `:pipe [cmd]` | Pipe each selection into an external shell command and replace with its stdout |
| `!` | `shell_insert_output` | `:insert-output [cmd]` | Execute a shell command and insert its stdout before selections |
| `Alt+!` | `shell_append_output` | `:append-output [cmd]` | Execute a shell command and insert its stdout after selections |
| `$` | `shell_keep_pipe` | `:keep-pipe [cmd]` | Filter selections by piping into a shell command, keeping only exit code 0 |
| `Alt+\|` | `shell_pipe_to` | `:pipe-to [cmd]` | Pipe each selection into a shell command ignoring its output |

### Actions on Selection

| Key | Description |
|-----|-------------|
| `d` | Delete active selection & copy to clipboard |
| `Alt+d` | Delete active selection without copying to clipboard (`delete_selection_noyank`) |
| `c` | Delete active selection, copy to clipboard, and enter `Insert` mode |
| `Alt+c` | Delete active selection without copying to clipboard, and enter `Insert` mode (`change_selection_noyank`) |
| `y` | Yank (copy) active selection to clipboard |
| `p` | Paste clipboard after selection / caret |
| `P` | Paste clipboard before selection / caret |
| `r<char>` | Replace each selected character (or character under cursor) with `<char>` |
| `R` | Replace selection (or character under cursor) with clipboard / yanked text |
| `J` | Join lines inside selection, or join current line with line below |
| `.` | Repeat last insert sequence across all active carets (`repeat_last_insert`) |
| `i` | Enter `Insert` mode at start of selection |
| `a` | Enter `Insert` mode after caret / selection |
| `I` | Enter `Insert` mode at line start (first non-blank) |
| `A` | Enter `Insert` mode at line end |
| `o` | Insert new line below (`EditorStartNewLine`) and enter `Insert` mode |
| `O` | Insert new line above (`EditorStartNewLineBefore`) and enter `Insert` mode |
| `u` | Undo (`$Undo`) |
| `U` | Redo (`$Redo`) |
| `Q` | Start / stop recording keyboard macro (`StartStopMacroRecording`, `record-macro`) |
| `q` | Replay last recorded macro (`PlaybackLastMacro`, `replay-macro`, supports `[count]`) |
| `Ctrl+s` *(in `Insert` mode)* | Commit undo checkpoint (`commit-undo-checkpoint`) |
| `=` | Reformat code (`ReformatCode`) |
| `>` / `<` | Indent / Unindent selection |
| `~` | Switch case of selection (`switch_case`) |
| `` ` `` | Switch selection to lowercase (`switch_to_lowercase`) |
| `Alt+` `` | Switch selection to uppercase (`switch_to_uppercase`) |
| `Ctrl+a` | Increment integer under cursor or within selection (`increment`, supports `[count]`) |
| `Ctrl+x` | Decrement integer under cursor or within selection (`decrement`, supports `[count]`) |
| `Escape` | Return to `Normal` mode / clear pending chords |

### Insert Mode & Terminal / Readline Shortcuts

Essential insert-mode operations matching standard Helix and terminal/readline workflows:

| Key | Helix Command | Description |
|---|---|---|
| `Ctrl+w` / `Alt+Backspace` | `delete_word_backward` | Delete previous word backward without triggering IntelliJ's expand selection |
| `Alt+d` / `Alt+Delete` | `delete_word_forward` | Delete next word forward |
| `Ctrl+u` | `kill_to_line_start` | Delete from cursor to start of current line |
| `Ctrl+k` | `kill_to_line_end` | Delete from cursor to end of current line (deletes newline if at line end) |
| `Ctrl+r <char>` | `insert_register` | Insert contents of specified register while typing |
| `Ctrl+x` | `completion` | Trigger explicit code completion menu (`CodeCompletion`) |
| `Ctrl+s` | `commit-undo-checkpoint` | Commit undo checkpoint to break typing history |


### Surround & Match Mode (`m` menu)

Built-in surround and textobject functionality matching [Helix Surround](https://docs.helix-editor.com/surround.html) and [Helix Textobjects](https://docs.helix-editor.com/textobjects.html):

| Key | Action | Description |
|-----|--------|-------------|
| `ms<char>` | `surround_add` | Surround active selection (or single character under cursor) with delimiter `<char>` |
| `mr<from><to>` | `surround_replace` | Replace closest enclosing surround pair `<from>` with `<to>` |
| `md<char>` | `surround_delete` | Delete closest enclosing surround pair `<char>` |
| `mm` | `match_bracket` | Jump to matching bracket |
| `ma<object>` | `select_textobject_around` | Select **around** the textobject (e.g. `maw`, `maW`, `map`, `ma(`, `mam`, `maa`) |
| `mi<object>` | `select_textobject_inside` | Select **inside** the textobject (e.g. `miw`, `miW`, `mip`, `mi(`, `mim`, `mia`) |

#### Textobjects Supported
- **`w`** &rarr; Word (`miw` selects inner word; `maw` selects word + trailing or leading whitespace)
- **`W`** &rarr; WORD (`miW` selects non-whitespace token; `maW` selects token + whitespace)
- **`p`** &rarr; Paragraph (`mip` selects paragraph lines; `map` includes blank lines)
- **`i`** &rarr; Indentation block (`mii` / `mai` selects all lines indented at the same or deeper level)
- **`e`** &rarr; Entire buffer (`mie` / `mae` selects the entire document)
- **Delimiters**: `(`, `[`, `{`, `<`, `"`, `'`, `` ` `` (e.g. `mi(` selects inside parens; `ma(` includes parens)
- **`m`** &rarr; Closest enclosing pair / quote (`mim` inside closest pair; `mam` around closest pair)
- **`a`** &rarr; Argument / parameter (`mia` inside parameter; `maa` includes delimiter/comma)
- **`f`** &rarr; Function / method (PSI-aware; falls back to enclosing `{...}`)
- **`t`** &rarr; Type / class (PSI-aware; falls back to enclosing `{...}`)
- **`T`** &rarr; Test method (PSI-aware; `miT` selects body, `maT` includes annotations/signature)
- **`x`** &rarr; XML / HTML element (`mix` inside tag content, `max` around full tag)
- **`g`** &rarr; VCS change / diff hunk (`mig` inside change lines, `mag` around change hunk)
- **`c`** &rarr; Comment (PSI-aware comment node or line comment)

#### Supported Delimiters & Aliases
- **Pairs**: `()` (alias `b` or `p`), `[]` (alias `r`), `{}` (alias `B` or `c`), `<>` (alias `a`)
- **Quotes**: `"` (double quote), `'` (single quote), `` ` `` (backtick), or `q` (any quote)
- **Arbitrary Characters**: Any arbitrary delimiter such as `*` (e.g. `*bold*`), `_`, `~`, `/`, etc.
- **Multi-Caret**: Works seamlessly across all active carets simultaneously.

### Deep IntelliJ IDE Integrations

#### Navigation (`g` menu)
- `gd` &rarr; `goto_definition` (`GotoDeclaration`)
- `gi` &rarr; `goto_implementation` (`GotoImplementation`)
- `gy` &rarr; `goto_type_definition` (`GotoTypeDeclaration`)
- `gr` &rarr; `goto_reference` (`FindUsages`)
- `gn` &rarr; `goto_next_buffer` (`NextTab`)
- `gp` &rarr; `goto_previous_buffer` (`PreviousTab`)
- `g.` &rarr; `goto_last_change` (`JumpToLastChange`)
- `gh` &rarr; `goto_line_start` (Line start)
- `gs` &rarr; `goto_first_nonwhitespace` (First non-whitespace character)
- `gl` &rarr; `goto_line_end` (Line end)
- `gt` &rarr; `goto_window_top` (Top visible line in viewport)
- `gc` &rarr; `goto_window_center` (Middle visible line in viewport)
- `gb` &rarr; `goto_window_bottom` (Bottom visible line in viewport)
- `gf` &rarr; `goto_file` (Goto file at caret)
- `g|` &rarr; `goto_column <count>` (Goto column within line)
- `gw` &rarr; `goto_word` (Jump to word on screen with overlay badges)
- `ge` &rarr; `goto_last_line` (Goto end of buffer)
- `gg` &rarr; `goto_line_start_file` (Goto start of buffer)
- `ga` &rarr; `goto_last_accessed_file` (Last accessed file / alternate buffer)
- `gm` &rarr; `goto_last_modified_file` (Last modified file in project)
- `gj` &rarr; `move_visual_line_down` (Move down by visual screen line)
- `gk` &rarr; `move_visual_line_up` (Move up by visual screen line)

#### Interactive Which-Key Floating Menu
Whenever a chord prefix key (<kbd>Space</kbd>, `Ctrl+w`, `g`, `m`, `[`, or `]`) is pressed in Normal mode, an interactive, non-intrusive **Which-Key popup** appears in authentic Helix cyan:
- **Zero latency**: Muscle memory stays instant—typing the follow-up key immediately executes the command without waiting.
- **Visual discoverability**: Pausing on any chord reveals all available pickers and actions in authentic Helix `snake_case`.
- **Interactive hint toggle**: Pressing <kbd>Tab</kbd> or clicking the header badge dynamically toggles between Helix commands and IntelliJ Action IDs in real time.
- **Action inspection on hover**: Hovering over any row displays a tooltip with the underlying IntelliJ Action ID.
- **Configurable**: Can be enabled/disabled or configured for default hint mode via **Settings &rarr; Helix Keymap**.
- **Single-key & click dispatch**: Pressing any highlighted key or clicking any option directly executes the action.
- **Cancelable**: Pressing <kbd>Esc</kbd> or clicking outside dismisses the menu and restores `Normal` mode.

#### Window & Split Management (`Ctrl+w`)
| Key | Helix Command | IntelliJ Action | Description |
|-----|---------------|-----------------|-------------|
| `Ctrl+w v` | `vsplit` | `SplitVertically` | Vertical editor split |
| `Ctrl+w s` | `hsplit` | `SplitHorizontally` | Horizontal editor split |
| `Ctrl+w h` | `jump_view_left` | `PrevSplitter` | Focus split to the left |
| `Ctrl+w j` | `jump_view_down` | `NextSplitter` | Focus split below |
| `Ctrl+w k` | `jump_view_up` | `PrevSplitter` | Focus split above |
| `Ctrl+w l` | `jump_view_right` | `NextSplitter` | Focus split to the right |
| `Ctrl+w w` | `jump_next_view` | `NextSplitter` | Cycle focus to next split window |
| `Ctrl+w q` / `Ctrl+w c` | `wclose` | `Unsplit` | Close active split |
| `Ctrl+w o` | `wonly` | `UnsplitAll` | Close all other splits |

*(Holding Ctrl during chords, e.g. `Ctrl+w Ctrl+v`, `Ctrl+w Ctrl+w`, etc. is also fully supported.)*

#### Pickers & Space Menu (`space`)
| Key | Helix Command | IntelliJ Action | Description |
|-----|---------------|-----------------|-------------|
| `space + f` | `file_picker` | `GotoFile` | Dedicated fuzzy file picker |
| `space + b` | `buffer_picker` | `RecentFiles` | Open buffer / tab switcher |
| `space + /` | `global_search` | `FindInPath` | Live project-wide text search (live grep) with preview |
| `space + j` | `jumplist_picker` | — | Open interactive jumplist picker popup |
| `space + e` | `file_explorer` | `ActivateProjectToolWindow` | Open / toggle project file explorer tool window |
| `space + .` | `file_explorer_buffer` | `SelectInProjectView` | Reveal active buffer file in project explorer tree |
| `space + g` | `changed_file_picker` | `ActivateVersionControlToolWindow` | Open / toggle Git version control changes window |
| `space + s` | `symbol_picker` | `FileStructurePopup` | Document symbols / outline picker |
| `space + S` | `workspace_symbol_picker` | `GotoSymbol` | Workspace-wide symbol picker across AST |
| `space + d` | `diagnostics_picker` | `ShowErrorDescription` | Diagnostic error inspection under caret |
| `space + D` | `workspace_diagnostics_picker` | `ActivateProblemsViewToolWindow` | Workspace diagnostics (IntelliJ Problems panel) |
| `space + a` | `code_action` | `ShowIntentionActions` | Code actions & quick-fixes (Alt+Enter) |
| `space + r` | `rename_symbol` | `RenameElement` | Refactor rename symbol |
| `space + c` | `toggle_comments` | `CommentByLineComment` | Toggle line comments on selection or line |
| `space + C` | `toggle_block_comments` | `CommentByBlockComment` | Toggle block comments on selection |
| `space + h` | `select_references_to_symbol_under_cursor` | `FindUsages` | Find symbol references / usages across project |
| `space + w` | `w` | `SaveAll` | Save all modified buffers |
| `space + y` | `yank_main_selection_to_clipboard` | — | Yank active selection to system clipboard |
| `space + p` | `paste_clipboard_after` | — | Paste system clipboard after cursor / selection |
| `space + P` | `paste_clipboard_before` | — | Paste system clipboard before cursor / selection |
| `space + R` | `replace_selections_with_clipboard` | — | Replace current selections with system clipboard |
| `space + k` | `hover` | `QuickJavaDoc` | Hover documentation popup |
| `space + '` | `last_picker` | — | Re-open last active space picker |
| `space + ?` | `command_palette` | `GotoAction` | Action / command palette picker |

#### Unimpaired Navigation (`[` / `]`)
| Forward (`]`) | Backward (`[`) | Helix Command | Description |
| :--- | :--- | :--- | :--- |
| `]f` | `[f` | `goto_next_function` / `goto_prev_function` | Next / previous function or method |
| `]c` | `[c` | `goto_next_comment` / `goto_prev_comment` | Next / previous comment |
| `]t` | `[t` | `goto_next_class` / `goto_prev_class` | Next / previous class or type |
| `]a` | `[a` | `goto_next_parameter` / `goto_prev_parameter` | Next / previous parameter |
| `]T` | `[T` | `goto_next_test` / `goto_prev_test` | Next / previous test method |
| `]p` | `[p` | `goto_next_paragraph` / `goto_prev_paragraph` | Next / previous paragraph (blank line) |
| `]g` | `[g` | `goto_next_change` / `goto_prev_change` | Next / previous VCS change marker |
| `]G` | `[G` | `goto_last_change` / `goto_first_change` | Last / first VCS change marker |
| `]d` | `[d` | `goto_next_diag` / `goto_prev_diag` | Next / previous diagnostic error |
| `]D` | `[D` | `goto_last_diag` / `goto_first_diag` | Last / first diagnostic error |
| `]Space` | `[Space` | `add_newline_below` / `add_newline_above` | Add empty line below / above |
| `]b` | `[b` | `goto_next_buffer` / `goto_prev_buffer` | Next / previous editor tab |

#### Code AST & Inspection
- `Alt+o` &rarr; Expand structural selection via PSI hierarchy (`SelectWordAtCaret`)
- `Alt+i` &rarr; Shrink structural selection (`UnselectWordAtCaret`)
- `Alt+p` / `Alt+Left` &rarr; Select previous sibling AST element (`select_prev_sibling`)
- `Alt+a` &rarr; Select all sibling AST elements (`select_all_siblings`)
- `Alt+I` &rarr; Select all direct children AST elements (`select_all_children`)
- `Alt+n` &rarr; Select next occurrence (`SelectNextOccurrence`)
- `Ctrl+c` &rarr; Toggle line comment (`CommentByLineComment`)
- `K` &rarr; Quick documentation hover (`QuickJavaDoc`)

#### Command Picker (`:`)
Press `:` in Normal mode to open the interactive **Helix Command Picker**, styled with the signature cyan prompt (`:`), live fuzzy suggestions, and keyboard navigation (<kbd>↑</kbd>/<kbd>↓</kbd>, <kbd>Tab</kbd> to complete, <kbd>Enter</kbd> to execute):
- `:w` / `:write` &rarr; Save all files (`SaveAll`)
- `:q` / `:quit` &rarr; Close active tab (`CloseContent`)
- `:wq` / `:x` &rarr; Save all and close active tab
- `:wa` &rarr; Save all modified buffers (`SaveAll`)
- `:qa` &rarr; Close all editors (`CloseAllEditors`)
- `:vsp` / `:vsplit` &rarr; Split editor vertically
- `:sp` / `:hsplit` &rarr; Split editor horizontally
- `:unsplit` / `:only` &rarr; Close all other splits (`UnsplitAll`)
- `:close-split` / `:close` &rarr; Close active split (`Unsplit`)
- `:format` &rarr; Reformat code (`ReformatCode`)
- `:reload` / `:e!` &rarr; Reload file from disk (`SynchronizeCurrentFile`)
- `:open` / `:edit` / `:e [file]` &rarr; Open fuzzy file picker (`GotoFile`) or open file by path
- `:buffer` / `:b` &rarr; Open buffer switcher (`RecentFiles`)
- `:buffer-close` / `:bc` / `:bclose` &rarr; Close active tab (`CloseContent`)
- `:buffer-close-others` / `:bco` / `:bcloseother` &rarr; Close all other tabs (`CloseAllEditorsButActive`)
- `:buffer-close-all` / `:bca` / `:bcloseall` &rarr; Close all editor tabs (`CloseAllEditors`)
- `:buffer-next` / `:bn [count]` &rarr; Switch to next buffer / tab (`NextTab`)
- `:buffer-previous` / `:bp [count]` &rarr; Switch to previous buffer / tab (`PreviousTab`)
- `:new` / `:n` &rarr; Create new scratch buffer / file (`NewScratchFile`)
- `:find` &rarr; Find in project files (`FindInPath`)
- `:commit-undo-checkpoint` / `:checkpoint` &rarr; Commit undo checkpoint to break typing history
- `:toggle-search-ui` / `:search-ui` &rarr; Toggle between Stock Helix inline bar and Popup dialog
- `:set search-ui=inline` / `:set search-ui=stock` &rarr; Set search UI to Stock Helix inline bar
- `:set search-ui=popup` &rarr; Set search UI to Popup dialog
- `:jumps` &rarr; Open interactive jumplist picker
- `:increment` / `:inc` &rarr; Increment integer under cursor or within selection (`Ctrl+a`)
- `:decrement` / `:dec` &rarr; Decrement integer under cursor or within selection (`Ctrl+x`)
- `:trim-selections` / `:trim_selections` &rarr; Trim whitespace from selections (`_`)
- `:align-selections` / `:align_selections` &rarr; Align selections into columns by inserting whitespace (`&`)
- `:keep-selections` / `:keep_selections` &rarr; Filter selections by regex, keeping matching (`Alt+k`)
- `:remove-selections` / `:remove_selections` &rarr; Filter selections by regex, removing matching (`Alt+K`)
- `:ensure-selections-forward` / `:ensure_selections_forward` &rarr; Flip backward selections forward (`Alt+:`)
- `:merge-selections` / `:merge_consecutive_selections` &rarr; Merge contiguous or overlapping selections (`Alt+_`)
- `:merge-all-selections` &rarr; Merge all active selections from earliest to latest into a single selection (`Alt+-`)
- `:join-selections-space` &rarr; Join lines inside selection and select the joined space (`Alt+J`)
- `:delete-noyank` / `:d!` &rarr; Delete selection without copying to clipboard (`Alt+d`)
- `:change-noyank` / `:c!` &rarr; Change selection without copying to clipboard (`Alt+c`)
- `:repeat-last-motion` &rarr; Repeat the last recorded motion (`Alt+.`)
- `:rotate-selection-contents-forward` &rarr; Cycle text contents forward without moving carets (`Alt+)`)
- `:rotate-selection-contents-backward` &rarr; Cycle text contents backward without moving carets (`Alt+(`)
- `:extend-to-line-bounds` / `:extend_to_line_bounds` &rarr; Extend selection to whole line bounds (`X`)
- `:shrink-to-line-bounds` / `:shrink_to_line_bounds` &rarr; Shrink selection to line bounds excluding line breaks (`Alt+x`)
- `:record-macro` / `:macro-record` &rarr; Start or stop recording a keyboard macro (`Q`)
- `:replay-macro` / `:macro-play` &rarr; Replay the last recorded keyboard macro (`q`)
- `:sort` / `:sort -r` &rarr; Sort selected lines alphabetically or in reverse (whole buffer if no selection)
- `:pipe [cmd]` &rarr; Pipe each selection into external shell command and replace with stdout (`|`)
- `:pipe-to [cmd]` &rarr; Pipe each selection into external shell command ignoring stdout (`Alt+|`)
- `:insert-output [cmd]` &rarr; Execute shell command and insert stdout before selections (`!`)
- `:append-output [cmd]` &rarr; Execute shell command and insert stdout after selections (`Alt+!`)
- `:keep-pipe [cmd]` &rarr; Filter selections through shell command exit code (`$`)
- `:sh [cmd]` / `:run-shell-command [cmd]` / `:! [cmd]` &rarr; Run shell command asynchronously or toggle terminal (`ActivateTerminalToolWindow`)
- `:terminal` &rarr; Open / toggle IntelliJ's built-in terminal tool window
- `:cd [path]` / `:pwd` &rarr; Change or display current working directory (supports `~`, `-`, relative paths)

#### Search & Selection UI Modes
Helix Keymap supports two switchable search and regex prompt styles:
1. **Stock Helix Mode (Default)**: Single-line prompt bar docked at the bottom of the active editor (`search: `, `rsearch: `, `select: `, `split: `, `keep: `, `remove: `, `pipe: `, `insert-output: `, `append-output: `, `keep-pipe: `, `pipe-to: `). Matches and selections update live in the editor buffer as you type for search/regex modes, while shell operations execute safely upon pressing <kbd>Enter</kbd>. Pressing <kbd>Esc</kbd> (or <kbd>Backspace</kbd> on empty query) cancels and reverts all carets and selections to their pre-search snapshot.

2. **Popup Dialog Mode**: Centered floating dialog window with match counter badge, useful for users preferring a separate floating modal window.

- In IntelliJ Settings: **Preferences / Settings &rarr; Tools &rarr; Helix Keymap**
- In the `:` Command Picker: `:toggle-search-ui`, `:set search-ui=inline`, or `:set search-ui=popup`

#### Editor Behavior Settings
Configure Helix Keymap preferences under **Preferences / Settings &rarr; Tools &rarr; Helix Keymap**:
- **Which-Key Chord Menus**: Enable or disable Which-Key chord popups on chord prefixes (`Space`, `g`, `m`, `[`, `]`, `z`, `Ctrl+w`), choose default hint display style (Helix command names vs IntelliJ Action IDs), and set maximum columns (3 columns compact vs 2 columns classic).
- **Reset to Normal mode on tab switch / file open** *(default: enabled)*: Ensures each tab starts in **Normal** mode with block cursor whenever a file is opened or tabs are switched. Can be disabled if you prefer retaining active modes (such as Insert mode) across tabs.
- **Search and Selection Prompt UI**: Toggle between Stock Helix inline bottom bar or Popup dialog.
- **Jump List Capacity**: Set maximum recorded jump entries (10–1000).
- **Color Theme**: Choose between Sync with IDE, Dark, or Light themes for all Helix overlays.

---

## Installation

### From Pre-built Plugin Package
1. Build the distribution or download the `.zip` from `build/distributions/t68k-intellij-helix-0.1.0.zip`.
2. In IntelliJ IDEA, open **Settings / Preferences** (`Cmd+,` on macOS, `Ctrl+Alt+S` on Linux/Windows).
3. Navigate to **Plugins**.
4. Click the gear icon (⚙️) at the top-right and select **Install Plugin from Disk...**.
5. Select `t68k-intellij-helix-0.1.0.zip` and restart the IDE.

---

## Building from Source

### Prerequisites
- JDK 17 or 21+ (e.g., OpenJDK 21/24)
- Gradle 9.x (Gradle wrapper included)

### Build Distribution
```bash
./gradlew buildPlugin
```
The installable `.zip` will be produced at:
```
build/distributions/t68k-intellij-helix-0.1.0.zip
```

### Run Unit Tests
```bash
./gradlew test
```

### Run in Sandbox IDE
To launch an isolated instance of IntelliJ IDEA with the plugin enabled:
```bash
./gradlew runIde
```

---

## Project Structure

```
t68k-intellij-helix/
├── build.gradle.kts          # IntelliJ Platform Gradle Plugin 2.x build configuration
├── settings.gradle.kts
├── gradle.properties
├── src/
│   ├── main/
│   │   ├── kotlin/jp/titze/intellij/helix/
│   │   │   ├── action/       # Deletion, yanking, pasting, IntelliJ ActionManager delegation
│   │   │   ├── command/      # Lightweight : command palette popup
│   │   │   ├── editor/       # TypedActionHandler, Escape/Alt shortcuts, Editor listener
│   │   │   ├── jumplist/     # Jumplist service
│   │   │   ├── keymap/       # Sequence dispatching engine (g, space, [, ], count prefixes)
│   │   │   ├── motion/       # Word, line, and buffer motions with active selection semantics
│   │   │   ├── settings/     # Configuration panel and settings
│   │   │   ├── state/        # HelixMode (Normal, Insert, Select), state manager & cursor logic
│   │   │   └── ui/           # Status bar mode widget and factory
│   │   └── resources/
│   │       └── META-INF/
│   │           └── plugin.xml
│   └── test/
│       └── kotlin/jp/titze/intellij/helix/
│           ├── HelixEditingActionsTest.kt     # Tests for modal editing, insert, delete, and change actions
│           ├── HelixMotionsTest.kt            # Tests for word, line, count, page, find, and view motions
│           ├── HelixNavigationAndSearchTest.kt# Tests for search, commands, jumplist, and brackets
│           ├── HelixSelectionTest.kt          # Tests for multi-caret, surround, and text objects
│           └── settings/
│               └── HelixSettingsTest.kt       # Tests for plugin settings and configuration
└── README.md
```

---

## 🛠️ Development & Contributing

This simple extension was written out of my own need for a useful Helix-like IntelliJ extension 
after completely changing from Vim to Helix.

