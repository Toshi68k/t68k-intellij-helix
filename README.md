# Helix Keymap for IntelliJ IDEA

[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](https://github.com/t68k/t68k-intellij-helix)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2024.2+-000000.svg?logo=intellij-idea&logoColor=white)](https://plugins.jetbrains.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.4-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/Java-17%20%7C%2021+-ED8B00.svg?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Helix](https://img.shields.io/badge/Modal-Helix-03C7D3.svg)](https://helix-editor.com)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A complete modal editing plugin for JetBrains IDEs implementing the [Helix](https://helix-editor.com) editor's **selection-first paradigm**, deeply integrated with IntelliJ's native IDE intelligence, AST capabilities, and refactoring engines.

- **Plugin ID**: `jp.titze.intellij.helix`
- **Vendor**: [Thorsten Titze](https://titze.jp)
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
- **Command Palette (`:`)**: Lightweight command prompt supporting standard Helix buffer commands (`:w`, `:q`, `:wq`, `:wa`, `:qa`, `:vsp`, `:sp`, `:format`).

---

## Helix Keymap Reference

### Motions (Create / Extend Selections)

| Key | Description |
|-----|-------------|
| `f<char>` | Move to next occurrence of `<char>` (inclusive, searches across lines) |
| `t<char>` | Move till next occurrence of `<char>` (exclusive, stops before `<char>`, supports `<Enter>`) |
| `F<char>` | Move to previous occurrence of `<char>` (backward, inclusive) |
| `T<char>` | Move till previous occurrence of `<char>` (backward, exclusive, stops after `<char>`) |
| `w` | Advance to the start of the next word |
| `b` | Move backward to the start of the previous word |
| `e` | Advance to the end of the current/next word |
| `ge` | Move backward to the end of the previous word |
| `x` | Select current line (including newline); pressing `x` again extends to the next line |
| `%` | Select entire buffer |
| `h` / `j` / `k` / `l` | Move left / down / up / right (mode-aware selection update) |
| `gh` | Move to line start (actual first character) |
| `gs` | Move to first non-whitespace character of line |
| `gl` | Move to line end |
| `gg` | Move to the top of the buffer |
| `ge` *(in `g` menu)* | Move to the end of the buffer |
| `Ctrl+f` / `PageDown` | Move page down |
| `Ctrl+b` / `PageUp` | Move page up |
| `Ctrl+d` | Move half page down |
| `Ctrl+u` | Move half page up |

### Selection Manipulation

| Key | Description |
|-----|-------------|
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

### Actions on Selection

| Key | Description |
|-----|-------------|
| `d` | Delete active selection & copy to clipboard |
| `c` | Delete active selection, copy to clipboard, and enter `Insert` mode |
| `y` | Yank (copy) active selection to clipboard |
| `p` | Paste clipboard after selection / caret |
| `P` | Paste clipboard before selection / caret |
| `r<char>` | Replace each selected character (or character under cursor) with `<char>` |
| `R` | Replace selection (or character under cursor) with clipboard / yanked text |
| `J` | Join lines inside selection, or join current line with line below |
| `i` | Enter `Insert` mode at start of selection |
| `a` | Enter `Insert` mode after caret / selection |
| `I` | Enter `Insert` mode at line start (first non-blank) |
| `A` | Enter `Insert` mode at line end |
| `o` | Insert new line below (`EditorStartNewLine`) and enter `Insert` mode |
| `O` | Insert new line above (`EditorStartNewLineBefore`) and enter `Insert` mode |
| `u` | Undo (`$Undo`) |
| `U` | Redo (`$Redo`) |
| `=` | Reformat code (`ReformatCode`) |
| `>` / `<` | Indent / Unindent selection |
| `~` | Toggle case of selection (`ToggleCase`) |
| `Escape` | Return to `Normal` mode / clear pending chords |

### Surround & Match Mode (`m` menu)

Built-in surround and textobject functionality matching [Helix Surround](https://docs.helix-editor.com/surround.html) and [Helix Textobjects](https://docs.helix-editor.com/textobjects.html):

| Key | Action | Description |
|-----|--------|-------------|
| `ms<char>` | `surround_add` | Surround active selection (or single character under cursor) with delimiter `<char>` |
| `mr<from><to>` | `surround_replace` | Replace closest enclosing surround pair `<from>` with `<to>` |
| `md<char>` | `surround_delete` | Delete closest enclosing surround pair `<char>` |
| `mm` | `match_bracket` | Jump to matching bracket (`EditorMatchBracket`) |
| `ma<object>` | `select_textobject_around` | Select **around** the textobject (e.g. `maw`, `maW`, `map`, `ma(`, `mam`, `maa`) |
| `mi<object>` | `select_textobject_inside` | Select **inside** the textobject (e.g. `miw`, `miW`, `mip`, `mi(`, `mim`, `mia`) |

#### Textobjects Supported
- **`w`** &rarr; Word (`miw` selects inner word; `maw` selects word + trailing or leading whitespace)
- **`W`** &rarr; WORD (`miW` selects non-whitespace token; `maW` selects token + whitespace)
- **`p`** &rarr; Paragraph (`mip` selects paragraph lines; `map` includes blank lines)
- **Delimiters**: `(`, `[`, `{`, `<`, `"`, `'`, `` ` `` (e.g. `mi(` selects inside parens; `ma(` includes parens)
- **`m`** &rarr; Closest enclosing pair / quote (`mim` inside closest pair; `mam` around closest pair)
- **`a`** &rarr; Argument / parameter (`mia` inside parameter; `maa` includes delimiter/comma)
- **`f`** &rarr; Function / method (PSI-aware; falls back to enclosing `{...}`)
- **`t`** &rarr; Type / class (PSI-aware; falls back to enclosing `{...}`)
- **`c`** &rarr; Comment (PSI-aware comment node or line comment)

#### Supported Delimiters & Aliases
- **Pairs**: `()` (alias `b` or `p`), `[]` (alias `r`), `{}` (alias `B` or `c`), `<>` (alias `a`)
- **Quotes**: `"` (double quote), `'` (single quote), `` ` `` (backtick), or `q` (any quote)
- **Arbitrary Characters**: Any arbitrary delimiter such as `*` (e.g. `*bold*`), `_`, `~`, `/`, etc.
- **Multi-Caret**: Works seamlessly across all active carets simultaneously.

### Deep IntelliJ IDE Integrations

#### Navigation (`g` menu)
- `gd` &rarr; `GotoDeclaration` / `GotoImplementation`
- `gy` &rarr; `GotoTypeDeclaration`
- `gr` &rarr; `FindUsages`
- `gh` &rarr; Line start (first character)
- `gs` &rarr; First non-whitespace character
- `gl` &rarr; Line end
- `ge` &rarr; Goto end of buffer
- `gg` &rarr; Goto start of buffer

#### Interactive Which-Key Floating Menu
Whenever a chord prefix key (<kbd>Space</kbd>, `g`, `m`, `[`, or `]`) is pressed in Normal mode, an interactive, non-intrusive **Which-Key popup** appears in authentic Helix cyan:
- **Zero latency**: Muscle memory stays instant—typing the follow-up key immediately executes the command without waiting.
- **Visual discoverability**: Pausing on <kbd>Space</kbd> reveals all available pickers and actions.
- **Single-key & click dispatch**: Pressing any highlighted key or clicking any option directly executes the action.
- **Cancelable**: Pressing <kbd>Esc</kbd> or clicking outside dismisses the menu and restores `Normal` mode.

#### Pickers & Space Menu (`space`)
| Key | Action | Description |
|-----|--------|-------------|
| `space + f` | `GotoFile` / `SearchEverywhere` | Dedicated fuzzy file picker |
| `space + b` | `RecentFiles` | Open buffer / tab switcher |
| `space + /` | `FindInPath` | Live project-wide text search (live grep) with preview |
| `space + j` | `RecentLocations` | Visual jumplist picker with recent edits and code diffs |
| `space + s` | `FileStructurePopup` | Document symbols / outline picker |
| `space + S` | `GotoSymbol` | Workspace-wide symbol picker across AST |
| `space + d` | `ShowErrorDescription` | Diagnostic error inspection under caret |
| `space + D` | `ActivateProblemsViewToolWindow` | Workspace diagnostics (IntelliJ Problems panel) |
| `space + a` | `ShowIntentionActions` | Code actions & quick-fixes (Alt+Enter) |
| `space + r` | `RenameElement` | Refactor rename symbol |
| `space + w` | `SaveAll` | Save all modified buffers |
| `space + y` | `yank_main_selection` | Yank active selection to system clipboard |
| `space + p` | `paste_clipboard_after` | Paste system clipboard after cursor / selection |
| `space + P` | `paste_clipboard_before` | Paste system clipboard before cursor / selection |
| `space + R` | `replace_with_clipboard` | Replace current selections with system clipboard |
| `space + k` | `QuickJavaDoc` | Hover documentation popup |
| `space + ?` | `GotoAction` | Action / command palette picker |

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
- `:format` &rarr; Reformat code (`ReformatCode`)
- `:reload` / `:e!` &rarr; Reload file from disk (`SynchronizeCurrentFile`)
- `:open` &rarr; Open fuzzy file picker (`GotoFile`)
- `:buffer` / `:b` &rarr; Open buffer switcher (`RecentFiles`)
- `:find` &rarr; Find in project files (`FindInPath`)
- `:toggle-search-ui` / `:search-ui` &rarr; Toggle between Stock Helix inline bar and Popup dialog
- `:set search-ui=inline` / `:set search-ui=stock` &rarr; Set search UI to Stock Helix inline bar
- `:set search-ui=popup` &rarr; Set search UI to Popup dialog

#### Search & Selection UI Modes
Helix Keymap supports two switchable search and regex prompt styles:
1. **Stock Helix Mode (Default)**: Single-line prompt bar docked at the bottom of the active editor (`search: `, `rsearch: `, `select: `, `split: `). Matches and selections update **live in the editor buffer as you type**. Pressing <kbd>Enter</kbd> confirms, while pressing <kbd>Esc</kbd> (or <kbd>Backspace</kbd> on empty query) cancels and reverts all carets and selections to their pre-search snapshot.
2. **Popup Dialog Mode**: Centered floating dialog window with match counter badge, useful for users preferring a separate floating modal window.

Switch between modes at any time:
- In IntelliJ Settings: **Preferences / Settings &rarr; Tools &rarr; Helix Keymap**
- In the `:` Command Picker: `:toggle-search-ui`, `:set search-ui=inline`, or `:set search-ui=popup`

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
│   │   │   ├── keymap/       # Sequence dispatching engine (g, space, [, ], count prefixes)
│   │   │   ├── motion/       # Word, line, and buffer motions with active selection semantics
│   │   │   ├── state/        # HelixMode (Normal, Insert, Select), state manager & cursor logic
│   │   │   └── ui/           # Status bar mode widget and factory
│   │   └── resources/
│   │       └── META-INF/
│   │           └── plugin.xml
│   └── test/
│       └── kotlin/jp/titze/intellij/helix/
│           └── HelixEditorTest.kt  # Headless test suite for modal editing and motions
└── README.md
```

---

## 🛠️ Development & Contributing

This simple extension was written out of my own need for a useful Helix-like IntelliJ extension 
after completely changing from Vim to Helix

