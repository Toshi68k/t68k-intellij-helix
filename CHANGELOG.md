# Changelog

All notable changes to **Helix Keymap (T68k)** are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
