package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixSurroundActions
import jp.titze.intellij.helix.action.HelixTextObjectActions
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixEditorState
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixSearchManager
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

object HelixKeyHandler {

    fun handleKey(charTyped: Char, editor: Editor): Boolean {
        val state = HelixStateManager.getOrCreate(editor)

        if (state.mode.isInsertable) {
            return false // Let standard editor typing handle it
        }

        val seq = state.pendingSequence

        // Check if we are waiting for the second key of a multi-key prefix
        if (seq.isNotEmpty()) {
            return handlePendingSequence(seq, charTyped, editor, state)
        }

        // Accumulate count digits when no prefix is pending
        if (charTyped in '1'..'9' || (charTyped == '0' && state.hasCount)) {
            state.appendCountDigit(charTyped)
            return true
        }

        // If count was entered, allow optional space separator (e.g. "100 gg")
        if (charTyped == ' ' && state.hasCount) {
            return true
        }

        // Check if character starts a multi-key sequence
        when (charTyped) {
            'g', ' ', '[', ']', 'm' -> {
                state.appendKey(charTyped)
                HelixWhichKeyPopup.show(editor, charTyped.toString())
                return true
            }
            'f', 't', 'F', 'T', 'r' -> {
                state.appendKey(charTyped)
                return true
            }
            ':' -> {
                state.clearCount()
                HelixWhichKeyPopup.hide()
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
        HelixWhichKeyPopup.hide()
        val count = state.takeCount()
        when (prefix) {
            "r" -> {
                state.clearPendingSequence()
                HelixActions.replaceChar(editor, ch, count ?: 1)
                return true
            }
            "f", "t", "F", "T" -> {
                state.clearPendingSequence()
                return HelixMotions.findCharMotion(editor, prefix[0], ch, count ?: 1)
            }
            "g" -> {
                state.clearPendingSequence()
                return handleGMenu(ch, editor, count)
            }
            " " -> {
                state.clearPendingSequence()
                return handleSpaceMenu(ch, editor)
            }
            "[" -> {
                state.clearPendingSequence()
                return handleBracketOpen(ch, editor, count ?: 1)
            }
            "]" -> {
                state.clearPendingSequence()
                return handleBracketClose(ch, editor, count ?: 1)
            }
            "m" -> return handleMatchMenu(ch, editor, state)
            "ms" -> {
                state.clearPendingSequence()
                return HelixSurroundActions.surroundAdd(editor, ch)
            }
            "md" -> {
                state.clearPendingSequence()
                return HelixSurroundActions.surroundDelete(editor, ch)
            }
            "mr" -> {
                state.appendKey(ch)
                return true
            }
            "ma" -> {
                state.clearPendingSequence()
                return HelixTextObjectActions.selectTextObject(editor, inside = false, objectChar = ch)
            }
            "mi" -> {
                state.clearPendingSequence()
                return HelixTextObjectActions.selectTextObject(editor, inside = true, objectChar = ch)
            }
            else -> {
                if (prefix.startsWith("mr") && prefix.length == 3) {
                    val fromChar = prefix[2]
                    state.clearPendingSequence()
                    return HelixSurroundActions.surroundReplace(editor, fromChar, ch)
                }
                state.clearPendingSequence()
                return false
            }
        }
    }

    private fun handleMatchMenu(ch: Char, editor: Editor, state: HelixEditorState): Boolean {
        when (ch) {
            's', 'd', 'r', 'a', 'i' -> {
                state.appendKey(ch)
                return true
            }
            'm' -> {
                state.clearPendingSequence()
                return HelixActionDelegate.executeAction("EditorMatchBracket", editor)
            }
            else -> {
                state.clearPendingSequence()
                return false
            }
        }
    }

    private fun handleGMenu(ch: Char, editor: Editor, count: Int? = null): Boolean {
        return when (ch) {
            'd' -> HelixActionDelegate.executeAction("GotoDeclaration", editor)
            'y' -> HelixActionDelegate.executeAction("GotoTypeDeclaration", editor)
            'r' -> HelixActionDelegate.executeAction("FindUsages", editor)
            'h' -> {
                HelixMotions.moveLineStart(editor)
                true
            }
            's' -> {
                HelixMotions.moveLineFirstNonWhitespace(editor)
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
                HelixMotions.moveFileStart(editor, count)
                true
            }
            else -> false
        }
    }

    private fun handleSpaceMenu(ch: Char, editor: Editor): Boolean {
        return when (ch) {
            'f' -> HelixActionDelegate.executeAction("GotoFile", editor) || HelixActionDelegate.executeAction("SearchEverywhere", editor)
            'b' -> HelixActionDelegate.executeAction("RecentFiles", editor)
            '/' -> HelixActionDelegate.executeAction("FindInPath", editor)
            'j' -> HelixActionDelegate.executeAction("RecentLocations", editor)
            's' -> HelixActionDelegate.executeAction("FileStructurePopup", editor)
            'S' -> HelixActionDelegate.executeAction("GotoSymbol", editor)
            'd' -> HelixActionDelegate.executeAction("ShowErrorDescription", editor)
            'D' -> HelixActionDelegate.executeAction("ActivateProblemsViewToolWindow", editor)
            'a' -> HelixActionDelegate.executeAction("ShowIntentionActions", editor)
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
            'R' -> {
                HelixActions.replaceWithClipboard(editor)
                true
            }
            'k' -> HelixActionDelegate.executeAction("QuickJavaDoc", editor)
            '?' -> HelixActionDelegate.executeAction("GotoAction", editor)
            else -> false
        }
    }

    private fun handleBracketOpen(ch: Char, editor: Editor, count: Int): Boolean {
        return when (ch) {
            'd' -> HelixMotions.moveDiagnostic(editor, forward = false, count = count, toEnd = false)
            'D' -> HelixMotions.moveDiagnostic(editor, forward = false, count = count, toEnd = true)
            'b' -> repeat(count) { HelixActionDelegate.executeAction("PreviousTab", editor) }.let { true }
            'f' -> HelixMotions.moveFunction(editor, forward = false, count = count)
            't' -> HelixMotions.moveType(editor, forward = false, count = count)
            'a' -> HelixMotions.moveParameter(editor, forward = false, count = count)
            'c' -> HelixMotions.moveComment(editor, forward = false, count = count)
            'T' -> HelixMotions.moveTest(editor, forward = false, count = count)
            'p' -> {
                HelixMotions.moveParagraph(editor, forward = false, count = count)
                true
            }
            'g' -> HelixMotions.moveChange(editor, forward = false, count = count, toEnd = false)
            'G' -> HelixMotions.moveChange(editor, forward = false, count = count, toEnd = true)
            ' ' -> {
                HelixMotions.addNewline(editor, below = false, count = count)
                true
            }
            else -> false
        }
    }

    private fun handleBracketClose(ch: Char, editor: Editor, count: Int): Boolean {
        return when (ch) {
            'd' -> HelixMotions.moveDiagnostic(editor, forward = true, count = count, toEnd = false)
            'D' -> HelixMotions.moveDiagnostic(editor, forward = true, count = count, toEnd = true)
            'b' -> repeat(count) { HelixActionDelegate.executeAction("NextTab", editor) }.let { true }
            'f' -> HelixMotions.moveFunction(editor, forward = true, count = count)
            't' -> HelixMotions.moveType(editor, forward = true, count = count)
            'a' -> HelixMotions.moveParameter(editor, forward = true, count = count)
            'c' -> HelixMotions.moveComment(editor, forward = true, count = count)
            'T' -> HelixMotions.moveTest(editor, forward = true, count = count)
            'p' -> {
                HelixMotions.moveParagraph(editor, forward = true, count = count)
                true
            }
            'g' -> HelixMotions.moveChange(editor, forward = true, count = count, toEnd = false)
            'G' -> HelixMotions.moveChange(editor, forward = true, count = count, toEnd = true)
            ' ' -> {
                HelixMotions.addNewline(editor, below = true, count = count)
                true
            }
            else -> false
        }
    }

    private fun handleSingleKey(ch: Char, editor: Editor, state: HelixEditorState): Boolean {
        val rawCount = state.takeCount()
        val count = rawCount ?: 1

        when (ch) {
            // Motions
            'w' -> HelixMotions.moveNextWordStart(editor, count)
            'b' -> HelixMotions.movePrevWordStart(editor, count)
            'e' -> HelixMotions.moveWordEnd(editor, count)
            'x' -> HelixMotions.selectLine(editor, count)
            '%' -> HelixMotions.selectAll(editor)
            'h' -> HelixMotions.moveLeft(editor, count)
            'j' -> HelixMotions.moveDown(editor, count)
            'k' -> HelixMotions.moveUp(editor, count)
            'l' -> HelixMotions.moveRight(editor, count)
            'G' -> {
                if (rawCount != null) {
                    HelixMotions.moveFileStart(editor, rawCount)
                } else {
                    HelixMotions.moveFileEnd(editor)
                }
            }
            ';' -> HelixMotions.collapseSelection(editor)
            ',' -> HelixMotions.keepOnlyPrimaryCaret(editor)
            '(' -> HelixMotions.rotateSelections(editor, forward = false)
            ')' -> HelixMotions.rotateSelections(editor, forward = true)
            'C' -> HelixMotions.copySelectionOnNextLine(editor, count)
            '\u0006' -> HelixMotions.pageDown(editor, count)
            '\u0002' -> HelixMotions.pageUp(editor, count)
            '\u0004' -> HelixMotions.halfPageDown(editor, count)
            '\u0015' -> HelixMotions.halfPageUp(editor, count)

            // Actions
            'd' -> HelixActions.deleteSelection(editor)
            'c' -> HelixActions.deleteSelection(editor, enterInsert = true)
            'y' -> HelixActions.yankSelection(editor)
            'p' -> HelixActions.paste(editor, after = true)
            'P' -> HelixActions.paste(editor, after = false)
            'R' -> HelixActions.replaceWithClipboard(editor)
            'J' -> HelixActions.joinLines(editor, count)

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
            'u' -> repeat(count) { HelixActionDelegate.executeAction("\$Undo", editor) }
            'U' -> repeat(count) { HelixActionDelegate.executeAction("\$Redo", editor) }
            'K' -> HelixActionDelegate.executeAction("QuickJavaDoc", editor)
            '=' -> HelixActionDelegate.executeAction("ReformatCode", editor)
            '>' -> HelixActionDelegate.executeAction("EditorIndentSelection", editor)
            '<' -> HelixActionDelegate.executeAction("EditorUnindentSelection", editor)
            '~' -> HelixActionDelegate.executeAction("ToggleCase", editor)
            's' -> HelixSearchManager.startSelect(editor, isSplit = false)
            'S' -> HelixSearchManager.startSelect(editor, isSplit = true)
            '/' -> HelixSearchManager.startSearch(editor, backward = false, count = count)
            '?' -> HelixSearchManager.startSearch(editor, backward = true, count = count)
            'n' -> HelixActions.searchNext(editor, count)
            'N' -> HelixActions.searchPrev(editor, count)
            '*' -> HelixActions.searchSelection(editor)

            else -> return false
        }
        return true
    }
}
