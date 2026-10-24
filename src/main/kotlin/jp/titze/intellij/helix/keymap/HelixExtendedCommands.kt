// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixSelectionActions
import jp.titze.intellij.helix.action.HelixSplitActions
import jp.titze.intellij.helix.motion.HelixFileNavigation
import jp.titze.intellij.helix.motion.HelixJumpToWord
import jp.titze.intellij.helix.motion.HelixMotionHistory
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.motion.HelixViewMotions
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixRegistersPopup
import jp.titze.intellij.helix.ui.HelixSearchManager

internal object HelixExtendedCommands {

    fun registerAll() {
        registerUnimpaired()
        registerSurroundAndMatch()
        registerSelectionGrooming()
        registerInsertModeCommands()
        registerNumbers()
        registerNavigationAndWindow()
        registerDapCommands()
    }

    private fun registerUnimpaired() {
        registerUnimpairedCodeNav()
        registerUnimpairedItems()
    }

    private fun registerUnimpairedCodeNav() {
        HelixCommandRegistry.register("goto_prev_paragraph", "Go to previous paragraph") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'p')
            HelixMotions.moveParagraph(ed, forward = false, count = cnt)
            true
        }
        HelixCommandRegistry.register("goto_next_paragraph", "Go to next paragraph") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'p')
            HelixMotions.moveParagraph(ed, forward = true, count = cnt)
            true
        }
        HelixCommandRegistry.register("goto_prev_comment", "Go to previous comment") { ed, cnt ->
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'c')
            HelixMotions.moveComment(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_comment", "Go to next comment") { ed, cnt ->
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'c')
            HelixMotions.moveComment(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("goto_prev_function", "Go to previous function") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'f')
            HelixMotions.moveFunction(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_function", "Go to next function") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'f')
            HelixMotions.moveFunction(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("goto_prev_class", "Go to previous type definition") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 't')
            HelixMotions.moveType(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_class", "Go to next type definition") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 't')
            HelixMotions.moveType(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("goto_prev_diag", "Go to previous diagnostic") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'd')
            HelixMotions.moveDiagnostic(ed, forward = false, count = cnt, toEnd = false)
        }
        HelixCommandRegistry.register("goto_next_diag", "Go to next diagnostic") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'd')
            HelixMotions.moveDiagnostic(ed, forward = true, count = cnt, toEnd = false)
        }
        HelixCommandRegistry.register("goto_first_diag", "Go to first diagnostic") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'D')
            HelixMotions.moveDiagnostic(ed, forward = false, count = cnt, toEnd = true)
        }
        HelixCommandRegistry.register("goto_last_diag", "Go to last diagnostic") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'D')
            HelixMotions.moveDiagnostic(ed, forward = true, count = cnt, toEnd = true)
        }
        HelixCommandRegistry.register("goto_prev_change", "Go to previous VCS change") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'g')
            HelixMotions.moveChange(ed, forward = false, count = cnt, toEnd = false)
        }
        HelixCommandRegistry.register("goto_next_change", "Go to next VCS change") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'g')
            HelixMotions.moveChange(ed, forward = true, count = cnt, toEnd = false)
        }
        HelixCommandRegistry.register("goto_first_change", "Go to first VCS change") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'G')
            HelixMotions.moveChange(ed, forward = false, count = cnt, toEnd = true)
        }
        HelixCommandRegistry.register("goto_last_change", "Go to last VCS change") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'G')
            HelixMotions.moveChange(ed, forward = true, count = cnt, toEnd = true)
        }
    }

    private fun registerUnimpairedItems() {
        HelixCommandRegistry.register("goto_prev_entry", "Go to previous entry") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'e')
            HelixMotions.moveEntry(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_entry", "Go to next entry") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'e')
            HelixMotions.moveEntry(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("goto_prev_spelling_error", "Go to previous spelling error") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 's')
            repeat(cnt) { HelixActionDelegate.executeAction("GotoPreviousSpellingError", ed) }
            true
        }
        HelixCommandRegistry.register("goto_next_spelling_error", "Go to next spelling error") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 's')
            repeat(cnt) { HelixActionDelegate.executeAction("GotoNextSpellingError", ed) }
            true
        }
        HelixCommandRegistry.register("goto_prev_test", "Go to previous test") { ed, cnt ->
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'T')
            HelixMotions.moveTest(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_test", "Go to next test") { ed, cnt ->
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'T')
            HelixMotions.moveTest(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("goto_prev_parameter", "Go to previous parameter") { ed, cnt ->
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'a')
            HelixMotions.moveParameter(ed, forward = false, count = cnt)
        }
        HelixCommandRegistry.register("goto_next_parameter", "Go to next parameter") { ed, cnt ->
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'a')
            HelixMotions.moveParameter(ed, forward = true, count = cnt)
        }
        HelixCommandRegistry.register("add_newline_above", "Add newline above") { ed, cnt ->
            HelixMotions.addNewline(ed, below = false, count = cnt)
            true
        }
        HelixCommandRegistry.register("add_newline_below", "Add newline below") { ed, cnt ->
            HelixMotions.addNewline(ed, below = true, count = cnt)
            true
        }
        HelixCommandRegistry.register("goto_prev_xml_element", "Go to previous XML/HTML element") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = true, targetChar = 'x')
            HelixMotions.moveXmlElement(ed, forward = false, count = cnt)
            true
        }
        HelixCommandRegistry.register("goto_next_xml_element", "Go to next XML/HTML element") { ed, cnt ->
            HelixKeyHandler.recordJump(ed)
            HelixMotionHistory.recordBracket(isOpen = false, targetChar = 'x')
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
        HelixCommandRegistry.register("split_selection_on_newlines", "Split selection on newlines") { ed, _ ->
            HelixMotions.splitSelectionOnNewlines(ed)
            true
        }
        HelixCommandRegistry.register("shrink_to_line_bounds", "Shrink to line bounds") { ed, _ ->
            HelixMotions.shrinkToLineBounds(ed)
            true
        }
        HelixCommandRegistry.register("remove_selections", "Remove matching selections") { ed, _ ->
            HelixSearchManager.startRemoveSelections(ed)
            true
        }
        HelixCommandRegistry.register(
            "rotate_selections_contents_forward",
            "Rotate selections contents forward",
        ) { ed, _ ->
            HelixActions.rotateSelectionsContents(ed, forward = true)
            true
        }
        HelixCommandRegistry.register(
            "rotate_selections_contents_backward",
            "Rotate selections contents backward",
        ) { ed, _ ->
            HelixActions.rotateSelectionsContents(ed, forward = false)
            true
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

    private fun registerNavigationAndWindow() {
        HelixCommandRegistry.register("goto_column", "Goto column") { ed, cnt ->
            HelixMotions.gotoColumn(ed, cnt)
            true
        }
        HelixCommandRegistry.register("goto_word", "Jump to word") { ed, _ ->
            HelixJumpToWord.start(ed)
        }
        HelixCommandRegistry.register(
            "goto_last_modification",
            "Jump to last change",
            "JumpToLastChange",
        ) { ed, _ ->
            HelixKeyHandler.recordJump(ed)
            HelixActionDelegate.executeAction("JumpToLastChange", ed)
        }
        HelixCommandRegistry.register("last_picker", "Open last picker") { ed, _ ->
            val picker = HelixSpaceKeymap.lastPickerChar ?: 'b'
            HelixSpaceKeymap.handle(picker, ed)
        }
        HelixCommandRegistry.register("select_references", "Find references / usages", "FindUsages") { ed, _ ->
            HelixActionDelegate.executeAction("HighlightUsagesInFile", ed) ||
                HelixActionDelegate.executeAction("FindUsages", ed)
        }
        HelixCommandRegistry.register("window_mode", "Enter window mode") { ed, _ ->
            HelixKeyHandler.startWindowChord(ed)
            true
        }
        HelixCommandRegistry.register("page_cursor_half_down", "Scroll half page down") { ed, cnt ->
            HelixViewMotions.scrollHalfPageDown(ed, cnt)
            true
        }
        HelixCommandRegistry.register("page_cursor_half_up", "Scroll half page up") { ed, cnt ->
            HelixViewMotions.scrollHalfPageUp(ed, cnt)
            true
        }
        HelixCommandRegistry.register("goto_file_hsplit", "Goto file in horizontal split") { ed, _ ->
            HelixFileNavigation.gotoFileInSplit(ed, vertical = false)
        }
        HelixCommandRegistry.register("goto_file_vsplit", "Goto file in vertical split") { ed, _ ->
            HelixFileNavigation.gotoFileInSplit(ed, vertical = true)
        }
        HelixCommandRegistry.register("swap_view_left", "Swap split left") { ed, _ ->
            HelixActionDelegate.executeAction("ChangeSplitOrientation", ed)
        }
        HelixCommandRegistry.register("swap_view_down", "Swap split down") { ed, _ ->
            HelixActionDelegate.executeAction("ChangeSplitOrientation", ed)
        }
        HelixCommandRegistry.register("swap_view_up", "Swap split up") { ed, _ ->
            HelixActionDelegate.executeAction("ChangeSplitOrientation", ed)
        }
        HelixCommandRegistry.register("swap_view_right", "Swap split right") { ed, _ ->
            HelixActionDelegate.executeAction("ChangeSplitOrientation", ed)
        }
        HelixCommandRegistry.register("hsplit_new", "New horizontal scratch split") { ed, _ ->
            HelixSplitActions.openScratchSplit(ed, vertical = false)
        }
        HelixCommandRegistry.register("vsplit_new", "New vertical scratch split") { ed, _ ->
            HelixSplitActions.openScratchSplit(ed, vertical = true)
        }
        HelixCommandRegistry.register("jump_prev_view", "Focus previous split", "PrevSplitter") { ed, _ ->
            HelixActionDelegate.executeAction("PrevSplitter", ed)
        }
        HelixCommandRegistry.register("jump_next_view", "Focus next split", "NextSplitter") { ed, _ ->
            HelixActionDelegate.executeAction("NextSplitter", ed)
        }
        HelixCommandRegistry.register("switch_to_uppercase", "Switch to uppercase") { ed, cnt ->
            HelixActions.toUpperCase(ed, cnt)
            true
        }
    }

    private fun registerDapCommands() {
        HelixCommandRegistry.register(
            "dap_toggle_breakpoint",
            "Toggle breakpoint",
            "ToggleLineBreakpoint",
        ) { ed, _ ->
            HelixActionDelegate.executeAction("ToggleLineBreakpoint", ed)
        }
        HelixCommandRegistry.register("dap_continue", "Continue / Resume", "Resume") { ed, _ ->
            HelixActionDelegate.executeAction("Resume", ed)
        }
        HelixCommandRegistry.register("dap_step_in", "Step into", "StepInto") { ed, _ ->
            HelixActionDelegate.executeAction("StepInto", ed)
        }
        HelixCommandRegistry.register("dap_next", "Step over", "StepOver") { ed, _ ->
            HelixActionDelegate.executeAction("StepOver", ed)
        }
        HelixCommandRegistry.register("dap_step_out", "Step out", "StepOut") { ed, _ ->
            HelixActionDelegate.executeAction("StepOut", ed)
        }
        HelixCommandRegistry.register("dap_pause", "Pause program", "Pause") { ed, _ ->
            HelixActionDelegate.executeAction("Pause", ed)
        }
        HelixCommandRegistry.register("dap_launch", "Launch debugger", "Debug") { ed, _ ->
            HelixActionDelegate.executeAction("Debug", ed)
        }
        HelixCommandRegistry.register("dap_restart", "Restart session", "Rerun") { ed, _ ->
            HelixActionDelegate.executeAction("Rerun", ed)
        }
        HelixCommandRegistry.register("run", "Run without debugger", "RunClass") { ed, _ ->
            HelixActionDelegate.executeAction("RunClass", ed) || HelixActionDelegate.executeAction("Run", ed)
        }
        HelixCommandRegistry.register("dap_terminate", "Terminate session", "Stop") { ed, _ ->
            HelixActionDelegate.executeAction("Stop", ed)
        }
        HelixCommandRegistry.register("dap_variables", "Show debug window", "ActivateDebugToolWindow") { ed, _ ->
            HelixActionDelegate.executeAction("ActivateDebugToolWindow", ed)
        }
        HelixCommandRegistry.register("dap_evaluate", "Evaluate expression", "EvaluateExpression") { ed, _ ->
            HelixActionDelegate.executeAction("EvaluateExpression", ed)
        }
        HelixCommandRegistry.register("dap_edit_condition", "Edit breakpoint", "EditBreakpoint") { ed, _ ->
            HelixActionDelegate.executeAction("EditBreakpoint", ed)
        }
        HelixCommandRegistry.register("dap_view_breakpoints", "View breakpoints", "ViewBreakpoints") { ed, _ ->
            HelixActionDelegate.executeAction("ViewBreakpoints", ed)
        }
    }
}
