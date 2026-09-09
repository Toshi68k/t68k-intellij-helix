package jp.titze.intellij.helix.motion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

internal object HelixWordMotions {

    private enum class CharType {
        WORD,
        PUNCTUATION,
        WHITESPACE,
    }

    private fun getCharType(c: Char): CharType = when {
        c.isWhitespace() -> CharType.WHITESPACE
        c.isLetterOrDigit() || c == '_' -> CharType.WORD
        else -> CharType.PUNCTUATION
    }

    private fun isLineEnding(c: Char): Boolean = HelixMotionUtils.isLineEnding(c)
    private fun isHorizontalWhitespace(c: Char): Boolean = HelixMotionUtils.isHorizontalWhitespace(c)

    fun moveNextWordStart(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            var anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
                if (offset < textLen) {
                    if (isLineEnding(text[offset])) {
                        while (offset < textLen && isLineEnding(text[offset])) {
                            offset++
                        }
                        if (!isSelect) {
                            anchor = offset
                        }
                        if (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                                offset++
                            }
                        } else if (offset < textLen && !isLineEnding(text[offset])) {
                            val initialType = getCharType(text[offset])
                            while (offset < textLen && getCharType(text[offset]) == initialType) {
                                offset++
                            }
                            while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                                offset++
                            }
                        }
                    } else if (isHorizontalWhitespace(text[offset])) {
                        while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            offset++
                        }
                    } else {
                        val initialType = getCharType(text[offset])
                        while (offset < textLen && getCharType(text[offset]) == initialType) {
                            offset++
                        }
                        while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            offset++
                        }
                    }
                }
            }

            offset = offset.coerceIn(0, textLen)
            HelixMotionUtils.applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    fun movePrevWordStart(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
                if (offset > 0) {
                    offset--
                    while (offset > 0 && text[offset].isWhitespace()) {
                        offset--
                    }
                    val targetType = getCharType(text[offset])
                    while (offset > 0 && getCharType(text[offset - 1]) == targetType) {
                        offset--
                    }
                }
            }

            offset = offset.coerceIn(0, textLen)
            HelixMotionUtils.applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    fun moveWordEnd(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            var anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
                if (offset < textLen) {
                    val wasWhitespace = text[offset].isWhitespace()
                    offset++
                    while (offset < textLen && text[offset].isWhitespace()) {
                        offset++
                    }
                    if (!isSelect && wasWhitespace) {
                        anchor = offset
                    }
                    if (offset < textLen) {
                        val targetType = getCharType(text[offset])
                        while (offset + 1 < textLen && getCharType(text[offset + 1]) == targetType) {
                            offset++
                        }
                        if (offset < textLen) offset++
                    }
                }
            }

            offset = offset.coerceIn(0, textLen)
            HelixMotionUtils.applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    fun moveNextBigWordStart(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            var anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
                if (offset < textLen) {
                    if (isLineEnding(text[offset])) {
                        while (offset < textLen && isLineEnding(text[offset])) {
                            offset++
                        }
                        if (!isSelect) {
                            anchor = offset
                        }
                        if (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                                offset++
                            }
                        } else if (offset < textLen && !isLineEnding(text[offset])) {
                            while (offset < textLen && !text[offset].isWhitespace()) {
                                offset++
                            }
                            while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                                offset++
                            }
                        }
                    } else if (isHorizontalWhitespace(text[offset])) {
                        while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            offset++
                        }
                    } else {
                        while (offset < textLen && !text[offset].isWhitespace()) {
                            offset++
                        }
                        while (offset < textLen && isHorizontalWhitespace(text[offset])) {
                            offset++
                        }
                    }
                }
            }

            offset = offset.coerceIn(0, textLen)
            HelixMotionUtils.applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    fun movePrevBigWordStart(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
                if (offset > 0) {
                    offset--
                    while (offset > 0 && text[offset].isWhitespace()) {
                        offset--
                    }
                    while (offset > 0 && !text[offset - 1].isWhitespace()) {
                        offset--
                    }
                }
            }

            offset = offset.coerceIn(0, textLen)
            HelixMotionUtils.applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    fun moveBigWordEnd(editor: Editor, count: Int = 1) {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val startOffset = caret.offset
            var anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else startOffset
            var offset = startOffset

            repeat(count) {
                if (offset < textLen) {
                    val wasWhitespace = text[offset].isWhitespace()
                    offset++
                    while (offset < textLen && text[offset].isWhitespace()) {
                        offset++
                    }
                    if (!isSelect && wasWhitespace) {
                        anchor = offset
                    }
                    if (offset < textLen) {
                        while (offset + 1 < textLen && !text[offset + 1].isWhitespace()) {
                            offset++
                        }
                        if (offset < textLen) offset++
                    }
                }
            }

            offset = offset.coerceIn(0, textLen)
            HelixMotionUtils.applySelectingMotion(caret, anchor, offset)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }
}
