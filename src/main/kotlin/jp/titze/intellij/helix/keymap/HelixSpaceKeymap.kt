package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.ui.HelixJumplistPopup

internal object HelixSpaceKeymap {

    fun handle(ch: Char, editor: Editor): Boolean = when (ch) {
        'f' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction(
                "GotoFile",
                editor,
            ) || HelixActionDelegate.executeAction("SearchEverywhere", editor)
        }

        'b' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("RecentFiles", editor)
        }

        '/' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("FindInPath", editor)
        }

        'j' -> {
            HelixJumplistPopup.show(editor)
            true
        }

        's' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("FileStructurePopup", editor)
        }

        'S' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("GotoSymbol", editor)
        }

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
