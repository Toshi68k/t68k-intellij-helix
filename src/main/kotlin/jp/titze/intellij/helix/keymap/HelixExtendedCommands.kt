// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixSelectionActions
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixRegistersPopup

internal object HelixExtendedCommands {

    fun registerAll() {
        registerUnimpaired()
        registerSurroundAndMatch()
        registerSelectionGrooming()
        registerInsertModeCommands()
        registerNumbers()
    }

    private fun registerUnimpaired() {
        HelixCommandRegistry.register("goto_prev_paragraph", "Go to previous paragraph") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveParagraph(ed, forward = false, count = cnt)
            true
        }
        HelixCommandRegistry.register("goto_next_paragraph", "Go to next paragraph") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveParagraph(ed, forward = true, count = cnt)
            true
        }
        HelixCommandRegistry.register("goto_prev_comment", "Go to previous comment") { ed, cnt ->
            HelixMotions.moveComment(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_comment", "Go to next comment") { ed, cnt ->
            HelixMotions.moveComment(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("goto_prev_function", "Go to previous function") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveFunction(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_function", "Go to next function") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveFunction(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("goto_prev_class", "Go to previous type definition") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveType(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_class", "Go to next type definition") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveType(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("goto_prev_diag", "Go to previous diagnostic") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveDiagnostic(ed, forward = false, count = cnt, toEnd = false)
        }
        HelixCommandRegistry.register("goto_next_diag", "Go to next diagnostic") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveDiagnostic(ed, forward = true, count = cnt, toEnd = false)
        }
        HelixCommandRegistry.register("goto_prev_change", "Go to previous VCS change") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveChange(ed, forward = false, count = cnt, toEnd = false)
        }
        HelixCommandRegistry.register("goto_next_change", "Go to next VCS change") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveChange(ed, forward = true, count = cnt, toEnd = false)
        }
        HelixCommandRegistry.register("goto_prev_xml_element", "Go to previous XML/HTML element") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveXmlElement(ed, forward = false, count = cnt)
            true
        }
        HelixCommandRegistry.register("goto_next_xml_element", "Go to next XML/HTML element") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotions.moveXmlElement(ed, forward = true, count = cnt)
            true
        }
    }

    private fun registerSurroundAndMatch() {
        HelixCommandRegistry.register("match_brackets", "Match opening/closing bracket") { ed, cnt ->
            jp.titze.intellij.helix.motion.HelixMotionHistory.recordMatchBracket()
            HelixMotions.matchBrackets(ed, cnt)
        }
        HelixCommandRegistry.register("surround_add", "Surround add") { ed, _ ->
            armSubmode(ed, 's')
        }
        HelixCommandRegistry.register("surround_delete", "Surround delete") { ed, _ ->
            armSubmode(ed, 'd')
        }
        HelixCommandRegistry.register("surround_replace", "Surround replace") { ed, _ ->
            armSubmode(ed, 'r')
        }
        HelixCommandRegistry.register("select_textobject_around", "Select textobject around") { ed, _ ->
            armSubmode(ed, 'a')
        }
        HelixCommandRegistry.register("select_textobject_inner", "Select textobject inner") { ed, _ ->
            armSubmode(ed, 'i')
        }
    }

    private fun armSubmode(editor: Editor, submodeChar: Char): Boolean {
        val state = HelixStateManager.getOrCreate(editor)
        state.clearPendingSequence()
        state.appendKey('m')
        state.appendKey(submodeChar)
        return true
    }

    private fun registerSelectionGrooming() {
        HelixCommandRegistry.register("flip_selections", "Flip selection cursor and anchor") { ed, _ ->
            HelixMotions.flipSelection(ed)
            true
        }
        HelixCommandRegistry.register("ensure_selections_forward", "Ensure selections are forward") { ed, _ ->
            HelixSelectionActions.ensureSelectionsForward(ed)
            true
        }
        HelixCommandRegistry.register("remove_primary_selection", "Remove primary selection") { ed, _ ->
            HelixMotions.removePrimarySelection(ed)
            true
        }
        HelixCommandRegistry.register("clear_selection", "Clear all extra selections") { ed, _ ->
            HelixMotions.keepOnlyPrimaryCaret(ed)
            true
        }
        HelixCommandRegistry.register("merge_selections", "Merge contiguous selections") { ed, _ ->
            HelixSelectionActions.mergeSelections(ed)
            true
        }
        HelixCommandRegistry.register("merge_consecutive_selections", "Merge all selections") { ed, _ ->
            HelixSelectionActions.mergeAllSelections(ed)
            true
        }
        HelixCommandRegistry.register("copy_selection_on_prev_line", "Copy selection on previous line") { ed, cnt ->
            HelixMotions.copySelectionOnPrevLine(ed, cnt)
            true
        }
        HelixCommandRegistry.register(
            "expand_selection",
            "Expand selection to parent node",
            "EditorSelectWord",
        ) { ed, _ ->
            HelixActionDelegate.executeAction("EditorSelectWord", ed)
        }
        HelixCommandRegistry.register(
            "shrink_selection",
            "Shrink selection to child node",
            "EditorUnSelectWord",
        ) { ed, _ ->
            HelixActionDelegate.executeAction("EditorUnSelectWord", ed)
        }
        HelixCommandRegistry.register("select_next_sibling", "Select next sibling AST node") { ed, _ ->
            HelixActions.selectNextSibling(ed)
        }
        HelixCommandRegistry.register("select_prev_sibling", "Select previous sibling AST node") { ed, _ ->
            HelixActions.selectPrevSibling(ed)
        }
    }

    private fun registerInsertModeCommands() {
        HelixCommandRegistry.register(
            "delete_char_backward",
            "Delete character backward",
            "EditorBackSpace",
        ) { ed, cnt ->
            repeat(cnt) { HelixActionDelegate.executeAction("EditorBackSpace", ed) }
            true
        }
        HelixCommandRegistry.register(
            "delete_char_forward",
            "Delete character forward",
            "EditorDelete",
        ) { ed, cnt ->
            repeat(cnt) { HelixActionDelegate.executeAction("EditorDelete", ed) }
            true
        }
        HelixCommandRegistry.register(
            "insert_newline",
            "Insert newline",
            "EditorEnter",
        ) { ed, cnt ->
            repeat(cnt) { HelixActionDelegate.executeAction("EditorEnter", ed) }
            true
        }
        HelixCommandRegistry.register("delete_word_backward", "Delete word backward") { ed, _ ->
            HelixActions.deleteWordBackward(ed)
            true
        }
        HelixCommandRegistry.register("delete_word_forward", "Delete word forward") { ed, _ ->
            HelixActions.deleteWordForward(ed)
            true
        }
        HelixCommandRegistry.register("kill_to_line_start", "Delete to line start") { ed, _ ->
            HelixActions.killToLineStart(ed)
            true
        }
        HelixCommandRegistry.register("kill_to_line_end", "Delete to line end") { ed, _ ->
            HelixActions.killToLineEnd(ed)
            true
        }
        HelixCommandRegistry.register("insert_register", "Insert register content") { ed, _ ->
            HelixRegistersPopup.show(ed)
            true
        }
        HelixCommandRegistry.register(
            "signature_help",
            "Show signature help / parameter info",
            "ParameterInfo",
        ) { ed, _ ->
            HelixActionDelegate.executeAction("ParameterInfo", ed)
        }
        HelixCommandRegistry.register("completion", "Trigger code completion", "CodeCompletion") { ed, _ ->
            HelixActionDelegate.executeAction("CodeCompletion", ed)
        }
    }

    private fun registerNumbers() {
        HelixCommandRegistry.register("increment", "Increment number under cursor") { ed, cnt ->
            HelixActions.increment(ed, cnt)
            true
        }
        HelixCommandRegistry.register("decrement", "Decrement number under cursor") { ed, cnt ->
            HelixActions.decrement(ed, cnt)
            true
        }
    }
}
