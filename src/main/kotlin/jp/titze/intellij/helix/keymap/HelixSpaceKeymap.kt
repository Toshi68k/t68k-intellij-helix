package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.ui.HelixDirectoryFilePickerPopup
import jp.titze.intellij.helix.ui.HelixJumplistPopup

internal object HelixSpaceKeymap {

    var lastPickerChar: Char? = null

    fun handle(ch: Char, editor: Editor): Boolean {
        if (ch in listOf('f', 'F', 'b', '/', 'j', 's', 'S', 'd', 'D', 'g', '?')) {
            lastPickerChar = ch
        }

        return when (ch) {
            'f' -> {
                HelixKeyHandler.recordJump(editor)
                HelixActionDelegate.executeAction(
                    "GotoFile",
                    editor,
                ) || HelixActionDelegate.executeAction("SearchEverywhere", editor)
            }

            'F' -> {
                HelixKeyHandler.recordJump(editor)
                HelixDirectoryFilePickerPopup.show(editor)
                true
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

            'e' -> HelixActionDelegate.executeAction("ActivateProjectToolWindow", editor)

            '.' -> HelixActionDelegate.executeAction("SelectInProjectView", editor)

            'g' -> {
                HelixActionDelegate.executeAction("ActivateVersionControlToolWindow", editor) ||
                    HelixActionDelegate.executeAction("Vcs.Show.Local.Changes", editor)
            }

            'c' -> HelixActionDelegate.executeAction("CommentByLineComment", editor)

            'C' -> HelixActionDelegate.executeAction("CommentByBlockComment", editor)

            'h' -> {
                HelixActionDelegate.executeAction("HighlightUsagesInFile", editor) ||
                    HelixActionDelegate.executeAction("FindUsages", editor)
            }

            '\'' -> {
                val picker = lastPickerChar ?: 'b'
                handle(picker, editor)
            }

            'a' -> HelixActionDelegate.executeAction("ShowIntentionActions", editor)

            'r' -> HelixActionDelegate.executeAction("RenameElement", editor)

            'w' -> HelixActionDelegate.executeAction("SaveAll", editor)

            'y' -> {
                HelixActions.yankSelection(editor, register = '+')
                true
            }

            'p' -> {
                HelixActions.paste(editor, after = true, register = '+')
                true
            }

            'P' -> {
                HelixActions.paste(editor, after = false, register = '+')
                true
            }

            'R' -> {
                HelixActions.replaceWithClipboard(editor, register = '+')
                true
            }

            'k' -> HelixActionDelegate.executeAction("QuickJavaDoc", editor)

            '?' -> HelixActionDelegate.executeAction("GotoAction", editor)

            else -> false
        }
    }
}
