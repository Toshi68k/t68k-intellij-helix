package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixEditorState
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixKeyHandler {

    fun handleKey(charTyped: Char, editor: Editor): Boolean {
        val state = HelixStateManager.getOrCreate(editor)

        if (state.mode == HelixMode.INSERT) {
            return false // Let standard editor typing handle it
        }

        val seq = state.pendingSequence

        // Check if we are waiting for the second key of a multi-key prefix
        if (seq.isNotEmpty()) {
            return handlePendingSequence(seq, charTyped, editor, state)
        }

        // Check if character starts a multi-key sequence
        when (charTyped) {
            'g', ' ', '[', ']' -> {
                state.appendKey(charTyped)
                return true
            }
            ':' -> {
                HelixCommandPopup.show(editor)
                return true
            }
        }

        // Single key commands
        return handleSingleKey(charTyped, editor, state)
    }

    private fun handlePendingSequence(
        prefix: String,
        ch: Char,
        editor: Editor,
        state: HelixEditorState
    ): Boolean {
        state.clearPendingSequence()

        when (prefix) {
            "g" -> return handleGMenu(ch, editor)
            " " -> return handleSpaceMenu(ch, editor)
            "[" -> return handleBracketOpen(ch, editor)
            "]" -> return handleBracketClose(ch, editor)
        }

        return true
    }

    private fun handleGMenu(ch: Char, editor: Editor): Boolean {
        return when (ch) {
            'd' -> HelixActionDelegate.executeAction("GotoDeclaration", editor)
            'y' -> HelixActionDelegate.executeAction("GotoTypeDeclaration", editor)
            'r' -> HelixActionDelegate.executeAction("FindUsages", editor)
            'h' -> {
                HelixMotions.moveLineStart(editor)
                true
            }
            'l' -> {
                HelixMotions.moveLineEnd(editor)
                true
            }
            'e' -> {
                HelixMotions.moveFileEnd(editor)
                true
            }
            'g' -> {
                HelixMotions.moveFileStart(editor)
                true
            }
            else -> false
        }
    }

    private fun handleSpaceMenu(ch: Char, editor: Editor): Boolean {
        return when (ch) {
            'f' -> HelixActionDelegate.executeAction("SearchEverywhere", editor)
            'b' -> HelixActionDelegate.executeAction("RecentFiles", editor)
            's' -> HelixActionDelegate.executeAction("FileStructurePopup", editor)
            'S' -> HelixActionDelegate.executeAction("GotoSymbol", editor)
            'a' -> HelixActionDelegate.executeAction("ShowIntentionActions", editor)
            'd' -> HelixActionDelegate.executeAction("ShowErrorDescription", editor)
            'r' -> HelixActionDelegate.executeAction("RenameElement", editor)
            'w' -> HelixActionDelegate.executeAction("SaveAll", editor)
            'y' -> {
                HelixActions.yankSelection(editor)
                true
            }
            'p' -> {
                HelixActions.paste(editor, after = true)
                true
            }
            'P' -> {
                HelixActions.paste(editor, after = false)
                true
            }
            else -> false
        }
    }

    private fun handleBracketOpen(ch: Char, editor: Editor): Boolean {
        return when (ch) {
            'd' -> HelixActionDelegate.executeAction("GotoPreviousError", editor)
            'b' -> HelixActionDelegate.executeAction("PreviousTab", editor)
            else -> false
        }
    }

    private fun handleBracketClose(ch: Char, editor: Editor): Boolean {
        return when (ch) {
            'd' -> HelixActionDelegate.executeAction("GotoNextError", editor)
            'b' -> HelixActionDelegate.executeAction("NextTab", editor)
            else -> false
        }
    }

    private fun handleSingleKey(ch: Char, editor: Editor, state: HelixEditorState): Boolean {
        when (ch) {
            // Motions
            'w' -> HelixMotions.moveNextWordStart(editor)
            'b' -> HelixMotions.movePrevWordStart(editor)
            'e' -> HelixMotions.moveWordEnd(editor)
            'x' -> HelixMotions.selectLine(editor)
            '%' -> HelixMotions.selectAll(editor)
            'h' -> HelixMotions.moveLeft(editor)
            'j' -> HelixMotions.moveDown(editor)
            'k' -> HelixMotions.moveUp(editor)
            'l' -> HelixMotions.moveRight(editor)
            ';' -> HelixMotions.collapseSelection(editor)
            ',' -> HelixMotions.keepOnlyPrimaryCaret(editor)

            // Actions
            'd' -> HelixActions.deleteSelection(editor)
            'c' -> HelixActions.deleteSelection(editor, enterInsert = true)
            'y' -> HelixActions.yankSelection(editor)
            'p' -> HelixActions.paste(editor, after = true)
            'P' -> HelixActions.paste(editor, after = false)

            // Insert transitions
            'i' -> HelixActions.enterInsert(editor)
            'a' -> HelixActions.enterInsertAppend(editor)
            'I' -> HelixActions.enterInsertLineStart(editor)
            'A' -> HelixActions.enterInsertLineEnd(editor)
            'o' -> {
                HelixActionDelegate.executeAction("EditorStartNewLine", editor)
                HelixActions.enterInsert(editor)
            }
            'O' -> {
                HelixActionDelegate.executeAction("EditorStartNewLineBefore", editor)
                HelixActions.enterInsert(editor)
            }

            // Mode & History & IDE
            'v' -> HelixActions.toggleSelectMode(editor)
            'u' -> HelixActionDelegate.executeAction("\$Undo", editor)
            'U' -> HelixActionDelegate.executeAction("\$Redo", editor)
            'K' -> HelixActionDelegate.executeAction("QuickJavaDoc", editor)
            '=' -> HelixActionDelegate.executeAction("ReformatCode", editor)
            '>' -> HelixActionDelegate.executeAction("EditorIndentSelection", editor)
            '<' -> HelixActionDelegate.executeAction("EditorUnindentSelection", editor)
            '~' -> HelixActionDelegate.executeAction("ToggleCase", editor)
            's' -> HelixActionDelegate.executeAction("Find", editor)

            else -> return false
        }
        return true
    }
}
