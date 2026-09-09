package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.math.BigInteger

object HelixNumberActions {

    private val NUMBER_REGEX = Regex(
        """(?<![a-zA-Z0-9])0[xX][0-9a-fA-F][0-9a-fA-F_]*|""" +
            """(?<![a-zA-Z0-9])0[bB][01][01_]*|""" +
            """(?<![a-zA-Z0-9])0[oO][0-7][0-7_]*|""" +
            """(?<![\w\)\]])-[0-9][0-9_]*|""" +
            """[0-9][0-9_]*""",
    )

    data class NumberSpan(val start: Int, val end: Int, val text: String)

    fun increment(editor: Editor, count: Int = 1) {
        modifyNumber(editor, count.coerceAtLeast(1))
    }

    fun decrement(editor: Editor, count: Int = 1) {
        modifyNumber(editor, -count.coerceAtLeast(1))
    }

    private fun modifyNumber(editor: Editor, amount: Int) {
        val project = editor.project ?: return
        val doc = editor.document
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }

            for (caret in carets) {
                if (caret.hasSelection()) {
                    modifySelection(caret, doc, amount)
                } else {
                    modifyUnselected(caret, doc, amount, isSelect)
                }
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun modifySelection(caret: Caret, doc: Document, amount: Int) {
        val selStart = caret.selectionStart
        val selEnd = caret.selectionEnd
        val selectedText = doc.getText(TextRange(selStart, selEnd))
        if (selectedText.isEmpty()) return

        val span = findTargetNumberInSelection(selectedText, caret.offset - selStart) ?: return
        val newText = computeModifiedNumber(span.text, amount) ?: return

        val replaceStart = selStart + span.start
        val replaceEnd = selStart + span.end
        val isCursorAtStart = caret.offset == selStart

        doc.replaceString(replaceStart, replaceEnd, newText)

        val delta = newText.length - span.text.length
        val newSelEnd = selEnd + delta
        caret.setSelection(selStart, newSelEnd)
        if (isCursorAtStart) {
            caret.moveToOffset(selStart)
        } else {
            caret.moveToOffset(newSelEnd)
        }
    }

    private fun modifyUnselected(caret: Caret, doc: Document, amount: Int, isSelect: Boolean) {
        val offset = caret.offset
        if (offset >= doc.textLength && doc.textLength > 0) return
        val lineNum = doc.getLineNumber(offset.coerceAtMost((doc.textLength - 1).coerceAtLeast(0)))
        val lineStart = doc.getLineStartOffset(lineNum)
        val lineEnd = doc.getLineEndOffset(lineNum)
        if (lineStart >= lineEnd) return

        val lineText = doc.getText(TextRange(lineStart, lineEnd))
        val offsetInLine = (offset - lineStart).coerceIn(0, lineText.length)

        val span = findTargetNumberInLine(lineText, offsetInLine) ?: return
        val newText = computeModifiedNumber(span.text, amount) ?: return

        val replaceStart = lineStart + span.start
        val replaceEnd = lineStart + span.end
        val relOffsetInSpan = (offsetInLine - span.start).coerceIn(0, span.text.length.coerceAtLeast(1) - 1)

        doc.replaceString(replaceStart, replaceEnd, newText)

        if (isSelect) {
            caret.setSelection(replaceStart, replaceStart + newText.length)
            caret.moveToOffset(replaceStart + newText.length)
        } else {
            caret.removeSelection()
            val targetOffset = if (offsetInLine < span.start) {
                replaceStart
            } else {
                val boundedRelOffset = relOffsetInSpan.coerceAtMost(newText.length - 1)
                replaceStart + boundedRelOffset
            }
            caret.moveToOffset(targetOffset)
        }
    }

    fun findTargetNumberInSelection(selection: String, cursorRelOffset: Int): NumberSpan? {
        val trimmed = selection.trim()
        val matchEntire = NUMBER_REGEX.matchEntire(trimmed)
        if (matchEntire != null) {
            val start = selection.indexOf(trimmed)
            return NumberSpan(start, start + trimmed.length, trimmed)
        }

        val matches = NUMBER_REGEX.findAll(selection).map {
            NumberSpan(it.range.first, it.range.last + 1, it.value)
        }.toList()

        if (matches.isEmpty()) return null

        val matchUnderCursor = matches.firstOrNull { cursorRelOffset in it.start until it.end }
        return matchUnderCursor ?: matches.first()
    }

    fun findTargetNumberInLine(lineText: String, cursorCol: Int): NumberSpan? {
        val matches = NUMBER_REGEX.findAll(lineText).map {
            NumberSpan(it.range.first, it.range.last + 1, it.value)
        }.toList()

        if (matches.isEmpty()) return null

        val underCursor = matches.firstOrNull { cursorCol in it.start until it.end }
        if (underCursor != null) return underCursor

        return matches.firstOrNull { it.start >= cursorCol }
    }

    fun computeModifiedNumber(rawText: String, amount: Int): String? {
        val (radix, prefix, body) = when {
            rawText.startsWith("0x", ignoreCase = true) -> Triple(16, rawText.substring(0, 2), rawText.substring(2))
            rawText.startsWith("0b", ignoreCase = true) -> Triple(2, rawText.substring(0, 2), rawText.substring(2))
            rawText.startsWith("0o", ignoreCase = true) -> Triple(8, rawText.substring(0, 2), rawText.substring(2))
            else -> Triple(10, "", rawText)
        }

        val cleanBody = body.filter { it != '_' }
        if (cleanBody.isEmpty()) return null

        return if (radix == 10) {
            formatDecimal(cleanBody, body, amount)
        } else {
            formatNonDecimal(cleanBody, body, radix, prefix, amount)
        }
    }

    private fun formatDecimal(cleanBody: String, originalBody: String, amount: Int): String? {
        val value = try {
            BigInteger(cleanBody)
        } catch (_: NumberFormatException) {
            return null
        }

        val newValue = value + BigInteger.valueOf(amount.toLong())
        val hasLeadingZero = (cleanBody.startsWith("0") && cleanBody.length > 1) ||
            (cleanBody.startsWith("-0") && cleanBody.length > 2)

        val digitsPartLen = if (cleanBody.startsWith("-")) cleanBody.length - 1 else cleanBody.length
        val isNewNegative = newValue < BigInteger.ZERO
        val absString = newValue.abs().toString()

        val paddedAbs = if (hasLeadingZero) absString.padStart(digitsPartLen, '0') else absString
        val formattedNumber = if (isNewNegative) "-$paddedAbs" else paddedAbs

        return if (originalBody.contains('_')) {
            val sign = if (formattedNumber.startsWith("-")) "-" else ""
            val rawDigits = formattedNumber.removePrefix("-")
            val withSeparators = insertSeparators(rawDigits, originalBody.removePrefix("-"))
            sign + withSeparators
        } else {
            formattedNumber
        }
    }

    private fun formatNonDecimal(
        cleanBody: String,
        originalBody: String,
        radix: Int,
        prefix: String,
        amount: Int,
    ): String? {
        val value = try {
            BigInteger(cleanBody, radix)
        } catch (_: NumberFormatException) {
            return null
        }

        val newValue = (value + BigInteger.valueOf(amount.toLong())).coerceAtLeast(BigInteger.ZERO)
        val hasUppercase = originalBody.any { it in 'A'..'F' }
        val rawDigits = if (radix == 16 && hasUppercase) {
            newValue.toString(radix).uppercase()
        } else {
            newValue.toString(radix).lowercase()
        }

        val hasLeadingZero = cleanBody.startsWith("0") && cleanBody.length > 1
        val padded = if (hasLeadingZero) rawDigits.padStart(cleanBody.length, '0') else rawDigits

        val withSeparators = if (originalBody.contains('_')) {
            insertSeparators(padded, originalBody)
        } else {
            padded
        }

        return prefix + withSeparators
    }

    private fun insertSeparators(text: String, originalBody: String): String {
        val rtlIndexes = originalBody.reversed().mapIndexedNotNull { index, c ->
            if (c == '_') index else null
        }
        var result = text
        for (rtlIndex in rtlIndexes) {
            val insertPos = result.length - rtlIndex
            if (insertPos in 1 until result.length) {
                result = result.substring(0, insertPos) + '_' + result.substring(insertPos)
            }
        }
        return result
    }
}
