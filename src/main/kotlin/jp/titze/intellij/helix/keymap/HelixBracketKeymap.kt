package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.motion.HelixMotions

internal object HelixBracketKeymap {

    fun handleOpen(ch: Char, editor: Editor, count: Int): Boolean = when (ch) {
        'd' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveDiagnostic(editor, forward = false, count = count, toEnd = false)
        }

        'D' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveDiagnostic(editor, forward = false, count = count, toEnd = true)
        }

        'b' -> {
            HelixKeyHandler.recordJump(editor)
            repeat(count) { HelixActionDelegate.executeAction("PreviousTab", editor) }.let { true }
        }

        'f' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveFunction(editor, forward = false, count = count)
        }

        't' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveType(editor, forward = false, count = count)
        }

        'a' -> HelixMotions.moveParameter(editor, forward = false, count = count)

        'c' -> HelixMotions.moveComment(editor, forward = false, count = count)

        'T' -> HelixMotions.moveTest(editor, forward = false, count = count)

        'p' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveParagraph(editor, forward = false, count = count)
            true
        }

        'g' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveChange(editor, forward = false, count = count, toEnd = false)
        }

        'G' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveChange(editor, forward = false, count = count, toEnd = true)
        }

        ' ' -> {
            HelixMotions.addNewline(editor, below = false, count = count)
            true
        }

        else -> false
    }

    fun handleClose(ch: Char, editor: Editor, count: Int): Boolean = when (ch) {
        'd' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveDiagnostic(editor, forward = true, count = count, toEnd = false)
        }

        'D' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveDiagnostic(editor, forward = true, count = count, toEnd = true)
        }

        'b' -> {
            HelixKeyHandler.recordJump(editor)
            repeat(count) { HelixActionDelegate.executeAction("NextTab", editor) }.let { true }
        }

        'f' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveFunction(editor, forward = true, count = count)
        }

        't' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveType(editor, forward = true, count = count)
        }

        'a' -> HelixMotions.moveParameter(editor, forward = true, count = count)

        'c' -> HelixMotions.moveComment(editor, forward = true, count = count)

        'T' -> HelixMotions.moveTest(editor, forward = true, count = count)

        'p' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveParagraph(editor, forward = true, count = count)
            true
        }

        'g' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveChange(editor, forward = true, count = count, toEnd = false)
        }

        'G' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveChange(editor, forward = true, count = count, toEnd = true)
        }

        ' ' -> {
            HelixMotions.addNewline(editor, below = true, count = count)
            true
        }

        else -> false
    }
}
