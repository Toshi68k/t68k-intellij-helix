package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.keymap.HelixBracketKeymap

object HelixMotionHistory {

    sealed class RepeatableMotion {
        data class FindChar(val motion: Char, val targetChar: Char) : RepeatableMotion()
        data class Bracket(val isOpen: Boolean, val targetChar: Char) : RepeatableMotion()
        object MatchBracket : RepeatableMotion()
    }

    var lastMotion: RepeatableMotion? = null

    fun recordFindChar(motion: Char, targetChar: Char) {
        lastMotion = RepeatableMotion.FindChar(motion, targetChar)
    }

    fun recordBracket(isOpen: Boolean, targetChar: Char) {
        lastMotion = RepeatableMotion.Bracket(isOpen, targetChar)
    }

    fun recordMatchBracket() {
        lastMotion = RepeatableMotion.MatchBracket
    }

    fun repeatLastMotion(editor: Editor, count: Int = 1): Boolean {
        val motion = lastMotion ?: return false
        return when (motion) {
            is RepeatableMotion.FindChar -> {
                HelixFindCharMotions.findCharMotion(editor, motion.motion, motion.targetChar, count)
            }

            is RepeatableMotion.Bracket -> {
                if (motion.isOpen) {
                    HelixBracketKeymap.handleOpen(motion.targetChar, editor, count)
                } else {
                    HelixBracketKeymap.handleClose(motion.targetChar, editor, count)
                }
            }

            is RepeatableMotion.MatchBracket -> {
                HelixJumpMotions.matchBrackets(editor, count)
            }
        }
    }
}
