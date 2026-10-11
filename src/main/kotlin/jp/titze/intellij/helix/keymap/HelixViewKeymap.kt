package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.motion.HelixViewMotions

internal object HelixViewKeymap {

    fun handle(ch: Char, editor: Editor, count: Int? = null): Boolean {
        val lines = count ?: 1
        return when (ch) {
            'z', 'c' -> {
                HelixViewMotions.alignViewCenter(editor)
                true
            }

            't' -> {
                HelixViewMotions.alignViewTop(editor)
                true
            }

            'b' -> {
                HelixViewMotions.alignViewBottom(editor)
                true
            }

            'm' -> {
                HelixViewMotions.alignViewMiddle(editor)
                true
            }

            'j' -> {
                HelixViewMotions.scrollViewDown(editor, lines)
                true
            }

            'k' -> {
                HelixViewMotions.scrollViewUp(editor, lines)
                true
            }

            'd', '\u0004' -> {
                HelixViewMotions.scrollHalfPageDown(editor, lines)
                true
            }

            'u', '\u0015' -> {
                HelixViewMotions.scrollHalfPageUp(editor, lines)
                true
            }

            'f', '\u0006' -> {
                HelixViewMotions.scrollPageDown(editor, lines)
                true
            }

            'F', '\u0002' -> {
                HelixViewMotions.scrollPageUp(editor, lines)
                true
            }

            else -> false
        }
    }
}
