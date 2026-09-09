package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate

internal object HelixWindowKeymap {

    fun handle(ch: Char, editor: Editor): Boolean = when (ch) {
        'v', 'V' -> HelixActionDelegate.executeAction("SplitVertically", editor)

        's', 'S' -> HelixActionDelegate.executeAction("SplitHorizontally", editor)

        'h', 'H' -> HelixActionDelegate.executeAction("PrevSplitter", editor)

        'j', 'J' -> HelixActionDelegate.executeAction("NextSplitter", editor)

        'k', 'K' -> HelixActionDelegate.executeAction("PrevSplitter", editor)

        'l', 'L' -> HelixActionDelegate.executeAction("NextSplitter", editor)

        'w', 'W' -> HelixActionDelegate.executeAction("NextSplitter", editor)

        'q', 'Q', 'c', 'C' -> {
            HelixActionDelegate.executeAction("Unsplit", editor) ||
                HelixActionDelegate.executeAction("CloseContent", editor)
        }

        'o', 'O' -> HelixActionDelegate.executeAction("UnsplitAll", editor)

        else -> false
    }
}
