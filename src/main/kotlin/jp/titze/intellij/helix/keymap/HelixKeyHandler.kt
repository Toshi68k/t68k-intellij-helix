package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixEditorState
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixSearchManager
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

object HelixKeyHandler {

    internal fun recordJump(editor: Editor) {
        val project = editor.project ?: return
        HelixJumpListService.getInstance(project).recordCurrent(editor)
    }

    fun handleKey(charTyped: Char, editor: Editor): Boolean {
        val state = HelixStateManager.getOrCreate(editor)

        if (state.mode.isInsertable) {
            return false // Let standard editor typing handle it
        }

        val seq = state.pendingSequence

        // Check if we are waiting for the second key of a multi-key prefix
        if (seq.isNotEmpty()) {
            if (seq == "Z" && isCountDigit(charTyped, state.hasCount)) {
                state.appendCountDigit(charTyped)
                return true
            }
            return handlePendingSequence(seq, charTyped, editor, state)
        }

        // Accumulate count digits when no prefix is pending
        if (isCountDigit(charTyped, state.hasCount)) {
            state.appendCountDigit(charTyped)
            return true
        }

        // If count was entered, allow optional space separator (e.g. "100 gg")
        if (charTyped == ' ' && state.hasCount) {
            return true
        }

        // Check if character starts a multi-key sequence
        when (charTyped) {
            'g', ' ', '[', ']', 'm', 'z', 'Z' -> {
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

    private fun handlePendingSequence(prefix: String, ch: Char, editor: Editor, state: HelixEditorState): Boolean {
        if (prefix != "Z") {
            HelixWhichKeyPopup.hide()
        }
        val count = state.takeCount()
        return when (prefix) {
            "r" -> {
                state.clearPendingSequence()
                HelixActions.replaceChar(editor, ch, count ?: 1)
                true
            }

            "f", "t", "F", "T" -> {
                state.clearPendingSequence()
                HelixMotions.findCharMotion(editor, prefix[0], ch, count ?: 1)
            }

            "g" -> {
                state.clearPendingSequence()
                HelixGotoKeymap.handle(ch, editor, count)
            }

            " " -> {
                state.clearPendingSequence()
                HelixSpaceKeymap.handle(ch, editor)
            }

            "[" -> {
                state.clearPendingSequence()
                HelixBracketKeymap.handleOpen(ch, editor, count ?: 1)
            }

            "]" -> {
                state.clearPendingSequence()
                HelixBracketKeymap.handleClose(ch, editor, count ?: 1)
            }

            "z" -> {
                state.clearPendingSequence()
                HelixViewKeymap.handle(ch, editor, count)
            }

            "Z" -> {
                val handled = HelixViewKeymap.handle(ch, editor, count)
                if (handled) {
                    if (!HelixWhichKeyPopup.isShowing("Z")) {
                        HelixWhichKeyPopup.show(editor, "Z")
                    }
                    true
                } else {
                    state.clearPendingSequence()
                    HelixWhichKeyPopup.hide()
                    false
                }
            }

            "m" -> HelixMatchKeymap.handle(ch, editor, state, count)

            else -> HelixMatchKeymap.handleSubmenu(prefix, ch, editor, state)
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

            '%' -> {
                recordJump(editor)
                HelixMotions.selectAll(editor)
            }

            'h' -> HelixMotions.moveLeft(editor, count)

            'j' -> HelixMotions.moveDown(editor, count)

            'k' -> HelixMotions.moveUp(editor, count)

            'l' -> HelixMotions.moveRight(editor, count)

            'G' -> {
                recordJump(editor)
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

            '\u000F' -> {
                val project = editor.project
                if (project != null) {
                    HelixJumpListService.getInstance(project).jumpBackward(editor, count)
                }
            }

            '\u0013' -> {
                val project = editor.project
                if (project != null) {
                    HelixJumpListService.getInstance(project).recordCurrent(editor, force = true)
                }
            }

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

            '/' -> {
                recordJump(editor)
                HelixSearchManager.startSearch(editor, backward = false, count = count)
            }

            '?' -> {
                recordJump(editor)
                HelixSearchManager.startSearch(editor, backward = true, count = count)
            }

            'n' -> {
                recordJump(editor)
                HelixActions.searchNext(editor, count)
            }

            'N' -> {
                recordJump(editor)
                HelixActions.searchPrev(editor, count)
            }

            '*' -> {
                recordJump(editor)
                HelixActions.searchSelection(editor)
            }

            else -> return false
        }
        return true
    }

    private fun isCountDigit(ch: Char, hasCount: Boolean): Boolean = ch in '1'..'9' || (ch == '0' && hasCount)
}
