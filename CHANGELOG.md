# Changelog

All notable changes to **Helix Keymap (T68k)** are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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

### Fixed

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
