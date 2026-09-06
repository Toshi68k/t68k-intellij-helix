package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor

object HelixSurroundActions {

    fun resolvePair(ch: Char): Pair<String, String> = when (ch) {
        '(', ')', 'b', 'p' -> "(" to ")"
        '[', ']', 'r' -> "[" to "]"
        '{', '}', 'B', 'c' -> "{" to "}"
        '<', '>', 'a' -> "<" to ">"
        '"' -> "\"" to "\""
        '\'' -> "'" to "'"
        '`' -> "`" to "`"
        else -> ch.toString() to ch.toString()
    }

    fun findEnclosingPair(document: Document, caret: Caret, delim: Char): Pair<Int, Int>? {
        if (delim == 'q') {
            val candidates = listOf('"', '\'', '`')
                .mapNotNull { findEnclosingPair(document, caret, it) }
            return candidates.minByOrNull { it.second - it.first }
        }
        if (delim == 'm') {
            val candidates = listOf('(', '[', '{', '<', '"', '\'', '`')
                .mapNotNull { findEnclosingPair(document, caret, it) }
            return candidates.minByOrNull { it.second - it.first }
        }

        val (open, close) = resolvePair(delim)
        return if (open != close) {
            findEnclosingBrackets(document, caret, open[0], close[0])
        } else {
            findEnclosingSymmetric(document, caret, open[0])
        }
    }

    private fun findEnclosingBrackets(document: Document, caret: Caret, open: Char, close: Char): Pair<Int, Int>? {
        val text = document.charsSequence
        val len = text.length
        if (len == 0) return null

        val hasActiveSelection = caret.hasSelection() && caret.offset in caret.selectionStart..caret.selectionEnd
        val selStart = if (hasActiveSelection) caret.selectionStart else caret.offset
        val selEnd = if (hasActiveSelection) caret.selectionEnd else (caret.offset + 1).coerceAtMost(len)

        val direct = findDirectBracketPair(text, caret, selStart, selEnd, hasActiveSelection, open, close)
        if (direct != null) return direct

        val startScan = (selStart - 1).coerceAtMost(len - 1)
        val requiredEnd = selEnd - 1
        return findEnclosingBracketsByScanning(text, startScan, requiredEnd, open, close)
    }

    private fun findDirectBracketPair(
        text: CharSequence,
        caret: Caret,
        selStart: Int,
        selEnd: Int,
        hasActiveSelection: Boolean,
        open: Char,
        close: Char,
    ): Pair<Int, Int>? {
        if (hasActiveSelection && selStart < selEnd && isDirectlySurrounded(text, selStart, selEnd, open, close)) {
            return Pair(selStart, selEnd - 1)
        }

        if (!caret.hasSelection() && caret.offset < text.length) {
            val cur = text[caret.offset]
            if (cur == close) {
                val matchingOpen = findMatchingOpen(text, caret.offset, open, close)
                if (matchingOpen != null) return Pair(matchingOpen, caret.offset)
            } else if (cur == open) {
                val matchingClose = findMatchingClose(text, caret.offset, open, close)
                if (matchingClose != null) return Pair(caret.offset, matchingClose)
            }
        }
        return null
    }

    private fun isDirectlySurrounded(
        text: CharSequence,
        selStart: Int,
        selEnd: Int,
        open: Char,
        close: Char,
    ): Boolean = text[selStart] == open &&
        text[selEnd - 1] == close &&
        isBalanced(text, selStart, selEnd - 1, open, close)

    private fun findEnclosingBracketsByScanning(
        text: CharSequence,
        startScan: Int,
        requiredEnd: Int,
        open: Char,
        close: Char,
    ): Pair<Int, Int>? {
        var depth = 0
        for (i in startScan downTo 0) {
            when (text[i]) {
                close -> depth++

                open -> {
                    if (depth > 0) {
                        depth--
                    } else {
                        val matchingClose = findMatchingClose(text, i, open, close)
                        if (matchingClose != null && matchingClose >= requiredEnd) {
                            return Pair(i, matchingClose)
                        }
                    }
                }
            }
        }
        return null
    }

    fun findMatchingClose(text: CharSequence, openIndex: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (i in (openIndex + 1) until text.length) {
            val c = text[i]
            if (c == open) {
                depth++
            } else if (c == close) {
                if (depth == 0) {
                    return i
                }
                depth--
            }
        }
        return null
    }

    fun findMatchingOpen(text: CharSequence, closeIndex: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (i in (closeIndex - 1) downTo 0) {
            val c = text[i]
            if (c == close) {
                depth++
            } else if (c == open) {
                if (depth == 0) {
                    return i
                }
                depth--
            }
        }
        return null
    }

    private fun isBalanced(text: CharSequence, start: Int, end: Int, open: Char, close: Char): Boolean {
        var depth = 0
        for (i in start..end) {
            val c = text[i]
            if (c == open) {
                depth++
            } else if (c == close) {
                depth--
                if (depth < 0) return false
                if (depth == 0 && i < end) return false
            }
        }
        return depth == 0
    }

    private fun findEnclosingSymmetric(document: Document, caret: Caret, delim: Char): Pair<Int, Int>? {
        val text = document.charsSequence
        val len = text.length
        if (len == 0) return null

        val hasActiveSelection = caret.hasSelection() && caret.offset in caret.selectionStart..caret.selectionEnd
        val selStart = if (hasActiveSelection) caret.selectionStart else caret.offset
        val selEnd = if (hasActiveSelection) caret.selectionEnd else (caret.offset + 1).coerceAtMost(len)

        // Check if selection itself starts and ends with delim
        if (hasActiveSelection && selStart < selEnd - 1) {
            if (text[selStart] == delim && text[selEnd - 1] == delim) {
                return Pair(selStart, selEnd - 1)
            }
        }

        // Search on the current line first
        val line = document.getLineNumber(caret.offset.coerceAtMost(len - 1).coerceAtLeast(0))
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)

        val linePair = findSymmetricPairInRange(text, lineStart, lineEnd, selStart, selEnd, delim)
        if (linePair != null) return linePair

        // Search in surrounding block
        val searchStart = document.getLineStartOffset((line - 100).coerceAtLeast(0))
        val searchEnd = document.getLineEndOffset((line + 100).coerceAtMost(document.lineCount - 1))
        return findSymmetricPairInRange(text, searchStart, searchEnd, selStart, selEnd, delim)
    }

    private fun findSymmetricPairInRange(
        text: CharSequence,
        rangeStart: Int,
        rangeEnd: Int,
        selStart: Int,
        selEnd: Int,
        delim: Char,
    ): Pair<Int, Int>? {
        val unescaped = mutableListOf<Int>()
        var i = rangeStart
        while (i < rangeEnd) {
            if (text[i] == delim) {
                var backslashes = 0
                var b = i - 1
                while (b >= rangeStart && text[b] == '\\') {
                    backslashes++
                    b--
                }
                if (backslashes % 2 == 0) {
                    unescaped.add(i)
                }
            }
            i++
        }

        if (unescaped.size < 2) return null

        var p = 0
        var bestPair: Pair<Int, Int>? = null
        var minSpan = Int.MAX_VALUE

        while (p + 1 < unescaped.size) {
            val o = unescaped[p]
            val c = unescaped[p + 1]

            if (o <= selStart && c >= selEnd - 1) {
                val span = c - o
                if (span < minSpan) {
                    minSpan = span
                    bestPair = Pair(o, c)
                }
            }
            p += 2
        }

        if (bestPair != null) return bestPair

        for (idx in unescaped) {
            if (idx == selStart) {
                val pos = unescaped.indexOf(idx)
                if (pos % 2 == 0 && pos + 1 < unescaped.size) {
                    return Pair(unescaped[pos], unescaped[pos + 1])
                } else if (pos % 2 == 1 && pos - 1 >= 0) {
                    return Pair(unescaped[pos - 1], unescaped[pos])
                }
            }
        }

        return null
    }

    fun surroundAdd(editor: Editor, ch: Char): Boolean {
        val project = editor.project
        val (openStr, closeStr) = resolvePair(ch)

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.selectionStart }
            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    editor.document.insertString(end, closeStr)
                    editor.document.insertString(start, openStr)
                    val newStart = start
                    val newEnd = end + openStr.length + closeStr.length
                    caret.setSelection(newStart, newEnd)
                    caret.moveToOffset(newEnd)
                } else {
                    val offset = caret.offset
                    val docLen = editor.document.textLength
                    if (offset < docLen) {
                        editor.document.insertString(offset + 1, closeStr)
                        editor.document.insertString(offset, openStr)
                        val newStart = offset
                        val newEnd = offset + 1 + openStr.length + closeStr.length
                        caret.setSelection(newStart, newEnd)
                        caret.moveToOffset(newEnd)
                    } else {
                        editor.document.insertString(offset, openStr + closeStr)
                        caret.moveToOffset(offset + openStr.length)
                    }
                }
            }
        }
        return true
    }

    fun surroundDelete(editor: Editor, ch: Char): Boolean {
        val project = editor.project
        val doc = editor.document

        val carets = editor.caretModel.allCarets
        val pairs = carets.mapNotNull { findEnclosingPair(doc, it, ch) }.distinct()
        if (pairs.isEmpty()) return false

        val sortedPairs = pairs.sortedByDescending { it.second }

        WriteCommandAction.runWriteCommandAction(project) {
            for ((openPos, closePos) in sortedPairs) {
                doc.deleteString(closePos, closePos + 1)
                doc.deleteString(openPos, openPos + 1)
            }
        }
        return true
    }

    fun surroundReplace(editor: Editor, fromChar: Char, toChar: Char): Boolean {
        val project = editor.project
        val doc = editor.document
        val (newOpen, newClose) = resolvePair(toChar)

        val carets = editor.caretModel.allCarets
        val pairs = carets.mapNotNull { findEnclosingPair(doc, it, fromChar) }.distinct()
        if (pairs.isEmpty()) return false

        val sortedPairs = pairs.sortedByDescending { it.second }

        WriteCommandAction.runWriteCommandAction(project) {
            for ((openPos, closePos) in sortedPairs) {
                doc.replaceString(closePos, closePos + 1, newClose)
                doc.replaceString(openPos, openPos + 1, newOpen)
            }
        }
        return true
    }
}
