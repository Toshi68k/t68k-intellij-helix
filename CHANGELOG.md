# Changelog

All notable changes to **Helix Keymap (T68k)** are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- **Project-Local Configuration (`.helix/intellij.toml` & `.helix/config.toml`)**:
  - Automatically discovers, loads, and merges repository-specific configuration from `<projectRoot>/.helix/intellij.toml` (falling back to `<projectRoot>/.helix/config.toml`) on top of global `~/.config/helix/intellij.toml`.
  - **Multi-Project Isolation**: Maintains project-isolated keymap tries and editor option caches keyed by project path. Multiple simultaneously open project windows never leak custom keybindings or editor overrides to each other or global state.
  - **Strict Layered Precedence Hierarchy**: Enforces `:set-option (session)` &rsaquo; `Project TOML (.helix/)` &rsaquo; `Global TOML (~/.config/helix/)` &rsaquo; `Settings UI (persisted XML)` &rsaquo; `Built-in Defaults`.
  - **Project Lifecycle & Safe Disposal**: Hooks into `HelixStartupActivity` on project open, refreshes active project editors on focus switch, and cleans up project tries upon project close via IntelliJ `Disposer`.
  - **Project Trust Guard**: Respects IntelliJ's project trust state (`TrustedProjects.isTrusted`), preventing untrusted repositories from executing unauthorized project action macros.
  - **Live VFS File Watcher**: `HelixConfigWatcher` monitors `.helix/` config files across open projects and triggers debounced live reloads upon saving.
  - **Keymap Browser & Settings Provenance**: Displays a distinct `[Custom: Project]` badge in the Keymap Browser for project-bound shortcuts; disabled Settings UI controls and Active Overrides tables highlight `.helix/` provenance (even when no global `intellij.toml` exists) with direct line navigation.
  - **Command Palette (`:config-open-project`)**: Added `:config-open-project` (aliases `:config-open-workspace`, `:project-config-open`, `:workspace-config-open`) to scaffold `.helix/intellij.toml` from a starter template and open it in the editor.
  
- **Tool Windows Navigation Extensions**:
  - **VCS Commit Tool Window (`Alt+0`) Shortcuts**: Added `Space` to toggle file checkboxes in Changelist mode or stage/unstage (`Git.Stage.Add` / `Git.Stage.Reset`) in Git Staging mode, `d` to open the diff preview (`Diff.ShowDiff`, also in `Version Control`), and `c` to focus the commit message box cleanly for typing.
  - **Two-Step SpeedSearch Confirmation, `?` Backward Search & `n`/`N` Navigation**: Added `?` to start backward SpeedSearch from the current row alongside `/` forward search, updated `n`/`N` to follow or reverse the active search direction (including after SpeedSearch closes), and confirmed the search on the first `Enter` without immediately executing/opening the selected item (letting a second `Enter` or `o` activate it), configurable via **Confirm speed search on Enter without executing (press Enter or o again to open/run)** (enabled by default).
  - **Seamless Window Focus Jump (`Ctrl+w` / `space + w`)**: Enhanced `h`/`j`/`k`/`l` window navigation with 2D spatial split resolution (`FileEditorManagerEx`) and boundary crossing into enabled, Helix-navigable docked tool windows (and `Ctrl+w h/j/k/l` / `Ctrl+w w` inside tool windows to move between adjacent tool windows or return focus to the editor, automatically skipping `Terminal` and non-navigable consoles), configurable via **Cross editor/tool window boundaries with Ctrl+w / space + w (h/j/k/l)**.
    
## [0.9.1] - 2026-11-25

### Added

- **Harpoon Persistence Across Restarts & Branch Switches**:
  - Pinned Harpoon slots (`space + 1..5`) now survive IDE restarts, project reopenings, and branch switches via a project-level `PersistentStateComponent` saved to `.idea/workspace.xml` (zero Git pollution).
  - **Lazy RangeMarker Rehydration**: Restores file URLs, line numbers, and preview snippets on project load, creating active `RangeMarker` tracking on demand without blocking startup.
  - **Dynamic Line Tracking**: Pinned line numbers are dynamically derived from active `RangeMarker`s, keeping line numbers accurate as code shifts.
  - **Non-Destructive Branch Switching**: Preserves slot definitions for files temporarily absent on the current Git branch, labeling them `[other branch]` in the Harpoon card UI and Which-Key rather than discarding them.
- **Functional Keys & Modifier Chords (`F1`–`F24`)**:
  - Full support for binding functional keys (`F1`–`F24`, including extended keys `F13`–`F24`) and modifier chords (`S-F13`, `C-F13`, `A-F12`, `C-S-F13`, etc.) across `[keys.normal]`, `[keys.select]`, and `[keys.insert]` in `intellij.toml` and `config.toml`.
  - Non-printable action key resolution via `HelixEventDispatcher`: function keys (which only emit `KEY_PRESSED` without `KEY_TYPED` in Java/Swing) resolve directly against the active keymap Trie.
  - Fail-open IDE passthrough: unmapped function keys (e.g. `Shift+F6` for Rename, `F2` for Next Highlighted Error) pass through untouched to IntelliJ's native keymap.
  - Full compatibility with remapped/virtual functional keys (e.g. AutoHotkey or Karabiner-Elements).
  - Supports numeric count prefixes (e.g. `3 F12`).


### Fixed

- **Inclusive Selection Model Matching Native Helix**:
  - Fixed an issue where the character under the cursor was excluded from active selections in `SELECT` mode and range-selecting motions (such as `e` and `E`).
  - **Precise Word-End Positioning (`e` / `E`)**: Removed trailing offset shift so the cursor lands directly on the actual final character of the word (e.g. on `c` in `public` rather than trailing whitespace) while maintaining an inclusive selection of the entire word.
  - **Inclusive Motions in Select Mode (`h` / `j` / `k` / `l` / `b` / `B` / `F` / `T` / visual lines)**: Moving in `SELECT` mode now treats the character under the cursor as part of the selection across all directions, backward word motions, backward find-char motions, and line movements.
  - **Select Mode Jump-to-Word (`v` + `gw`)**: Jumping backward across word boundaries in Select mode now inclusively retains the starting character under the cursor; jumping to the current word where the cursor is positioned selects the entire word.
  - **Symmetric Selection Flipping (`Alt+;`)**: Flipping selection orientation toggles the cursor seamlessly between `selectionStart` and `selectionEnd - 1` without dropping the boundary character or advancing past the selection range.

## [0.9.0] - 2026-11-24

### Added

- **Harpoon-Style Quick Slots & IntelliJ Bookmarks Integration**:
  - **Deterministic Muscle-Memory Jumps (`space + 1..5`)**: Fast-path jump to pinned working slots 1 through 5 in constant time (2 keystrokes).
  - **Dynamic Shift-Tracking (`RangeMarker`)**: Pinned slots use IntelliJ `RangeMarker` pointers that automatically adjust their line numbers when text is added or deleted above them.
  - **Interactive Harpoon Card Popup (`space + H H` / `:harpoon`)**: Floating card UI displaying all 5 quick slots with keycap badges (`[1]`–`[5]`), filename, relative path, line number, and code snippet preview. Supports direct jumping (`1`–`5`), pinning current file (`a`), unpinning (`d`/`x`), and live slot swapping / reordering (`J`/`K` with Shift).
  - **Harpoon Submenu (`space + H`)**: Dedicated Which-Key submenu with live preview of pinned files (`space + H 1..5`), auto-pin (`space + H a`), explicit slot assignment (`space + H s 1..5`), and slot deletion (`space + H d 1..5`).
  - **Clean Space Which-Key Hierarchy**: Root `<Space>` menu avoids numeric clutter by presenting a single compact `1-5: Quick slots 1–5` summary row and `H: Harpoon / Quick slots`.
  - **Command Palette (`:commands`) & Native Bookmarks Bridge**: Added `:harpoon` / `:pins`, `:pin [1-5]`, `:unpin [1-5]`, `:bookmarks` (`ShowBookmarks`), `:bookmark-toggle` (`ToggleBookmark`), and `:bookmark-mnemonic` (`ToggleBookmarkWithMnemonic`).
- **Live Preview for Registers (`"`) and Insert Registers (`Ctrl-r`) in Which-Key**:
  - Pressing `"` in Normal or Select mode (or `Ctrl-r` in Insert mode) now displays a live preview overlay populated with actual text snippets from active registers instead of generic category rows.
  - Formats snippets with single-line whitespace normalization (`\n` &rarr; `⏎`), multi-line truncation (`…` at 36 chars), linewise tags (`[L]`), and multi-caret piece-wise selection counts (`[N sel]`).
  - Dynamically discovers and renders active delete history registers (`1`–`9`) and populated named registers (`a`–`z`), omitting empty letter slots to keep Which-Key compact and noise-free.
  - Monospace snippet styling matching the `:reg` palette, with custom tooltips previewing full register contents.
- **Helix Option Value Cycling (`:toggle-option` & `:toggle`)**:
  - Enhanced `:toggle-option <key> [val1 val2 ...]` (alias `:toggle`) to support cycling through multiple space-separated option values (e.g. `:toggle line-number relative absolute` or `:toggle whitespace.render all none`).
  - When no target values are provided, it performs canonical boolean or default state toggling.
- **Pure Relative Gutter Line Numbers (`pure-relative`)**:
  - Added an explicit `pure-relative` option (with aliases `relative-zero` and `zero-relative`) for users who specifically prefer IntelliJ's 0-based distance display on the cursor line instead of the hybrid Helix default.

### Changed

- **Helix-Aligned Relative Line Numbers (`line-number = "relative"`)**:
  - Aligned `line-number = "relative"` and `HelixLineNumberMode.SMART` (Normal and Select modes) with stock Helix by default, displaying the actual line number on the active cursor line and relative distances on all other lines (using IntelliJ's native `LineNumerationType.HYBRID`).

### Fixed

- **Next & Previous Buffer Navigation (`g n` / `g p` / `] b` / `[ b` / `:bn` / `:bp`)**:
  - Fixed an issue where `g n` (next buffer/tab) and `g p` (previous buffer/tab) were silently dropped because the Which-Key popup briefly captured focus, causing IntelliJ's native `NextTabAction` / `PreviousTabAction` focus check (`ToolWindowManager.isEditorComponentActive()`) to evaluate to `false` and reject execution.
  - Implemented direct buffer navigation via `HelixFileNavigation.gotoNextBuffer` and `gotoPreviousBuffer` that inspects the current editor split's buffer list (falling back to open project files when tabs are hidden or single-tab splits are active) and activates the target buffer directly with circular modulo wrapping.
  - Supports numeric count prefixes (e.g. `2 g n` to jump forward 2 buffers).

## [0.8.0] - 2026-11-21

### Added

- **Tool Windows Navigation (Experimental)**:
  - Added basic Helix-style keyboard navigation (`j`/`k`, `h`/`l`, `gg`/`ge`, `Ctrl-d`/`Ctrl-u`, `Ctrl-f`/`Ctrl-b`, `o`/`Enter`, count prefixes) to tool window trees, lists, and tables.
  - Supported targets (configurable in **Settings → Tools → Helix Keymap → General & Scope**): Project / file explorer, Find / Usages, Version Control (Log, Changes, Commit), Problems View, Structure, Services/Run, Debugger (opt-in), and third-party tool windows.
  - Disabled by default and marked as **Experimental** to protect user muscle memory for IntelliJ's traditional type-to-search.
  - Native Speed Search integration: press `/` to trigger the tool window's native speed search; remembers the last search query per tool window; `n` and `N` jump to next/previous matches.

### Fixed

- **Which-Key and Search Popup Escape Key Dismissal & Focus Restoration**:
  - Fixed an issue where pressing `Escape` to dismiss Which-Key, search, or regex selection popups could leave pending sequence state in `HelixEditorState`, requiring an extra `Escape` press before returning to Normal mode or collapsing selections.
  - Enabled native `.setCancelKeyEnabled(true)` and pre-dispatch event routing (`Escape`, `Ctrl-c`, `Ctrl-[`) on `HelixWhichKeyPopup` to ensure popups dismiss reliably even if child focus shifts.
  - Introduced `isHandlingSelection` execution scope in `HelixWhichKeyPopup` to prevent popup teardown callbacks from prematurely wiping the active pending sequence (`" "`, `"g"`, `"m"`, etc.) when executing chords (e.g. `Space e` for file explorer).

## [0.7.3] - 2026-11-17

### Fixed

- **Enter Key & EDT Exception in Injected Code Fragments**:
  - Fixed an `Access is allowed from Event Dispatch Thread (EDT) only` exception triggered during background action updates.
  - Fixed an issue where pressing `Enter` inside injected language fragments (e.g. strings, SQL, regex) failed to insert a newline in Insert mode.
  - Injected editors now properly inherit the host editor's active modal state and safely dispatch cursor UI updates to the EDT.

## [0.7.2] - 2026-11-16

### Added

- **CLI `config.toml` Fallback Policy**:
  - Added `cliConfigFallbackMode` option (**Settings | Tools | Helix Keymap | Configuration (TOML)**) supporting a 3-way fallback policy when `intellij.toml` is not present:
    - **Keymaps Only (Default)**: Automatically reads keybindings from `~/.config/helix/config.toml` without applying `[editor]` options, preventing CLI dotfiles from hijacking IntelliJ editor appearance or locking Settings UI controls.
    - **Full**: Reads keybindings and applies `[editor]` options from `config.toml`.
    - **Disabled**: Never auto-loads `config.toml`, restricting configuration discovery strictly to `intellij.toml` or custom paths.
  - Updated `HelixConfigLoader` and `HelixConfigWatcher` to honor the fallback policy during auto-discovery and live reload.

### Changed

- **Cursor Shape Settings Layout Alignment**:
  - Restructured the Cursor Shape (`cursor-shape`) selectors in **Settings | Tools | Helix Keymap | Appearance** from a wide horizontal row into a clean, vertically stacked 2-column form layout.
  - Aligned mode labels (`Normal (NOR):`, `Insert (INS):`, `Select (SEL):`) and dropdowns flush to the left, preventing horizontal overflow and awkward wrapping on narrow displays.
  - Positioned TOML configuration override notices directly beneath their respective mode dropdowns for contextual provenance visibility.

### Fixed

- **Live Settings UI Synchronization on TOML Reload & Fallback Mode Changes**:
  - Added change listeners to `HelixEffectiveSettings` so that mutating TOML overrides (via VFS file watcher reload, "Reload" button, or changing CLI fallback policy in Configuration settings) immediately synchronizes the open **Appearance** and **Behavior** settings panels without requiring the Settings dialog to be closed and reopened.

## [0.7.1] - 2026-11-15

### Added

- **AI Prompt Box Toggle & EditorTextField Modal Editing Scope**:
  - Added `activateInAiPrompt` setting in **Settings | Editor | Helix | Activation** (disabled by default) allowing users to opt into Helix modal editing for AI assistant and LLM prompt inputs (e.g. JetBrains Air / `ml-llm`).
  - Automatically excluded `HelixLineNumberManager` from applying gutter line numbers to AI prompt boxes and embedded `EditorTextField` components even when modal editing is active.
  - Automatically bypassed `EditorTextField`-managed editors from modal editing unless explicitly enabled via `activateInOtherEditors`.

### Fixed

- **Cursor Shapes Rendering as Full Block**:
  - Corrected thickness factor in `HelixStateManager` for `BAR` (`0.25f`) and `UNDERLINE` (`0.20f`) cursor shapes; previously, a hardcoded `1.0f` thickness caused IntelliJ's `EditorCaretPainter` to render bars and underscores across 100% of character cell width and line height, making them visually indistinguishable from solid block cursors.
  - Added a `CaretListener` in `HelixStateManager` to ensure newly spawned carets (e.g., multi-cursor selections) immediately inherit the active mode's visual attributes.
- **UnsupportedOperationException in Swing Text Components (`TextComponentEditor`)**:
  - Excluded lightweight Swing text component editors (`TextComponentEditorImpl`) from `HelixEditorEligibility` so dialog inputs and plain text areas (e.g. run configurations, commit templates, tool window fields) bypass modal editing and route directly to standard IDE action handlers.
  - Added defensive exception handling in `HelixStateManager.updateCursorColor` when retrieving `editor.colorsScheme` to prevent unhandled `UnsupportedOperationException` on the AWT Event Dispatch Thread when interacting with non-standard or stub editor implementations.
- **EDT Freeze During Config Reload on File Save**:
  - Decoupled `HelixConfigWatcher` automatic reload from the VFS `BulkFileListener.after` write-lock callback using `Application.invokeLater`, preventing synchronous EDT lockups when saving `config.toml` or `intellij.toml`.
  - Mutated `editor.settings` directly when applying `soft-wrap` and `whitespace.render` in `HelixEffectiveSettings` and `HelixEditorOptions`, eliminating nested `ActionManager.tryToExecute` calls that trigger coroutine-based action expand deadlocks on the Event Dispatch Thread.
- **Unwanted Line Numbers in VCS Commit Message Box**:
  - Excluded VCS commit message editors from `HelixLineNumberManager.applyLineNumbers` to prevent relative/smart line numbers from unexpectedly appearing in the commit tool window during modal editing.


### Changed

- **UI Color Modernization & IntelliJ DevKit Compliance**:
  - Replaced direct `java.awt.Color` instantiations across the codebase with `com.intellij.ui.JBColor` to comply with IntelliJ platform DevKit inspections (`'java.awt.Color' used instead of 'JBColor'`) and ensure robust theme adaptation across Light, Dark, and High Contrast IDE themes.
  - Centralized transparent background colors to `HelixTheme.TRANSPARENT` across modal popups (`HelixDirectoryFilePickerPopup`, `HelixJumplistPopup`, `HelixMacrosPopup`, `HelixRegistersPopup`), `HelixListUI`, and `HelixScrollBarUI`.
  - Added integer RGB color helpers to `HelixTheme` (`color(lightRgb, darkRgb)`), streamlining palette definitions into clean hex literals and eliminating raw `Color(...)` calls.
  - Promoted jump target badge colors in `HelixJumpToWord` to static `JBColor` constants, eliminating per-paint heap allocations on every keystroke during jump navigation.
  - Precomputed scrollbar thumb colors (`SCROLLBAR_THUMB_DEFAULT`, `SCROLLBAR_THUMB_ROLLOVER`, `SCROLLBAR_THUMB_DRAGGING`) in `HelixTheme`, eliminating dynamic per-frame color allocations during scrolling and hover states in `HelixScrollBarUI`.

## [0.7.0] - 2026-11-15

### Added

- **`[editor]` Configuration Section Support**:
  - Helix configuration files (`intellij.toml` and fallback `config.toml`) now configure editor behavior and appearance in addition to keymaps.
  - Supported options: `line-number`, `color-modes`, `cursor-tint`, `scrolloff`, `cursorline`, `rulers`, `soft-wrap.enable`, `whitespace.render`, `indent-guides.render`, `cursor-shape.{normal,insert,select}`, `search.smart-case`, `search.wrap-around`, `jump-label-alphabet`, and `bufferline`.
  - Known CLI/LSP options (e.g. `true-color`, `lsp.*`) are cleanly ignored without errors, while unrecognized keys produce non-fatal diagnostic warnings.
  - Layered, non-destructive precedence: `:set-option (session only) › TOML [editor] › Settings UI (persisted) › built-in default`. TOML overrides sit on top of saved settings without altering persisted XML configuration.
  - Overridden controls in Appearance and Behavior panels are automatically locked with provenance notice badges linking directly to the line in the TOML file.
  - Added an **Active editor overrides** table in the Configuration (TOML) settings page with live status and double-click navigation.
  - Configurable per-mode caret shapes (`block`, `bar`, `underline`, `hidden`) dynamically updating `CaretVisualAttributes` across single and multi-carets.
- **Code Generation & Refactoring Expansion (`space + m`)**:
  - Expanded the Refactor submenu (`space + m`) and Which-Key interactive chord menu with essential code generation, safe deletion, and OOP refactorings:
    - `space + m g`: **Generate Code** (`Generate`) for generating constructors, getters/setters, equals/hashCode, toString, and test methods.
    - `space + m d`: **Safe Delete** (`SafeDelete`) to safely remove unused classes, methods, and fields with IDE usage checking.
    - `space + m u`: **Pull Members Up** (`MembersPullUp`) to move members up to superclasses or interfaces.
    - `space + m D`: **Push Members Down** (`PushMembersDown`) to move members down to subclasses.
  - Added new built-in command palette entries and aliases:
    - `:generate` / `:gen` / `:generate-code`: Invoke IntelliJ's code generation popup (`Generate`).
    - `:safe-delete` / `:safe_delete` / `:safedelete`: Invoke Safe Delete dialog with usage validation (`SafeDelete`).
    - `:pull-up` / `:pull_up` / `:members-pull-up`: Invoke Members Pull Up refactoring (`MembersPullUp`).
    - `:push-down` / `:push_down` / `:push-members-down`: Invoke Push Members Down refactoring (`PushMembersDown`).
    - `:cleanup` / `:clean` / `:code-cleanup` / `:reformat-cleanup`: One-step format and optimize imports executing `ReformatWithCodeCleanup` (with automatic fallback to `ReformatCode` + `OptimizeImports`).
- **Git & VCS Workflow Enhancements (`space + v`)**:
  - Implemented a dedicated Version Control leader submenu in `HelixVcsKeymap` providing ergonomic access to daily VCS micro-actions:
    - `space + v r`: **Rollback / Revert Hunk at Caret** (`Vcs.RollbackChangedLines`).
    - `space + v d`: **Show Diff for Current File / Selection** (`Compare.SameVersion` &rarr; `Diff.ShowDiff`).
    - `space + v h`: **File History** (`Vcs.ShowTabbedFileHistory` &rarr; `Vcs.ShowHistory`).
    - `space + v s`: **Git Stash** (`Git.Stash`).
    - `space + v u`: **Git Unstash** (`Git.Unstash`).
    - `space + v b`: **Toggle Git Blame / Annotate** (`Annotate`).
    - `space + v c`: **Commit Changes** (`CheckinProject`).
    - `space + v p`: **Push Commits** (`Vcs.Push`).
    - `space + v P`: **Pull / Update Project** (`Vcs.UpdateProject`).
    - `space + v l`: **Show VCS / Git Log** (`Vcs.Show.Log`).
    - `space + v B`: **Git Branches Popup** (`Git.Branches` &rarr; `Vcs.Branches`).
    - `space + v ?`: **VCS Operations Quick Popup** (`Vcs.QuickListPopupAction`).
  - Integrated `VCS / GIT MENU` into interactive Which-Key popup on chord `space + v` and added `v` (`vcs_menu`) to `spaceItems`.
  - Added new built-in command palette entries and aliases: `:rollback-hunk`, `:revert-hunk`, `:file-history` (`:history`, `:git-history`), `:stash` (`:git-stash`), and `:unstash` (`:git-unstash`).
- **Merge Conflict Navigation (`]C` / `[C`)**:
  - Added unimpaired bracket navigation for merge conflicts: `]C` (next conflict) and `[C` (previous conflict).
  - Implemented smart hybrid resolution: delegates to native IDE `NextConflict` / `PreviousConflict` actions with automatic fallback to document conflict marker scanning (`<<<<<<<`, `=======`, `>>>>>>>`) for raw buffer conflicts.
  - Integrated with `HelixMotionHistory` for repetition via `Alt+.` and count prefixes (e.g., `2]C`).
  - Added `C` entries to Which-Key `[` and `]` bracket navigation menus.
- **Dedicated Test Leader (`space + t`) & TDD Workflow Support**:
  - Implemented a dedicated test leader submenu in `HelixTestKeymap` providing context-to-global fallback actions:
    - `space + t t` / `space + t r`: **Run Test at Caret** (`RunClass` &rarr; `Run`).
    - `space + t d`: **Debug Test at Caret** (`DebugClass` &rarr; `Debug`).
    - `space + t l`: **Rerun Last Execution** (`Rerun`).
    - `space + t f`: **Rerun Failed Tests** (`RerunFailedTests`).
    - `space + t c`: **Run with Coverage** (`RunCoverage` &rarr; `Coverage`).
    - `space + t a`: **Toggle Test and Subject** (`GotoTest`, records jump to jumplist).
  - Integrated `TEST MENU` into interactive Which-Key popup on chord `space + t` with Tab action ID toggling support.
  - Added fast test toggle motion `g T` in `HelixGotoKeymap` complementing unimpaired test jumps (`]T` / `[T`).
  - Registered built-in commands `:test-run`, `:test-debug`, `:test-rerun-failed`, and `:test-coverage` in `HelixBuiltinCommands`.
- **Responsive 1–4 Column Layout & Configurable Max Rows for Which-Key Popups**:
  - Expanded `WhichKeyColumnLayout` with `AUTO` (responsive based on editor width up to 4 columns), `FOUR_COLUMNS` (ultra-compact, packing the 30-item `SPACE` menu into just 8 rows on widescreen displays), `THREE_COLUMNS` (standard), `TWO_COLUMNS` (narrow), and `SINGLE_COLUMN` (vertical list).
  - Added configurable **Preferred Max Rows** (`whichKeyMaxRows`) in **Preferences &rarr; Tools &rarr; Helix Keymap &rarr; Appearance**, dynamically allocating columns to keep row counts within user-targeted limits (e.g. 8, 10, 12, 15, or dynamic).
  - Added responsive viewport width clamping in `HelixWhichKeyPopup` so wide multi-column layouts gracefully scale down to fit narrow editor split panes without horizontal overflow.
- **Custom Transparent Scrollbar UI (`HelixScrollBarUI`)**:
  - Implemented `HelixScrollBarUI` featuring a 100% transparent track, stealth zero-size arrow steppers, and a sleek, semi-transparent rounded pill thumb matching `HelixTheme.CARD_BG`.
  - Applied across all floating card popups (`HelixWhichKeyPopup`, `HelixCommandPopup`, `HelixJumplistPopup`, `HelixRegistersPopup`, `HelixMacrosPopup`, and `HelixDirectoryFilePickerPopup`), eliminating stark white scrollbar tracks and corner clipping on macOS and Light IDE themes.

### Fixed

- **AST Motion Keybindings Parity**:
  - Bound `Alt+b` (`move_parent_node_start`), `Alt+e` (`move_parent_node_end`), `Alt+a` (`select_all_siblings`), and `Alt+I` (`select_all_children`) in `HelixDefaultKeymap` and registered them in `HelixCommandRegistry` to restore full parity with documentation and Keymap Browser inspection.
- **Command Palette & Modal Picker Selection Corner Artifacts**:
  - Eliminated four white/light corner dots visible around selected rows in `:` Command Palette, File Picker, Jumplist, Registers, and Macros popups.

## [0.6.1] - 2026-11-11

### Changed

- **Settings Structure & Tree Hierarchy**:
  - Reorganized the crowded General Settings panel into a clean, hierarchical layout following IntelliJ UX conventions (similar to `Diff & Merge`):
    - **`Helix Keymap` (Parent)**: Houses core user preferences partitioned into 3 focused tabs:
      - **`General`**: Master enable, editor scopes (Diff, Console, Commit message, Other editors), tab switch mode reset, and clipboard synchronization.
      - **`Appearance`**: Theme (Sync/Dark/Light), status bar mode pills, dynamic cursor tinting, gutter line numbers (Relative/Hybrid/Smart/Off), and Which-Key popups/hint mode/columns.
      - **`Behavior`**: Line navigation (Stock Helix visual vs Vim physical), search prompt UI & history capacity, scratch window splits, and jump list capacity.
    - **`Keybindings` (Child Tree Leaf)**: Dedicated full-screen sub-page in the Settings tree hosting the Visual Keymap Browser and OS-level Ctrl Key routing tabs.
    - **`Configuration (TOML)` (Child Tree Leaf)**: Dedicated sub-page in the Settings tree for `intellij.toml` path management, live reload diagnostics, and CLI compatibility checks.
  - Decomposed the monolithic 610-line `HelixGeneralSettingsPanel` into modular, single-responsibility panels (`HelixAppearancePanel`, `HelixBehaviorPanel`, `HelixGeneralSettingsPanel`).
- **UI Popup Performance & List Rendering Overhaul**:
  - Eliminated typing stutter, EDT allocations, and list churn across all high-volume popups (`HelixDirectoryFilePickerPopup`, `HelixMacrosPopup`, `HelixRegistersPopup`, and `HelixJumplistPopup`).
  - Switched list backings from `DefaultListModel` with sequential clear/add cycles to `CollectionListModel` with atomic `replaceAll(...)` batch events.
  - Configured `prototypeCellValue` on list components to bypass expensive multi-hundred element cell size calculations during layout and search.
  - Made `KeycapBadge` renderer components reusable in place with dynamic text and state updating (`setText(...)` / `update(...)`), eliminating panel and label re-allocations on every scroll and repaint.
  - Precomputed normalized lowercase fields for data items (`DirectoryFileItem`, `HelixMacroItem`, `HelixRegisterItem`, `JumpListItem`) and single-pass search query normalization.
  - Bounded register search buffer matching to prevent UI freezes when large multi-megabyte clipboard contents are stored.
- **Action Picker & Keymap Browser Search Optimization**:
  - Prevented 10,000–30,000 temporary string allocations on the Swing EDT per keystroke in `HelixActionPickerDialog` by precomputing lowercase fields (`actionIdLower`, `displayNameLower`, `descriptionLower`) on `HelixIntelliJAction`.
  - Optimized sorting in `HelixActionExtractor.extractActions()` using precomputed lowercase fields, avoiding ~130,000 lowercase allocations during action catalog initialization.
  - Precomputed normalized lowercase lookup strings (`displayShortcutLower`, `rawSequenceLower`, `commandLower`, `descriptionLower`) on `HelixKeybindingRow` to make `HelixKeymapBrowserPanel` search filtering instantaneous without per-row string transformations.
  - Added duplicate query and prefix suppression in `HelixActionPickerDialog` and `HelixKeymapBrowserPanel` to skip redundant filter passes on non-textual input events.
- **Large-Document Regex Match Bounding & Live Search Optimization**:
  - Capped live document and selection regex match counting at `MAX_MATCH_COUNT = 1000` (`countDocumentRegexMatches`, `countRegexMatchesInSnapshot`, and `countFilterSelectionsMatches`), terminating search loops early to prevent EDT lockups when typing patterns in large buffers.
  - Added duplicate query deduplication (`lastQuery == query`) across `HelixPromptBar`, `HelixSearchPopup`, and `HelixSelectRegexPopup` to eliminate redundant regex evaluation passes on no-op events.
  - Formatted status indicators to gracefully display `"1000+ matches"` / `"1000+ matches found"` / `"1000+ in selection"` using existing bundle format templates when matches reach the threshold.
- **Status Bar Widget Hot-Path Optimization**:
  - Eliminated redundant `Class.forName(...)` and `getMethod(...)` reflection calls on the caret and state update hot paths by caching `ActionMacroManager` reflection lookups statically.
  - Pre-allocated immutable `ModePillIcon` instances for each mode (`NORMAL`, `INSERT`, `SELECT`), eliminating icon object re-instantiations on editor focus and caret events.
  - Added dirty-checking for icon, text, and tooltip changes in `HelixStatusBarWidget.updateComponent()` to skip unnecessary status bar repaints when widget state remains unchanged.
- **Kotlin Language Version**:
  - Updated Kotlin compiler `languageVersion` to `2.2` in `build.gradle.kts` while maintaining `apiVersion` at `2.1` for IntelliJ platform compatibility.

### Fixed

- **IntelliJ Platform API Compatibility**:
  - Replaced scheduled-for-removal `SimpleListCellRenderer.create(...)` in the Appearance settings panel with a direct `SimpleListCellRenderer` subclass implementation to ensure clean compatibility with IntelliJ 2026.x and beyond.
  - Replaced standard Swing `JScrollPane` usages in `HelixActionPickerDialog` and `HelixKeymapBrowserPanel` with `com.intellij.ui.components.JBScrollPane` to follow IntelliJ UI conventions and eliminate IDE warnings.

## [0.6.0] - 2026-11-10

### Added

- **Relative Line Numbers & Smart Insert Toggle (`line-number`)**:
  - Added full Helix-style line number modes: `IDE_DEFAULT`, `ABSOLUTE`, `RELATIVE`, `HYBRID`, and `SMART` (relative line numbers in Normal/Select mode with automatic absolute fallback in Insert mode).
  - Added runtime commands `:set-option line-number relative|smart|hybrid|absolute|off` and `:toggle-relative-line-numbers` (`:relative-numbers`, `:rnu`).
  - Added Gutter Line Numbers dropdown configuration in **Preferences &rarr; Tools &rarr; Helix Keymap &rarr; General**.
  - Registered `toggle_relative_line_numbers` in `HelixCommandRegistry` for user keybinding configurations.
- **Mode-Colored Status Bar Pill (`color-modes = true`)**:
  - Added Helix-style color-coded status bar pills with custom background and foreground colors per mode (`NOR` in Indigo/Cyan, `INS` in Emerald Green, `SEL` in Purple/Magenta).
  - Added runtime commands `:set-option color-modes true|false` (`:set color-modes true|false`) and `:toggle-color-modes` (`:color-modes`).
  - Added "Enable mode-colored status bar pill" configuration setting in **Preferences &rarr; Tools &rarr; Helix Keymap &rarr; General**.
  - Added `HelixStatusBarWidget.updateAllWidgets()` to broadcast status bar widget invalidation across all open projects immediately on settings changes.
- **Independent Editor Cursor Tinting (`cursor-tint = true`)**:
  - Decoupled cursor tinting into an independent setting, allowing users to keep mode-colored status pills without overriding editor caret color schemes.
  - Added dynamic cursor tint matching the active mode in the editor when `cursor-tint = true`.
  - Added runtime commands `:set-option cursor-tint true|false` (`:set cursor-tint true|false`) and `:toggle-cursor-tint` (`:cursor-tint`).
  - Added "Tint editor cursor to match mode color" configuration setting in **Preferences &rarr; Tools &rarr; Helix Keymap &rarr; General**.
  - Registered `toggle_color_modes` and `toggle_cursor_tint` in `HelixCommandRegistry` for user keybinding configurations.
- **Deep IDE Intelligence & View Management Commands**:
  - Added `:recent-locations` (`:locations`, `:recents`) and bound `space + B` to open IntelliJ's `RecentLocations` popup for visual code snippet inspection of recently visited and edited code locations.
  - Added `:show-usages` (`:usages-popup`) to trigger `ShowUsages` for previewing symbol call sites in an in-place floating popup without opening the tool window.
  - Added `:maximize-view` (`:maximize-editor`, `:maximize`) and bound `Ctrl+w m` / `space w m` (`wmaximize` / `maximize_view`) to maximize the active editor split or toggle distraction-free window mode (`MaximizeEditorInSplit` / `HideAllWindows`).
  - Added `:close-to-right` (`:buffer-close-right`, `:bcr`, `CloseAllToTheRight`) to close all editor tabs located to the right of the active tab.
  - Added `:close-unmodified` (`:buffer-close-unmodified`, `:bcu`, `CloseAllUnmodifiedEditors`) to close all clean, unmodified editor tabs.
  - Registered `recent_locations`, `show_usages`, `maximize_view`, `wmaximize`, `close_to_right`, and `close_unmodified` in `HelixCommandRegistry`.
- **Numeric Sorting**:
  - Added numeric line sorting (`:sort -n`, `:sort -rn`, `:sort-numeric`, `:sort-numeric-reverse`) that compares mathematical values (handling decimals and negative numbers) rather than raw ASCII/lexicographic order.
  - Registered `sort_numeric` and `sort_numeric_reverse` in `HelixCommandRegistry` for user keybinding configurations.
- **Multi-Caret Commands (`add_selection_next` / `add_selection_prev` / `select_all_occurrences`)**:
  - Added `add_selection_next` (`:select-next-occurrence`, `:select-next`) to select the word under cursor or append the next occurrence of the selection as a new caret (the classic `Ctrl-D` / `SelectNextOccurrence` multi-cursor workflow).
  - Added `add_selection_prev` (`:select-prev-occurrence`, `:select-prev`) to append the previous occurrence of the selection as a new caret.
  - Added `select_all_occurrences` (`:select-all-occurrences`, `:select-all-matches`) to select all occurrences of the word/selection across the entire buffer simultaneously.
  - Registered commands in `HelixCommandRegistry` so users can bind them to arbitrary shortcuts (e.g. `"C-d" = "add_selection_next"`) in `intellij.toml` or `config.toml`.
- **Essential Developer Workflow & Git Commands**:
  - Added `:reopen-closed-tab` (`:reopen`, `ReopenClosedTab`) to reopen the most recently closed editor tab.
  - Added `:copy-path` (`CopyAbsolutePath`), `:copy-relative-path` (`CopyContentRootPath`), and `:copy-reference` (`CopyReferencePopup`) to quickly copy file paths and qualified symbol references.
  - Added `:branches` (`:git-branches`, `Git.Branches`) to open IntelliJ's Git branches switcher.
  - Added `:log-open` (`:show-log`, `ShowLog`) to open the IDE log file in the editor (matching Helix's `:log-open`).
  - Registered all new actions (`reopen_closed_tab`, `copy_path`, `copy_relative_path`, `copy_reference`, `branches`, `log_open`) in `HelixCommandRegistry`.

### Changed

- **Command Palette Search & UI Performance**:
  - Significantly reduced search latency and eliminated typing stutter in the `:` command palette.
  - Optimized command matching, scoring, and Swing list rendering to provide instant, fluid filtering across all commands.

### Fixed

- **Sticky Horizontal Position on Vertical Line Navigation (`j`/`k`, arrows, `gj`/`gk`)**:
  - Fixed loss of starting horizontal column when navigating up and down through lines shorter than the cursor position.
  - Delegated visual line navigation to IntelliJ's `Caret.moveCaretRelatively`, to maintain position across lines of uneven lengths, soft wraps, tabs, and fonts.
  - Added per-caret sticky column tracking for physical line navigation (`HelixMotions.moveDown` / `moveUp`), ensuring target columns are restored when passing across shorter physical lines and properly invalidated upon horizontal movements.
- **Configuration Documentation Alignment**:
  - Updated example TOML keymap snippet in `README.md` and `HelixKeymapTest` from `"X" = "extend_line_up"` to `"X" = "extend_line"` to align with registered commands.

## [0.5.0] - 2026-11-07

### Added

- **Helix Inline Keyboard Macro Playback (`@` syntax) & Deep Sub-Keymaps**:
  - **Inline Macro Evaluator (`HelixMacroExecutor`)**: Full support for Helix's canonical `@...` macro playback syntax in keymaps (e.g. `"\"" = "@f\"lmi\""` or `C-x = "@miw<A-o>"`). Replays keystroke sequences directly through the editor pipeline.
  - **Keystroke Tokenization & Special Keys**: Supports chained normal-mode characters, text objects, and angle-bracket special keys (`<ret>`, `<esc>`, `<space>`, `<tab>`, `<backspace>`, `<lt>`, `<gt>`, `<left>`, `<right>`, `<up>`, `<down>`, and modifier chords like `<C-s>`, `<A-o>`).
  - **Command Registry & Validation Integration**: Direct delegation in `HelixCommandRegistry.execute` for `@` prefixes, and updated `HelixConfigLoader.isValidActionName` so macro bindings pass config validation without errors.
  - **Which-Key Suppression & Recursion Guards**: Suppresses visual Which-Key popups during macro replay to avoid UI flicker, with built-in recursion depth limits.
  - **Arbitrary-Depth Sub-Keymap Routing**: Refined match submenu prefix matching in `HelixKeyHandler` so that deep custom sub-chords under `m` (e.g. `[keys.normal.m.n]` and `[keys.normal.m.p]` for jumping to and selecting next/previous enclosing delimiters) resolve cleanly through the keymap Trie.
- **Visual & IDE Toggle Commands (`:set-option`, `:toggle-option`, and `:toggle-*`)**:
  - **Canonical Helix Option Commands**: Added `:set-option <key> <val>` (alias `:set`) and `:toggle-option <key>` (alias `:toggle`), matching modern Helix configuration commands. Supports both space-delimited (`:set soft-wrap.enable true`) and key-value formats (`:set soft-wrap=on`).
  - **Supported Option Keys & Enums**: Full support for canonical dotted keys and flat aliases:
    - `soft-wrap.enable` / `soft-wrap` &rarr; Toggle visual soft-wrapping in active editor
    - `inlay-hints.enable` / `inlay-hints` &rarr; Toggle parameter and type hints globally
    - `whitespace.render` / `whitespace` (`all`, `none`, `true`, `false`) &rarr; Toggle whitespace character rendering
    - `line-number` / `line-numbers` (`absolute`, `none`, `true`, `false`) &rarr; Toggle gutter line numbers
    - `sticky-context` / `sticky-lines` &rarr; Toggle sticky context header lines
    - `breadcrumbs.enable` / `breadcrumbs` &rarr; Toggle editor breadcrumbs navigation
  - **First-Class Palette Shorthands**: Added quick toggle commands with fuzzy aliases: `:toggle-soft-wrap` (`:soft-wrap`, `:wrap`), `:toggle-inlay-hints` (`:inlay-hints`, `:hints`), `:toggle-sticky-context` (`:sticky-context`, `:sticky-lines`), `:toggle-line-numbers` (`:line-numbers`, `:numbers`), `:toggle-render-whitespace` (`:whitespace`, `:whitespaces`), and `:toggle-breadcrumbs` (`:breadcrumbs`).
  - **Config Registry Identifiers**: Registered canonical identifiers (`toggle_soft_wrap`, `toggle_inlay_hints`, `toggle_sticky_context`, `toggle_line_numbers`, `toggle_whitespace`, `toggle_breadcrumbs`) in `HelixCommandRegistry` so users can bind them to arbitrary keys in `config.toml`.
  - **Modular Architecture Refactoring**: Extracted built-in command palette definitions into `HelixBuiltinCommands.kt` while maintaining `HelixCommands.COMMANDS` facade compatibility and keeping both classes comfortably under Detekt's file complexity limits.
- **First-Class Refactoring Commands & Refactor Submenu (`space + m`)**:
  - **Refactor Submenu (`space + m`)**: Dedicated Which-Key submenu exposing core IntelliJ AST refactorings with single-key chords:
    - `space m v` &rarr; Extract variable (`IntroduceVariable`)
    - `space m m` &rarr; Extract method (`ExtractMethod`)
    - `space m c` &rarr; Extract constant (`IntroduceConstant`)
    - `space m f` &rarr; Extract field (`IntroduceField`)
    - `space m p` &rarr; Extract parameter (`IntroduceParameter`)
    - `space m i` &rarr; Inline symbol (`Inline`)
    - `space m s` &rarr; Change signature (`ChangeSignature`)
    - `space m r` &rarr; Rename symbol (`RenameElement`)
    - `space m M` &rarr; Move symbol / file (`Move`)
    - `space m w` &rarr; Surround with template (`SurroundWith`)
    - `space m o` &rarr; Override methods (`OverrideMethods`)
    - `space m I` &rarr; Implement methods (`ImplementMethods`)
    - `space m ?` / `space m space` &rarr; Refactor This quick picker (`Refactorings.QuickListPopupAction`)
  - **First-Class Helix Palette Commands (`:`)**: Added canonical commands with fuzzy aliases: `:extract-variable` (`:var`), `:extract-method` (`:method`), `:extract-constant` (`:const`), `:extract-field` (`:field`), `:extract-parameter` (`:param`), `:inline`, `:change-signature` (`:cs`), `:move`, `:override`, `:implement`, `:surround-with` (`:surround`), `:refactor-this` (`:refactor`).
  - **Mode Lifecycle Consistency**: Refactoring commands executed while in `SELECT` mode operate on the active selection and cleanly transition back to `NORMAL` mode.
  - **Trie & Keybindings Browser Parity**: Registered all `space m` sub-chords in `HelixDefaultKeymap` so they are fully discoverable and searchable in the Keybindings Explorer under the `SPACE` category.

### Fixed

- **Symmetric Delimiter Text Object Selection Between Delimiter Pairs (`mi"`, `mi'`, etc.)**:
  - Fixed an issue in `HelixSurroundActions.findSymmetricPairInRange` where cursors positioned between consecutive symmetric delimiter pairs (e.g. in `"aaa" = "bbb"` between the closing quote of `"aaa"` and opening quote of `"bbb"`) failed to recognize the span as an enclosing delimiter range.
  - Added a secondary interval scan matching real Helix behavior, correctly selecting the text between surrounding delimiters (such as ` = ` when jumping forward and selecting via `@f"lmi"`).
- **Option Command Consistency (`:set-option` & `:toggle-option`)**:
  - Unified legacy ad-hoc settings commands (`search-ui`, `line-nav`, `scratch-mode`) into canonical Helix `:set-option <key> <val>` and `:toggle-option <key>` syntax (e.g. `:set-option search-ui popup`, `:toggle-option line-nav`, `:set scratch-mode buffer`) while preserving backwards compatibility for legacy `=` assignments.
- **Which-Key Menu Branch Labeling**:
  - Fixed an issue in `HelixWhichKeyMenus.mergeMenuItems` where non-leaf branch nodes with null actions could overwrite existing visual Which-Key labels with empty strings (resolving the empty `m` badge in the `SPACE MENU`).

## [0.4.0] - 2026-11-02

### Added

- **Interactive Visual Keymap Customizer (Add / Edit / Reset / Disable)**:
  - **Add Keybinding (`Add Binding...`)**: Modal dialog (`HelixKeybindingEditDialog`) allowing users to define new bindings by mode (`Normal`, `Select`, `Insert`), shortcut keystroke or chord, and target action.
  - **Edit Keybinding (`Edit...`)**: Edit existing shortcuts via action button, table double-click, or right-click context menu.
  - **Reset to Default (`Reset to Default`)**: Reverts custom key overrides back to default bindings by cleanly removing them from `intellij.toml` (or custom config path).
  - **Disable Shortcut (`Disable (no_op)`)**: Quick action to disable a shortcut by mapping it to `"no_op"`.
  - **Integrated Shortcut Recording**: Direct `Record...` button inside the edit dialog to capture single keys and chords on the fly.
  - **IntelliJ Action Discovery**: Integrated `Browse...` button in the edit dialog to discover and bind any IntelliJ action (`:action <ActionId>`).
  - **Table Context Menu**: Right-click menu providing quick actions: Add Binding..., Edit..., Reset to Default, Disable (no_op), Copy Helix Command, and Copy Shortcut.
  - **Safe TOML Engine (`HelixConfigModifier`)**: Non-destructive TOML writer that preserves user comments, blank lines, and unrelated sections while persisting key changes and triggering live runtime reloads.
- **Interactive Macro Picker Popup (`:macros` / `:macro`)**: Added an interactive search and playback picker for recorded macros with action counts, preview snippets, fuzzy filtering, `Enter` to play, and `Alt+E` to edit.

### Fixed

- **IntelliJ Platform Modernization & Deprecation Fixes (2025.1 / 2025.2 / 2026.1+ Compatibility)**:
  - **Read Actions**: Replaced deprecated `ReadAction.compute` and `ReadAction.run` with cross-version `HelixReadAction.compute` and `run` delegating to `ApplicationManager.getApplication().runReadAction`, eliminating 12 deprecation warnings on IntelliJ 2026+.
  - **Editor & File Navigation**: Migrated `FileEditorManager.openFile` and `openTextEditor` usages to modern descriptor navigation via `OpenFileDescriptor(project, file).navigate(true)`.
  - **Event Queue Preprocessing**: Replaced deprecated `IdeEventQueue.addDispatcher` / `removeDispatcher` with `IdeEventQueue.addPreprocessor` with lifecycle managed via `Disposer`.
  - **Dynamic Bundle Classloading**: Updated `HelixBundle` to pass `HelixBundle::class.java` explicitly to `DynamicBundle` for reliable resource bundle lookup under isolated plugin classloaders.
  - **Status Bar Widget Modernization**: Migrated `HelixStatusBarWidget` from deprecated `StatusBarWidget.TextPresentation` to `CustomStatusBarWidget` using `TextPanel.WithIconAndArrows`, configured stable `JvmDefaultMode.NO_COMPATIBILITY`, and eliminated synthetic bytecode bridges.
  - **Macro Subsystem Decoupling**: Decoupled `HelixMacroItem` and `HelixMacroManager` from internal `com.intellij.ide.actionMacro.*` classes via reflection and IntelliJ `ActionManager`, eliminating 33 internal API verification errors.
- **Helix Select / Extend (`SEL`) Mode Lifecycle & Transitions**:
  - **Editing & Modifying Actions Return to Normal Mode**: Applying destructive, modifying, or yanking operations in `SEL` mode (`d`, `Alt-d`, `y`, `Alt-y`, clipboard yanks, `r`, `R`, `p`, `P`, `~`, `` ` ``, `Alt+` ``, `J`, `Alt-J`, `Ctrl-a`, `Ctrl-x`, and surround actions) executes on the highlighted selection and automatically exits `SEL` mode back to `NORMAL` mode.
  - **Direct Insert Mode Transitions**: Change actions (`c`, `Alt-c`) in `SEL` mode delete the selection and transition directly into `INSERT` mode.
  - **Selection Preservation on Exit (`v` / `Esc`)**: Toggling out of `SEL` mode back to `NORMAL` mode via `v` or exiting via `Esc` preserves the highlighted selection intact without modifying text or clearing the selection.
  - **Insert Mode Caret Placement (`i` / `a`)**: Entering insert mode (`i` / `insert_mode`) with an active selection now explicitly relocates the caret to `selectionStart` before clearing the selection, guaranteeing typing inserts before the selection (matching `a` / `append_mode` inserting after at `selectionEnd`).
  - **Buffer-Mutating & Selection Actions Exit Select Mode**: Aligning selections (`&`), rotating selection contents (`Alt-(`, `Alt-)`, `Alt-:`), sorting lines (`:sort`), repeating last insert (`.`), shell commands/pipes (`|`, `!`), and line/block comment toggling (`space c`, `space C`) automatically transition from `SELECT` mode back to `NORMAL` mode.
  - **Sub-Selection Splitting Transitions to Normal Mode**: Commands that split or filter a selection into multiple sub-selections (`s` regex select, `S` split regex, `Alt-s` split on newlines, `K` filter selections) automatically return to `NORMAL` mode so each sub-caret can be navigated independently.
  - **Extend-Search Preservation in Select Mode**: `HelixSearchPopup` checks for `SELECT` mode on submission, executing extend search navigation (`searchNext`) across carets rather than collapsing carets to head.
  - **Command Registry Modularization**: Refactored command registration in `HelixCommandRegistry` into modular domain groups (`registerEdits`, `registerInsertTransitions`, `registerFormattingAndIndents`) with full Detekt compliance and expanded unit test coverage in `HelixEditingActionsTest`.
- **Macro Recording Trailing `Q` Fix**: Fixed an issue where stopping macro recording with `Q` recorded the trailing `Q` into the macro, causing macro playback (`q`) to unintentionally restart recording.
- **Command Palette Match Relevance Ranking**: Added relevance scoring to the command palette (`:`) so exact command names and aliases (such as `:reg`, `:w`, `:q`) rank first instead of being shadowed by partial substring matches.

## [0.3.2] - 2026-10-24

### Fixed

- **Comprehensive Default Keymap Trie Population & Category Parity**:
  - Populated all **Match (`MATCH`)** chords (`mm`, `ms`, `mr`, `md`, `ma`, `mi`) in the default runtime trie so that match/surround actions and text objects are fully discoverable and searchable in the Keybindings Explorer.
  - Populated all **Bracket / Unimpaired (`BRACKET`)** navigation chords for both `[` (backward) and `]` (forward): paragraph (`p`), comment (`c`), function (`f`), class/type (`t`), diagnostic/error (`d`, `D`), change (`g`, `G`), entry (`e`), spelling error (`s`), test (`T`), parameter (`a`), and blank lines (`space`).
  - Populated missing **Goto (`GOTO`)** chords (`g.`, `g|`, `gw`), **View (`VIEW`)** chords (`zd`, `zu`, `zf`, `zF`), **Window (`WINDOW`)** split and view swap chords (`C-w f`, `C-w F`, `C-w H`, `C-w J`, `C-w K`, `C-w L`), and **Space (`SPACE`)** chords (`space '`, `space h`, `space w`).
  - Populated missing **Standalone Motions & Alt Chords (`STANDALONE`)**: `G`, `q`, `Q`, `A-d`, `A-c`, `A-.`, `A-;`, `A-:`, `A-k`, `A-K`, `A-s`, `A-,`, `A-C`, `A--`, `A-_`, `A-x`, `A-o`, `A-i`, `A-p`, `A-n`.
- **Extended Canonical Commands & Parametric Submode Routing**:
  - Registered unimpaired commands in `HelixExtendedCommands` with repeat bracket motion history tracking (`HelixMotionHistory.recordBracket`).
  - Registered DAP canonical commands (`dap_toggle_breakpoint`, `dap_continue`, `dap_step_in`, `dap_next`, `dap_step_out`, `dap_pause`, `dap_restart`, etc.) and window view swapping commands (`swap_view_left`, `swap_view_down`, etc.).
  - Enhanced `HelixKeyHandler` with parametric submode bypass so that multi-step textobject and surround sequences (`mi`, `ma`, `ms`) delegate directly to `HelixMatchKeymap`.

## [0.3.1] - 2026-10-24

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
    - **Find Shortcut by Keystroke (`Find by Shortcut...`)**: Shortcut recorder dialog capturing keystrokes and chords (e.g. `space f`, `g d`, `C-s`) to instantly locate bindings.
    - Provenance badges identifying binding sources (`[Default]`, `[custom: intellij]`, `[custom: config]`, `[no_op]`).
    - Quick actions to open active TOML configuration file or refresh table data.
    - Visual conflict warnings (`⚠`) highlighting shadowed keybindings where a shortcut cannot fire due to child chords taking precedence.
  - **Keymap Trie Extractor (`HelixKeymapExtractor`)**: Flattens nested prefix tries into tabular rows with human-readable chord formatting and deterministic provenance detection.
  - **Prefix Collision & Trie Shadowing Detector (`HelixKeymapConflictDetector`)**:
    - Automated prefix trie analysis detecting shadowed bindings where a single key has an action assigned but is masked by child chords (e.g. binding `g` or `space` directly when child chords `gd`, `gh`, `space f` exist).
    - Integrated into `HelixConfigLoader.validateConfig()`: TOML files containing shadowed shortcuts are automatically flagged with conflict diagnostics and child chord lists.

## [0.3.0] - 2026-10-21

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

## [0.2.2] - 2026-10-14

### Fixed

- **IDE Restart Requirement**:
  - Explicitly declared `require-restart="true"` in `plugin.xml` so the IntelliJ Plugin Manager prompts the user to restart the IDE upon installation or update, ensuring global typed action handlers, editor listeners, and caret states are reliably initialized across all open editors.
  - Updated installation instructions in `README.md` to instruct restarting the IDE after installation.

## [0.2.1] - 2026-10-13

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

## [0.1.0] - 2026-10-07

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

[Unreleased]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.9.1...HEAD
[0.9.1]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.7.3...v0.8.0
[0.7.3]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.7.2...v0.7.3
[0.7.2]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.6.1...v0.7.0
[0.6.1]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.3.2...v0.4.0
[0.3.2]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.2.2...v0.3.0
[0.2.2]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/Toshi68k/t68k-intellij-helix/compare/v0.1.0...v0.2.1
[0.1.0]: https://github.com/Toshi68k/t68k-intellij-helix/commits/v0.1.0
