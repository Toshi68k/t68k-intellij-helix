package jp.titze.intellij.helix.action

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

object HelixTextObjectActions {

    private enum class CharKind {
        WORD, PUNCTUATION, WHITESPACE
    }

    private fun getCharKind(c: Char): CharKind = when {
        c.isWhitespace() -> CharKind.WHITESPACE
        c.isLetterOrDigit() || c == '_' -> CharKind.WORD
        else -> CharKind.PUNCTUATION
    }

    /**
     * Main entry point: selects the textobject specified by [objectChar] for all carets.
     * [inside] = true for `mi<char>`, false for `ma<char>`.
     */
    fun selectTextObject(editor: Editor, inside: Boolean, objectChar: Char): Boolean {
        val document = editor.document
        val textLength = document.textLength
        if (textLength == 0) return false

        var anySelected = false
        editor.caretModel.runForEachCaret { caret ->
            val range = findTextObjectRange(editor, caret, inside, objectChar)
            if (range != null) {
                val (start, end) = range
                val clampedStart = start.coerceIn(0, textLength)
                val clampedEnd = end.coerceIn(clampedStart, textLength)
                caret.setSelection(clampedStart, clampedEnd)
                caret.moveToOffset(clampedEnd)
                anySelected = true
            }
        }
        return anySelected
    }

    fun findTextObjectRange(
        editor: Editor,
        caret: Caret,
        inside: Boolean,
        objectChar: Char
    ): Pair<Int, Int>? {
        val doc = editor.document
        return when (objectChar) {
            'w' -> findWordRange(doc, caret, inside)
            'W' -> findBigWordRange(doc, caret, inside)
            'p' -> findParagraphRange(doc, caret, inside)
            'm' -> findClosestPairRange(doc, caret, inside)
            '(', ')', 'b' -> findDelimitedPairRange(doc, caret, inside, '(')
            '[', ']', 'r' -> findDelimitedPairRange(doc, caret, inside, '[')
            '{', '}', 'B' -> findDelimitedPairRange(doc, caret, inside, '{')
            '<', '>' -> findDelimitedPairRange(doc, caret, inside, '<')
            '"', '\'', '`' -> findDelimitedPairRange(doc, caret, inside, objectChar)
            'c' -> findCommentRange(editor, caret, inside)
            'f' -> findFunctionRange(editor, caret, inside)
            't' -> findTypeRange(editor, caret, inside)
            'a' -> findArgumentRange(editor, caret, inside)
            else -> findDelimitedPairRange(doc, caret, inside, objectChar)
        }
    }

    /**
     * w: Word textobject
     * miw: selects word/token bounds
     * maw: selects word/token plus trailing whitespace (or leading if at line end)
     */
    private fun findWordRange(doc: Document, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val text = doc.charsSequence
        val len = text.length
        if (len == 0) return null

        var offset = caret.offset
        if (offset >= len && len > 0) offset = len - 1

        val currentLine = doc.getLineNumber(offset)
        val lineStart = doc.getLineStartOffset(currentLine)
        val lineEnd = doc.getLineEndOffset(currentLine)

        if (lineStart == lineEnd) return null

        // If offset is on lineEnd, clamp to lineEnd - 1
        if (offset >= lineEnd && offset > lineStart) {
            offset = lineEnd - 1
        }

        val targetKind = getCharKind(text[offset])

        // Find token start
        var start = offset
        while (start > lineStart && getCharKind(text[start - 1]) == targetKind) {
            start--
        }

        // Find token end
        var end = offset
        while (end < lineEnd && getCharKind(text[end]) == targetKind) {
            end++
        }

        if (inside) {
            return Pair(start, end)
        }

        // maw: include whitespace around the word
        if (targetKind == CharKind.WHITESPACE) {
            // Already whitespace, extend to include adjacent word if any
            var trailing = end
            if (trailing < lineEnd) {
                val nextKind = getCharKind(text[trailing])
                while (trailing < lineEnd && getCharKind(text[trailing]) == nextKind) {
                    trailing++
                }
                return Pair(start, trailing)
            }
            return Pair(start, end)
        }

        // Check trailing whitespace on same line
        var trailingEnd = end
        while (trailingEnd < lineEnd && text[trailingEnd].isWhitespace()) {
            trailingEnd++
        }

        if (trailingEnd > end) {
            return Pair(start, trailingEnd)
        }

        // If no trailing whitespace, include preceding whitespace on same line
        var leadingStart = start
        while (leadingStart > lineStart && text[leadingStart - 1].isWhitespace()) {
            leadingStart--
        }

        return Pair(leadingStart, end)
    }

    /**
     * W: Non-whitespace WORD textobject
     */
    private fun findBigWordRange(doc: Document, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val text = doc.charsSequence
        val len = text.length
        if (len == 0) return null

        var offset = caret.offset
        if (offset >= len && len > 0) offset = len - 1

        val currentLine = doc.getLineNumber(offset)
        val lineStart = doc.getLineStartOffset(currentLine)
        val lineEnd = doc.getLineEndOffset(currentLine)

        if (lineStart == lineEnd) return null

        if (offset >= lineEnd && offset > lineStart) {
            offset = lineEnd - 1
        }

        val isWhitespace = text[offset].isWhitespace()

        // Find token bounds
        var start = offset
        while (start > lineStart && (text[start - 1].isWhitespace() == isWhitespace)) {
            start--
        }

        var end = offset
        while (end < lineEnd && (text[end].isWhitespace() == isWhitespace)) {
            end++
        }

        if (inside) {
            return Pair(start, end)
        }

        if (isWhitespace) {
            return Pair(start, end)
        }

        // maW: include trailing whitespace
        var trailingEnd = end
        while (trailingEnd < lineEnd && text[trailingEnd].isWhitespace()) {
            trailingEnd++
        }

        if (trailingEnd > end) {
            return Pair(start, trailingEnd)
        }

        // Or preceding whitespace
        var leadingStart = start
        while (leadingStart > lineStart && text[leadingStart - 1].isWhitespace()) {
            leadingStart--
        }

        return Pair(leadingStart, end)
    }

    /**
     * p: Paragraph textobject
     * Paragraphs are delimited by blank lines (empty or whitespace only)
     */
    private fun findParagraphRange(doc: Document, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val lineCount = doc.lineCount
        if (lineCount == 0) return null

        val curLine = doc.getLineNumber(caret.offset.coerceIn(0, doc.textLength))

        fun isBlankLine(line: Int): Boolean {
            val start = doc.getLineStartOffset(line)
            val end = doc.getLineEndOffset(line)
            val seq = doc.charsSequence.subSequence(start, end)
            return seq.all { it.isWhitespace() }
        }

        // If caret is on a blank line, find contiguous blank lines
        if (isBlankLine(curLine)) {
            var firstBlank = curLine
            while (firstBlank > 0 && isBlankLine(firstBlank - 1)) {
                firstBlank--
            }
            var lastBlank = curLine
            while (lastBlank < lineCount - 1 && isBlankLine(lastBlank + 1)) {
                lastBlank++
            }
            val start = doc.getLineStartOffset(firstBlank)
            val end = (doc.getLineEndOffset(lastBlank) + 1).coerceAtMost(doc.textLength)
            return Pair(start, end)
        }

        // On non-blank line: find paragraph boundaries
        var pStartLine = curLine
        while (pStartLine > 0 && !isBlankLine(pStartLine - 1)) {
            pStartLine--
        }

        var pEndLine = curLine
        while (pEndLine < lineCount - 1 && !isBlankLine(pEndLine + 1)) {
            pEndLine++
        }

        val pStartOffset = doc.getLineStartOffset(pStartLine)
        val pEndOffset = doc.getLineEndOffset(pEndLine)

        if (inside) {
            // inside paragraph: from start of paragraph lines to end of paragraph lines
            return Pair(pStartOffset, pEndOffset)
        }

        // around paragraph: include trailing blank line if any, else preceding blank line
        if (pEndLine < lineCount - 1 && isBlankLine(pEndLine + 1)) {
            var trailingBlank = pEndLine + 1
            while (trailingBlank < lineCount - 1 && isBlankLine(trailingBlank + 1)) {
                trailingBlank++
            }
            val end = (doc.getLineEndOffset(trailingBlank) + 1).coerceAtMost(doc.textLength)
            return Pair(pStartOffset, end)
        } else if (pStartLine > 0 && isBlankLine(pStartLine - 1)) {
            var leadingBlank = pStartLine - 1
            while (leadingBlank > 0 && isBlankLine(leadingBlank - 1)) {
                leadingBlank--
            }
            val start = doc.getLineStartOffset(leadingBlank)
            val end = (pEndOffset + 1).coerceAtMost(doc.textLength)
            return Pair(start, end)
        }

        val end = (pEndOffset + 1).coerceAtMost(doc.textLength)
        return Pair(pStartOffset, end)
    }

    /**
     * Pair delimiters: '(', '[', '{', '<', '"', '\'', '`'
     */
    private fun findDelimitedPairRange(
        doc: Document,
        caret: Caret,
        inside: Boolean,
        delim: Char
    ): Pair<Int, Int>? {
        val pair = HelixSurroundActions.findEnclosingPair(doc, caret, delim) ?: return null
        val (openPos, closePos) = pair

        return if (inside) {
            Pair(openPos + 1, closePos)
        } else {
            Pair(openPos, closePos + 1)
        }
    }

    /**
     * m: Closest enclosing pair (brackets, braces, parens, quotes)
     */
    private fun findClosestPairRange(doc: Document, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val candidates = listOf('(', '[', '{', '<', '"', '\'', '`')
            .mapNotNull { HelixSurroundActions.findEnclosingPair(doc, caret, it) }

        val closest = candidates.minByOrNull { it.second - it.first } ?: return null
        return if (inside) {
            Pair(closest.first + 1, closest.second)
        } else {
            Pair(closest.first, closest.second + 1)
        }
    }

    /**
     * c: Comment textobject
     */
    private fun findCommentRange(editor: Editor, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val project = editor.project
        val doc = editor.document
        if (project != null) {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc)
            if (psiFile != null) {
                val element = psiFile.findElementAt(caret.offset.coerceIn(0, (doc.textLength - 1).coerceAtLeast(0)))
                val comment = PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false)
                if (comment != null) {
                    val range = comment.textRange
                    if (!inside) {
                        return Pair(range.startOffset, range.endOffset)
                    }
                    val text = comment.text
                    val trimmedStart = text.indexOfFirst { it != '/' && it != '*' && it != '#' && !it.isWhitespace() }
                    val trimmedEnd = text.indexOfLast { it != '/' && it != '*' && !it.isWhitespace() }
                    if (trimmedStart in 0..trimmedEnd) {
                        return Pair(range.startOffset + trimmedStart, range.startOffset + trimmedEnd + 1)
                    }
                    return Pair(range.startOffset, range.endOffset)
                }
            }
        }

        // Fallback: line comment detection on current line
        val line = doc.getLineNumber(caret.offset.coerceIn(0, doc.textLength))
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val lineText = doc.charsSequence.subSequence(lineStart, lineEnd).toString()

        val commentPrefix = listOf("//", "#", "--").firstOrNull { lineText.contains(it) } ?: return null
        val commentIdx = lineText.indexOf(commentPrefix)
        val startOffset = lineStart + commentIdx
        return if (inside) {
            val innerStart = startOffset + commentPrefix.length
            val trimmedStart = (innerStart until lineEnd).firstOrNull { !doc.charsSequence[it].isWhitespace() } ?: innerStart
            Pair(trimmedStart, lineEnd)
        } else {
            Pair(startOffset, lineEnd)
        }
    }

    /**
     * f: Function textobject
     * Uses PSI when available, otherwise falls back to enclosing '{' ... '}' block.
     */
    private fun findFunctionRange(editor: Editor, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val project = editor.project
        val doc = editor.document

        if (project != null) {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc)
            if (psiFile != null) {
                var elem: PsiElement? = psiFile.findElementAt(caret.offset.coerceIn(0, (doc.textLength - 1).coerceAtLeast(0)))
                while (elem != null && elem !is com.intellij.psi.PsiFile) {
                    val typeName = elem.javaClass.simpleName
                    if (typeName.contains("Method", ignoreCase = true) ||
                        typeName.contains("Function", ignoreCase = true)
                    ) {
                        val fullRange = elem.textRange
                        if (!inside) {
                            return Pair(fullRange.startOffset, fullRange.endOffset)
                        }
                        // For inside function, find body enclosed in { ... }
                        val bodyPair = findEnclosingBracketsInElement(doc, elem, '{', '}')
                        if (bodyPair != null) {
                            return bodyPair
                        }
                        return Pair(fullRange.startOffset, fullRange.endOffset)
                    }
                    elem = elem.parent
                }
            }
        }

        // Fallback to closest enclosing { ... }
        return findDelimitedPairRange(doc, caret, inside, '{')
    }

    /**
     * t: Type / Class textobject
     */
    private fun findTypeRange(editor: Editor, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val project = editor.project
        val doc = editor.document

        if (project != null) {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc)
            if (psiFile != null) {
                var elem: PsiElement? = psiFile.findElementAt(caret.offset.coerceIn(0, (doc.textLength - 1).coerceAtLeast(0)))
                while (elem != null && elem !is com.intellij.psi.PsiFile) {
                    val typeName = elem.javaClass.simpleName
                    if (typeName.contains("Class", ignoreCase = true) ||
                        typeName.contains("Interface", ignoreCase = true) ||
                        typeName.contains("Struct", ignoreCase = true)
                    ) {
                        val fullRange = elem.textRange
                        if (!inside) {
                            return Pair(fullRange.startOffset, fullRange.endOffset)
                        }
                        val bodyPair = findEnclosingBracketsInElement(doc, elem, '{', '}')
                        if (bodyPair != null) {
                            return bodyPair
                        }
                        return Pair(fullRange.startOffset, fullRange.endOffset)
                    }
                    elem = elem.parent
                }
            }
        }

        return findDelimitedPairRange(doc, caret, inside, '{')
    }

    /**
     * a: Argument / Parameter textobject
     */
    private fun findArgumentRange(editor: Editor, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val doc = editor.document
        val pair = HelixSurroundActions.findEnclosingPair(doc, caret, '(') ?: return null
        val (openPos, closePos) = pair
        val innerText = doc.charsSequence.subSequence(openPos + 1, closePos)

        val caretOffsetInInner = caret.offset - (openPos + 1)
        // Split by commas taking nesting into account
        var currentItemStart = 0
        var depth = 0
        val items = mutableListOf<Pair<Int, Int>>() // relative to openPos + 1

        for (i in innerText.indices) {
            val c = innerText[i]
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth > 0) depth--
                ',' -> {
                    if (depth == 0) {
                        items.add(Pair(currentItemStart, i))
                        currentItemStart = i + 1
                    }
                }
            }
        }
        items.add(Pair(currentItemStart, innerText.length))

        val matchedItem = items.firstOrNull { (s, e) ->
            caretOffsetInInner in s..e
        } ?: items.firstOrNull() ?: return null

        val itemStartRel = matchedItem.first
        val itemEndRel = matchedItem.second

        val absStart = openPos + 1 + itemStartRel
        val absEnd = openPos + 1 + itemEndRel

        val text = doc.charsSequence
        var trimmedStart = absStart
        while (trimmedStart < absEnd && text[trimmedStart].isWhitespace()) trimmedStart++
        var trimmedEnd = absEnd
        while (trimmedEnd > trimmedStart && text[trimmedEnd - 1].isWhitespace()) trimmedEnd--

        if (inside) {
            return Pair(trimmedStart, trimmedEnd)
        }

        // Around: include following comma and trailing space, or preceding comma
        if (absEnd < closePos && text[absEnd] == ',') {
            var afterComma = absEnd + 1
            while (afterComma < closePos && text[afterComma].isWhitespace()) afterComma++
            return Pair(trimmedStart, afterComma)
        } else {
            // Find preceding comma before absStart
            var beforeComma = absStart - 1
            while (beforeComma > openPos && text[beforeComma].isWhitespace()) beforeComma--
            if (beforeComma > openPos && text[beforeComma] == ',') {
                return Pair(beforeComma, trimmedEnd)
            }
            return Pair(trimmedStart, trimmedEnd)
        }
    }

    private fun findEnclosingBracketsInElement(
        doc: Document,
        elem: PsiElement,
        open: Char,
        close: Char
    ): Pair<Int, Int>? {
        val range = elem.textRange
        val text = doc.charsSequence
        val openIdx = (range.startOffset until range.endOffset).firstOrNull { text[it] == open } ?: return null
        val closeIdx = (range.endOffset - 1 downTo openIdx).firstOrNull { text[it] == close } ?: return null
        return Pair(openIdx + 1, closeIdx)
    }
}
