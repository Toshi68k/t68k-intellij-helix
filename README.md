# Helix Keymap for IntelliJ IDEA

[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](https://github.com/t68k/t68k-intellij-helix)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2024.2+-000000.svg?logo=intellij-idea&logoColor=white)](https://plugins.jetbrains.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
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
| `w` | Advance to the start of the next word |
| `b` | Move backward to the start of the previous word |
| `e` | Advance to the end of the current/next word |
| `ge` | Move backward to the end of the previous word |
| `x` | Select current line (including newline); pressing `x` again extends to the next line |
| `%` | Select entire buffer |
| `h` / `j` / `k` / `l` | Move left / down / up / right (mode-aware selection update) |
| `gh` | Move to line start (first non-whitespace character) |
| `gl` | Move to line end |
| `gg` | Move to the top of the buffer |
| `ge` *(in `g` menu)* | Move to the end of the buffer |

### Selection Manipulation

| Key | Description |
|-----|-------------|
| `;` | Collapse selection to a single cursor at caret |
| `Alt+;` | Flip selection anchor and cursor |
| `,` | Remove secondary carets, keeping only primary caret |
| `v` | Toggle between `Normal` and `Select` mode |

### Actions on Selection

| Key | Description |
|-----|-------------|
| `d` | Delete active selection & copy to clipboard |
| `c` | Delete active selection, copy to clipboard, and enter `Insert` mode |
| `y` | Yank (copy) active selection to clipboard |
| `p` | Paste clipboard after selection / caret |
| `P` | Paste clipboard before selection / caret |
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
- `gh` &rarr; Line start
- `gl` &rarr; Line end
- `ge` &rarr; Goto end of buffer
- `gg` &rarr; Goto start of buffer

#### Pickers & Diagnostics (`space` menu)
- `space + f` &rarr; `SearchEverywhere` (File search)
- `space + b` &rarr; `RecentFiles` (Buffer switcher)
- `space + s` &rarr; `FileStructurePopup` (Document symbols)
- `space + S` &rarr; `GotoSymbol` (Workspace symbols)
- `space + a` &rarr; `ShowIntentionActions` (Quick-fixes & intentions)
- `space + d` &rarr; `ShowErrorDescription` (Diagnostic tooltip under caret)
- `space + r` &rarr; `RenameElement` (Native refactor rename)
- `space + w` &rarr; `SaveAll`
- `space + y` / `space + p` &rarr; System clipboard yank / paste

#### Diagnostics Navigation
- `[d` &rarr; `GotoPreviousError`
- `]d` &rarr; `GotoNextError`
- `[b` / `]b` &rarr; Previous / Next editor tab

#### Code AST & Inspection
- `Alt+o` &rarr; Expand structural selection via PSI hierarchy (`SelectWordAtCaret`)
- `Alt+i` &rarr; Shrink structural selection (`UnselectWordAtCaret`)
- `Alt+n` &rarr; Select next occurrence (`SelectNextOccurrence`)
- `Ctrl+c` &rarr; Toggle line comment (`CommentByLineComment`)
- `K` &rarr; Quick documentation hover (`QuickJavaDoc`)

#### Command Palette (`:`)
Press `:` in Normal mode to open the command palette:
- `:w` &rarr; Save all files (`SaveAll`)
- `:q` &rarr; Close active tab (`CloseContent`)
- `:wq` / `:x` &rarr; Save all and close active tab
- `:wa` &rarr; Save all files
- `:qa` &rarr; Close all editors (`CloseAllEditors`)
- `:vsp` &rarr; Split editor vertically
- `:sp` &rarr; Split editor horizontally
- `:format` &rarr; Reformat code

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

