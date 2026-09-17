package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.motion.HelixFileNavigation
import jp.titze.intellij.helix.motion.HelixJumpToWord
import jp.titze.intellij.helix.motion.HelixMotions

internal object HelixGotoKeymap {

    fun handle(ch: Char, editor: Editor, count: Int? = null): Boolean = when (ch) {
        'd' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("GotoDeclaration", editor)
        }

        'D' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("GotoDeclarationOnly", editor) ||
                HelixActionDelegate.executeAction("GotoDeclaration", editor)
        }

        'i' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("GotoImplementation", editor)
        }

        'y' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("GotoTypeDeclaration", editor)
        }

        'r' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("FindUsages", editor)
        }

        't' -> {
            jp.titze.intellij.helix.motion.HelixViewMotions.gotoWindowTop(editor)
            true
        }

        'c' -> {
            jp.titze.intellij.helix.motion.HelixViewMotions.gotoWindowCenter(editor)
            true
        }

        'b' -> {
            jp.titze.intellij.helix.motion.HelixViewMotions.gotoWindowBottom(editor)
            true
        }

        'f' -> {
            HelixKeyHandler.recordJump(editor)
            HelixFileNavigation.gotoFileAtCaret(editor)
        }

        '|' -> {
            HelixMotions.gotoColumn(editor, count)
            true
        }

        'n' -> {
            HelixKeyHandler.recordJump(editor)
            repeat(count ?: 1) { HelixActionDelegate.executeAction("NextTab", editor) }.let { true }
        }

        'p' -> {
            HelixKeyHandler.recordJump(editor)
            repeat(count ?: 1) { HelixActionDelegate.executeAction("PreviousTab", editor) }.let { true }
        }

        '.' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("JumpToLastChange", editor)
        }

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
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveFileEnd(editor)
            true
        }

        'w' -> HelixJumpToWord.start(editor)

        'g' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveFileStart(editor, count)
            true
        }

        'a' -> {
            HelixKeyHandler.recordJump(editor)
            HelixFileNavigation.gotoLastAccessedFile(editor)
        }

        'm' -> {
            HelixKeyHandler.recordJump(editor)
            HelixFileNavigation.gotoLastModifiedFile(editor)
        }

        'j' -> {
            HelixMotions.moveVisualDown(editor, count ?: 1)
            true
        }

        'k' -> {
            HelixMotions.moveVisualUp(editor, count ?: 1)
            true
        }

        else -> false
    }
}
