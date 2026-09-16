package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.motion.HelixViewMotions

internal object HelixViewKeymap {

    fun handle(ch: Char, editor: Editor, count: Int? = null): Boolean {
        val lines = count ?: 1
        return when (ch) {
            'c', 'f' -> {
                HelixActionDelegate.executeAction("CollapseRegion", editor)
                true
            }

            'o' -> {
                HelixActionDelegate.executeAction("ExpandRegion", editor)
                true
            }

            'M' -> {
                HelixActionDelegate.executeAction("CollapseAllRegions", editor)
                true
            }

            'R' -> {
                HelixActionDelegate.executeAction("ExpandAllRegions", editor)
                true
            }

            'z' -> {
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

            'd' -> {
                HelixViewMotions.scrollHalfPageDown(editor, lines)
                true
            }

            'u' -> {
                HelixViewMotions.scrollHalfPageUp(editor, lines)
                true
            }

            'F' -> {
                HelixViewMotions.scrollPageUp(editor, lines)
                true
            }

            else -> false
        }
    }
}
