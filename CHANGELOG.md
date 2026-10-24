# Changelog

All notable changes to **Helix Keymap (T68k)** are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- **Tabbed Settings & Visual Keymap Explorer**:
  - Replaced the monolithic vertical settings layout with a modern `JBTabbedPane` featuring 4 organized tabs: **General**, **Keybindings**, **Ctrl Keys**, and **Config File (TOML)**.
  - **Structured General Settings Tab (Tab 1)**:
    - Grouped settings into 3 cohesive domains using native IntelliJ `TitledSeparator` dividers:
      1. **Activation & Editor Scope**: Master activation switch, editor targets (Diff, Console, Commit, Other), and Color Theme.
      2. **Editing & Navigation Behavior**: Line navigation (`gh`/`gl` vs `0`/`$`), Scratch splits, Clipboard synchronization, Tab-switch Normal reset, and Jump list capacity.
      3. **Visual Prompts & Feedback**: Search prompt UI style & history size, Which-Key popup hints (hint mode, column layout).

  - **Visual Keybindings Explorer (Tab 2)**:
    - Interactive, searchable table displaying all active runtime bindings from `HelixKeymapManager` across Normal, Select, and Insert modes.
    - Chords filter dropdown (All, Space, Goto, View, Match, Window, Bracket, Single keys & other).
    - Real-time text search filtering across shortcut combinations, canonical Helix commands, and action descriptions.
    - **Find Shortcut by Keystroke (`[⌨]`)**: Shortcut recorder dialog capturing keystrokes and chords (e.g. `space f`, `g d`, `C-s`) to instantly locate bindings.
    - **IntelliJ Action Discovery (`[ 🔍 Browse Actions... ]`)**: Searchable action picker dialog (`HelixActionPickerDialog`) allowing users to discover, search, and copy `:action <ActionId>` commands for any registered IntelliJ IDE action with shortcut and description preview.
    - Provenance badges identifying binding sources (`[Default]`, `[custom: intellij]`, `[custom: config]`, `[no_op]`).
    - Quick actions to open active TOML configuration file or refresh table data.
    - Visual conflict warnings (`⚠`) highlighting shadowed keybindings where a shortcut cannot fire due to child chords taking precedence.
  - **Keymap Trie Extractor (`HelixKeymapExtractor`)**: Flattens nested prefix tries into tabular rows with human-readable chord formatting and deterministic provenance detection.
  - **Prefix Collision & Trie Shadowing Detector (`HelixKeymapConflictDetector`)**:
    - Automated prefix trie analysis detecting shadowed bindings where a single key has an action assigned but is masked by child chords (e.g. binding `g` or `space` directly when child chords `gd`, `gh`, `space f` exist).
    - Integrated into `HelixConfigLoader.validateConfig()`: TOML files containing shadowed shortcuts are automatically flagged with conflict diagnostics and child chord lists.

## [0.3.0]

### Added

- **[BETA] Configurable Keymaps (`config.toml`)**:
  - Authentic Helix-style key rebinding via `~/.config/helix/config.toml` (or custom path configured in IDE Settings).
  - Pure-Kotlin, zero-dependency TOML parser supporting `[keys.normal]`, `[keys.insert]`, `[keys.select]`, nested submode prefixes (`[keys.normal.space]`, `[keys.normal.g]`, `[keys.normal.z]`, `[keys.normal."C-w"]`), and top-level shorthand tables (`[keys.space]`, `[keys.goto]`, `[keys.view]`, `[keys.match]`, `[keys.window]`).
  - Word modifier support in key sequences (e.g. `ctrl-s`, `control-s`, `alt-x`, `shift-tab`, `meta-f`, `insert`).
  - Comprehensive canonical Helix command registry parity:
    - **Motions & Jumps**: `move_char_left`, `move_line_down`, `page_down`, `half_page_down`, `goto_line_start`, etc.
    - **Unimpaired Navigation**: `goto_prev_paragraph`, `goto_next_paragraph`, `goto_prev_comment`, `goto_next_comment`, `goto_prev_function`, `goto_next_function`, `goto_prev_class`, `goto_next_class`, `goto_prev_diag`, `goto_next_diag`, `goto_prev_change`, `goto_next_change`, `goto_prev_xml_element`, `goto_next_xml_element`.
    - **Surround, Match & Textobjects**: `match_brackets`, `surround_add`, `surround_replace`, `surround_delete`, `select_textobject_around`, `select_textobject_inner`.
    - **Selection Grooming**: `flip_selections`, `ensure_selections_forward`, `clear_selection`, `remove_primary_selection`, `merge_selections`, `merge_consecutive_selections`, `copy_selection_on_prev_line`, `expand_selection`, `shrink_selection`, `select_next_sibling`, `select_prev_sibling`.
    - **Insert Mode Commands**: `delete_char_backward`, `delete_char_forward`, `insert_newline`, `delete_word_backward`, `delete_word_forward`, `kill_to_line_start`, `kill_to_line_end`, `insert_register`, `signature_help`, `completion`.
    - **Numbers & Math**: `increment` (`C-a`), `decrement` (`C-x`).
  - Multi-command macro sequences (`["half_page_down", "align_view_center"]`).
  - Direct IntelliJ action execution via `:action <ActionId>`.
  - Key unbinding / disabling via `"no_op"`.
  - Dynamic Which-Key integration: custom keybindings and chord overlays automatically reflect in Which-Key hint menus in real time.
  - Live configuration validation: unknown commands or invalid syntax are detected and reported as diagnostics.
- **Automatic Live Reloading (VFS File Watcher)**:
  - Built-in `BulkFileListener` file watcher automatically monitors `config.toml` changes (saved inside IntelliJ or by external editors) and reloads keymaps live with debounced status bar feedback.
  - Added `:config-reload` / `:reload-config` command palette actions for on-demand keymap reloading.
  - Added `:config-open` command palette action to open or initialize the active configuration file (automatically generating a commented starter template if missing).
- **Keymap Configuration Settings UI**:
  - Added dedicated **Keymap Configuration File** section under **Settings &rarr; Tools &rarr; Helix Keymap**.
  - `TextFieldWithBrowseButton` file chooser restricted to `.toml` files with placeholder hint for default discovery.
  - Dedicated `~/.config/helix/intellij.toml` configuration target prevents IDE-specific `:action` commands from causing syntax errors in terminal Helix (`hx`), while retaining `config.toml` as an automatic fallback.
  - Live CLI conflict warning in the settings panel when pointing to `config.toml` to guard terminal dotfiles from inadvertent breakage.
  - Quick action controls: **Open in Editor**, **Reload Keymap**, and **Reset to Default**.
  - Live status indicator showing active configuration file path, loaded binding counts, or syntax and validation diagnostics.

## [0.2.2]

### Fixed

- **IDE Restart Requirement**:
  - Explicitly declared `require-restart="true"` in `plugin.xml` so the IntelliJ Plugin Manager prompts the user to restart the IDE upon installation or update, ensuring global typed action handlers, editor listeners, and caret states are reliably initialized across all open editors.
  - Updated installation instructions in `README.md` to instruct restarting the IDE after installation.

## [0.2.1]

### Added

- **Scratch Window Splits**:
  - Added `<Ctrl+w n s>` / `<space w n s>` (`hsplit_new`) and `<Ctrl+w n v>` / `<space w n v>` (`vsplit_new`) chords to create new scratches in splits.
  - Added configurable scratch mode preference: Interactive dialog (`NewScratchFile`) vs. instant plain scratch buffer (`NewScratchBuffer`).
  - Added `:toggle-scratch-mode`, `:scratch-mode`, `:set scratch-mode=interactive`, and `:set scratch-mode=buffer` palette commands.
- **Directional Navigation Keys**:
  - Arrow keys (<kbd>←</kbd>, <kbd>→</kbd>, <kbd>↑</kbd>, <kbd>↓</kbd>) in Normal mode now behave as modal motions matching `h`, `l`, `k`, `j` (respecting visual line settings and count prefixes).
  - <kbd>Home</kbd> and <kbd>End</kbd> in Normal mode navigate to line start / line end.
- **Select Mode Search Caret Extension**:
  - In Select mode, `n` (`search_next`) and `N` (`search_prev`) now extend carets by keeping existing selections and adding next/previous matches (`extend_search_next` / `extend_search_prev`).
- **XML / HTML Tag Navigation**:
  - Added `[x` and `]x` unimpaired chords to jump to previous and next XML/HTML tags and elements (`goto_prev_xml_element` / `goto_next_xml_element`).
- **Last Insert Register**:
  - Added `".` register recording text inserted during the most recent insert sequence.
- **Selection Join & Yank**:
  - Added `:yank-join` command palette action to join all active multi-caret selections with a separator and copy to clipboard/register.
- **Command Palette Aliases**:
  - Added `:update` / `:u` alias for `SaveAll`.
  - Added `:exit` / `:xit` alias for save and exit (`:wq` / `:x`).
- **Developer Workflow & Execution Commands**:
  - Added `:run` / `:run-context` (`RunClass`), `:run-target` (`Run`), `:rerun` (`Rerun`), `:build` / `:make` / `:compile` (`CompileDirty`), and `:stop` / `:terminate` / `:kill` (`Stop`).
  - Added `<space + G R>` chord to run context configuration without debugger (`RunClass`).
- **Git & VCS Palette Commands**:
  - Added `:blame` / `:annotate` (`Annotate`), `:diff` / `:show-diff` (`Compare.SameVersion`), `:commit` (`CheckinProject`), `:push` (`Vcs.Push`), `:pull` (`Vcs.UpdateProject`), and `:vcs-log` (`Vcs.Show.Log`).
- **Code Navigation & Hierarchy Commands**:
  - Added `:goto-implementation` (`GotoImplementation`), `:goto-type-definition` (`GotoTypeDeclaration`), `:goto-reference` (`FindUsages`), `:goto-super` (`GotoSuperMethod`), `:call-hierarchy` (`CallHierarchy`), `:type-hierarchy` (`TypeHierarchy`), and `:file-structure` (`FileStructurePopup`).
- **Refactoring & Code Generation Commands**:
  - Added `:extract-variable` (`IntroduceVariable`), `:extract-method` (`ExtractMethod`), `:inline` (`Inline`), `:change-signature` (`ChangeSignature`), `:generate` (`Generate`), and `:replace-in-path` (`ReplaceInPath`).

### Fixed

- **Single-Line Indentation and Count Prefixes (`>` / `<`)**:
  - Fixed single-line indentation with `>` (`indent`) failing to execute when no text was actively selected. Switched action dispatch from selection-only `EditorIndentSelection` to `EditorIndentLineOrSelection`, mirroring `<` (`EditorUnindentSelection`) to indent either the current line or active selections.
  - Added count prefix support (`repeat(count)`) for both `>` and `<` (e.g. `2>` or `2<` to repeat indentation adjustments).
- **Numeric Count Prefixes on Motions and Selections (`[count]`)**:
  - Fixed an issue in `HelixEventDispatcher` where `KEY_PRESSED` events on non-arrow keys eagerly consumed and cleared pending numeric counts before the subsequent `KEY_TYPED` event reached `HelixKeyHandler`. Count prefixes on standard motions (e.g. `5j`, `3k`, `3x`, `3X`, `2w`, `3b`, `2e`, `2fa`, etc.) now reliably repeat motions and line selections $N$ times.
  - Fixed line selection downwards extension in `HelixMotions.selectLine` to safely maintain positive selection ranges regardless of initial selection orientation.
- **Helix View Mode Compliance (`z` / `Z`)**:
  - Fixed `zc` to vertically center the line in the viewport (`align_view_center`), matching the official Helix
    specification where both `z` and `c` center the view.
  - Removed accidental Vim folding chords (`zc`, `zf`, `zo`, `zM`, `zR`) from View mode so typing view chords
    no longer inadvertently collapses or expands code regions. Code folding remains fully accessible via
    `:fold`, `:unfold`, `:fold-all`, and `:unfold-all` commands.
  - Added support for `f` / `Ctrl+f` (`page_down`), `d` / `Ctrl+d` (`page_cursor_half_down`), and `u` / `Ctrl+u`
    (`page_cursor_half_up`) in View mode.
- **Alt Modifier Dual-Registration in Event Dispatcher**:
  - Closed dual-registration gaps in `HelixEventDispatcher` for `Alt-s` (split selection on newlines),
    `Alt-;` (flip selection anchor and cursor), `Alt-,` (remove primary selection), `Alt-C` (copy selection
    on previous line), `Alt-o` / `Alt-up` (expand selection), and `Alt-i` / `Alt-down` (shrink selection).
  - Keystrokes with Alt/Option modifiers on macOS and Linux are now reliably intercepted at root event level,
    preventing special character input (e.g. `ß`) from interfering with selection operations.
- **Search & Regex Prompt Bar Cancellation**:
  - Pressing <kbd>Ctrl+c</kbd>, <kbd>Esc</kbd>, or <kbd>Ctrl+[</kbd> inside the active search or regex prompt bar now cleanly dismisses the bar without triggering line comments (`CommentByLineComment`) on the editor.
  - Non-cancel keystrokes typed inside active prompts flow directly to the prompt input field without triggering Normal mode bindings.
- **Editor Disposal Safety**:
  - Guarded prompt bar cancellation and caret restoration against disposed editors during tab switching, closing, and test fixture teardown.
- **Recursive Caret Iteration on New Line Insertion (`o` / `O`)**:
  - Fixed an `IllegalStateException: Recursive runForEachCaret invocations are not allowed` when pressing `o`
    (`open_below` / `EditorStartNewLine`) or `O` (`open_above` / `EditorStartNewLineBefore`). The editor now transitions
    to Insert mode before dispatching the new line action, preventing `HelixEditorActionHandler` from intercepting
    the internal Enter action as a normal-mode selection collapse.
  - Configured `HelixEditorActionHandler` with `runForEachCaret = false` and guarded per-caret invocations during
    Enter handling to avoid nested caret iterations.

## [0.1.0]

### Added

- First public Marketplace release.
- Selection-first Helix modal editing (Normal / Insert / Select) on top of IntelliJ's native multi-caret model.
- Motions, text objects (`mi`/`ma`), surround (`ms`/`mr`/`md`), PSI-based structural selection and navigation.
- Registers (named, clipboard, black hole, selection index), macros, jumplist, and `.` repeat.
- Which-Key popups for `Space`, `g`, `m`, `[`, `]`, `z`, `Z`, `Ctrl-w` and `"`.
- `:` command palette, shell pipes (`|`, `!`, `$`), regex select / split / keep / remove.
- Master **Enable Helix mode** switch and *Toggle Helix Mode* action (`:toggle-helix`).
- Configurable editor scope: code editors, diff viewers, consoles, commit message, other embedded editors.
- Configurable **Ctrl key handling** per key and mode, with platform-aware defaults
  (Insert-mode Ctrl keys go to the IDE on Windows/Linux).

### Changed

- Minimum supported IDE version is now 2025.1.
- Ctrl and Escape keys are no longer registered in the global keymap; they are handled only inside Helix-active editors.

### Removed

- Incompatible with IdeaVim; both plugins replace the editor's typed-key handler.
