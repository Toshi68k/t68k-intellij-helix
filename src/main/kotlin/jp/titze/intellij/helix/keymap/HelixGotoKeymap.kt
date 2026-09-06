package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.motion.HelixMotions

internal object HelixGotoKeymap {

    fun handle(ch: Char, editor: Editor, count: Int? = null): Boolean = when (ch) {
        'd' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("GotoDeclaration", editor)
        }

        'y' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("GotoTypeDeclaration", editor)
        }

        'r' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("FindUsages", editor)
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

        'g' -> {
            HelixKeyHandler.recordJump(editor)
            HelixMotions.moveFileStart(editor, count)
            true
        }

        else -> false
    }
}
