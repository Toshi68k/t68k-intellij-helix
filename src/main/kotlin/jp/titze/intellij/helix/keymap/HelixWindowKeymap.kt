package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.motion.HelixFileNavigation

internal object HelixWindowKeymap {

    fun handle(ch: Char, editor: Editor): Boolean = when (ch) {
        'v', 'V' -> HelixActionDelegate.executeAction("SplitVertically", editor)

        's', 'S' -> HelixActionDelegate.executeAction("SplitHorizontally", editor)

        'f' -> HelixFileNavigation.gotoFileInSplit(editor, vertical = false)

        'F' -> HelixFileNavigation.gotoFileInSplit(editor, vertical = true)

        't', 'T' -> HelixActionDelegate.executeAction("ChangeSplitOrientation", editor)

        'h' -> HelixActionDelegate.executeAction("PrevSplitter", editor)

        'j' -> HelixActionDelegate.executeAction("NextSplitter", editor)

        'k' -> HelixActionDelegate.executeAction("PrevSplitter", editor)

        'l' -> HelixActionDelegate.executeAction("NextSplitter", editor)

        'w' -> HelixActionDelegate.executeAction("NextSplitter", editor)

        'W' -> HelixActionDelegate.executeAction("PrevSplitter", editor)

        'H', 'J', 'K', 'L' -> HelixActionDelegate.executeAction("ChangeSplitOrientation", editor)

        'q', 'Q', 'c', 'C' -> {
            HelixActionDelegate.executeAction("Unsplit", editor) ||
                HelixActionDelegate.executeAction("CloseContent", editor)
        }

        'o', 'O' -> HelixActionDelegate.executeAction("UnsplitAll", editor)

        else -> false
    }
}
