// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.register.HelixRegisterEntry
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixSelectionActions {

    fun trimSelections(editor: Editor) {
        val doc = editor.document
        if (doc.textLength == 0) return
        val text = doc.charsSequence

        val newCarets = editor.caretModel.allCarets.map { caret ->
            if (!caret.hasSelection()) {
                val offset = caret.offset
                HelixCaretSnapshot(offset, offset, offset)
            } else {
                val start = caret.selectionStart
                val end = caret.selectionEnd
                val isForward = caret.offset >= caret.leadSelectionOffset

                var firstNonWs = -1
                for (i in start until end) {
                    if (!text[i].isWhitespace()) {
                        firstNonWs = i
                        break
                    }
                }

                if (firstNonWs == -1) {
                    HelixCaretSnapshot(start, start, start)
                } else {
                    var lastNonWs = firstNonWs
                    for (i in end - 1 downTo firstNonWs) {
                        if (!text[i].isWhitespace()) {
                            lastNonWs = i
                            break
                        }
                    }
                    val newStart = firstNonWs
                    val newEnd = lastNonWs + 1
                    val cursor = if (isForward) newEnd else newStart
                    HelixCaretSnapshot(cursor, newStart, newEnd)
                }
            }
        }

        val caretStates = newCarets.map {
            CaretState(
                editor.offsetToLogicalPosition(it.offset),
                editor.offsetToLogicalPosition(it.selectionStart),
                editor.offsetToLogicalPosition(it.selectionEnd),
            )
        }
        editor.caretModel.setCaretsAndSelections(caretStates)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode == HelixMode.SELECT) {
            state.setMode(HelixMode.NORMAL)
        }
    }

    fun alignSelections(editor: Editor) {
        val allCarets = editor.caretModel.allCarets
        if (allCarets.size <= 1) return
        val doc = editor.document
        val project = editor.project

        val caretsByLine = allCarets.groupBy { doc.getLineNumber(it.selectionStart) }
            .mapValues { (_, carets) -> carets.sortedBy { it.selectionStart } }

        val maxColsPerLine = caretsByLine.values.maxOfOrNull { it.size } ?: 0
        if (maxColsPerLine == 0) return

        data class CaretPadInfo(val originalStart: Int, val originalEnd: Int, val isForward: Boolean, var padding: Int)

        val lineCaretsInfo = caretsByLine.mapValues { (_, carets) ->
            carets.map {
                CaretPadInfo(
                    originalStart = it.selectionStart,
                    originalEnd = it.selectionEnd,
                    isForward = it.offset >= it.leadSelectionOffset,
                    padding = 0,
                )
            }.toMutableList()
        }

        for (colIdx in 0 until maxColsPerLine) {
            val linesWithCol = lineCaretsInfo.filter { it.value.size > colIdx }
            var maxCol = 0

            for ((line, carets) in linesWithCol) {
                val lineStart = doc.getLineStartOffset(line)
                val info = carets[colIdx]
                val prevPaddings = carets.take(colIdx).sumOf { it.padding }
                val currentCol = (info.originalStart - lineStart) + prevPaddings
                if (currentCol > maxCol) {
                    maxCol = currentCol
                }
            }

            for ((line, carets) in linesWithCol) {
                val lineStart = doc.getLineStartOffset(line)
                val info = carets[colIdx]
                val prevPaddings = carets.take(colIdx).sumOf { it.padding }
                val currentCol = (info.originalStart - lineStart) + prevPaddings
                val diff = maxCol - currentCol
                if (diff > 0) {
                    info.padding = diff
                }
            }
        }

        val sortedLines = lineCaretsInfo.keys.sorted()
        val lineReplacements = mutableMapOf<Int, String>()
        var lineCumulativeDelta = 0
        data class CaretFinalSpan(val cursor: Int, val start: Int, val end: Int)
        val finalSpans = mutableListOf<CaretFinalSpan>()

        for (line in sortedLines) {
            val carets = lineCaretsInfo[line] ?: continue
            val lineStart = doc.getLineStartOffset(line)
            val lineEnd = doc.getLineEndOffset(line)
            val lineText = doc.getText(TextRange(lineStart, lineEnd))

            val sb = StringBuilder()
            var lastIdx = 0
            var runningLinePadding = 0

            for (caret in carets) {
                val relOffset = (caret.originalStart - lineStart).coerceIn(0, lineText.length)
                sb.append(lineText.substring(lastIdx, relOffset))
                if (caret.padding > 0) {
                    sb.append(" ".repeat(caret.padding))
                }
                runningLinePadding += caret.padding
                lastIdx = relOffset

                val newStart = lineStart + lineCumulativeDelta + relOffset + runningLinePadding
                val origLen = caret.originalEnd - caret.originalStart
                val newEnd = newStart + origLen
                val cursor = if (caret.isForward) newEnd else newStart
                finalSpans.add(CaretFinalSpan(cursor, newStart, newEnd))
            }
            sb.append(lineText.substring(lastIdx))
            val newLineText = sb.toString()
            lineReplacements[line] = newLineText
            lineCumulativeDelta += (newLineText.length - lineText.length)
        }

        WriteCommandAction.runWriteCommandAction(project) {
            for (line in sortedLines.reversed()) {
                val newText = lineReplacements[line] ?: continue
                val lineStart = doc.getLineStartOffset(line)
                val lineEnd = doc.getLineEndOffset(line)
                doc.replaceString(lineStart, lineEnd, newText)
            }
        }

        val finalCaretStates = finalSpans.map {
            CaretState(
                editor.offsetToLogicalPosition(it.cursor),
                editor.offsetToLogicalPosition(it.start),
                editor.offsetToLogicalPosition(it.end),
            )
        }
        editor.caretModel.setCaretsAndSelections(finalCaretStates)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode == HelixMode.SELECT) {
            state.setMode(HelixMode.NORMAL)
        }
    }

    fun ensureSelectionsForward(editor: Editor) {
        var changed = false
        val newStates = editor.caretModel.allCarets.map { caret ->
            if (caret.hasSelection() && caret.offset < caret.leadSelectionOffset) {
                changed = true
                CaretState(
                    editor.offsetToLogicalPosition(caret.selectionEnd),
                    editor.offsetToLogicalPosition(caret.selectionStart),
                    editor.offsetToLogicalPosition(caret.selectionEnd),
                )
            } else {
                CaretState(
                    editor.offsetToLogicalPosition(caret.offset),
                    editor.offsetToLogicalPosition(caret.selectionStart),
                    editor.offsetToLogicalPosition(caret.selectionEnd),
                )
            }
        }
        if (changed) {
            editor.caretModel.setCaretsAndSelections(newStates)
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
    }

    fun mergeSelections(editor: Editor) {
        val carets = editor.caretModel.allCarets.sortedBy { it.selectionStart }
        if (carets.size <= 1) return

        val merged = mutableListOf<Pair<Int, Int>>()
        var curStart = carets[0].selectionStart
        var curEnd = carets[0].selectionEnd

        for (i in 1 until carets.size) {
            val nextStart = carets[i].selectionStart
            val nextEnd = carets[i].selectionEnd
            if (nextStart <= curEnd) {
                curEnd = maxOf(curEnd, nextEnd)
            } else {
                merged.add(Pair(curStart, curEnd))
                curStart = nextStart
                curEnd = nextEnd
            }
        }
        merged.add(Pair(curStart, curEnd))

        if (merged.size < carets.size) {
            HelixCaretUtils.applyCarets(editor, merged)
        }
    }

    fun mergeAllSelections(editor: Editor) {
        val carets = editor.caretModel.allCarets
        if (carets.size <= 1) return
        val minStart = carets.minOf { if (it.hasSelection()) it.selectionStart else it.offset }
        val maxEnd = carets.maxOf { if (it.hasSelection()) it.selectionEnd else it.offset }
        HelixCaretUtils.applyCarets(editor, listOf(Pair(minStart, maxEnd)))
    }

    fun rotateSelectionsContents(editor: Editor, forward: Boolean) {
        transformSelectionsContents(editor) { texts ->
            val n = texts.size
            List(n) { i ->
                if (forward) {
                    texts[(i - 1 + n) % n]
                } else {
                    texts[(i + 1) % n]
                }
            }
        }
    }

    fun reverseSelectionsContents(editor: Editor) {
        transformSelectionsContents(editor) { texts ->
            texts.reversed()
        }
    }

    private fun transformSelectionsContents(editor: Editor, transform: (List<String>) -> List<String>) {
        val allCarets = editor.caretModel.allCarets
        if (allCarets.size <= 1) return
        val doc = editor.document
        val project = editor.project

        val sortedCarets = allCarets.sortedBy { it.selectionStart }
        val n = sortedCarets.size

        val ranges = sortedCarets.map {
            if (it.hasSelection()) {
                Pair(it.selectionStart, it.selectionEnd)
            } else {
                val start = it.offset
                val end = minOf(start + 1, doc.textLength)
                Pair(start, end)
            }
        }
        val orientations = sortedCarets.map { it.offset >= it.leadSelectionOffset }
        val texts = ranges.map { (start, end) ->
            if (start < end) doc.getText(TextRange(start, end)) else ""
        }

        val newTexts = transform(texts)

        var delta = 0
        val newRanges = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until n) {
            val origLen = ranges[i].second - ranges[i].first
            val newLen = newTexts[i].length
            val newStart = ranges[i].first + delta
            val newEnd = newStart + newLen
            newRanges.add(Pair(newStart, newEnd))
            delta += (newLen - origLen)
        }

        WriteCommandAction.runWriteCommandAction(project) {
            for (i in n - 1 downTo 0) {
                val (start, end) = ranges[i]
                doc.replaceString(start, end, newTexts[i])
            }
        }

        val caretStates = List(n) { i ->
            val (start, end) = newRanges[i]
            val isFwd = orientations[i]
            val cursor = if (isFwd) end else start
            CaretState(
                editor.offsetToLogicalPosition(cursor),
                editor.offsetToLogicalPosition(start),
                editor.offsetToLogicalPosition(end),
            )
        }
        editor.caretModel.setCaretsAndSelections(caretStates)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode == HelixMode.SELECT) {
            state.setMode(HelixMode.NORMAL)
        }
    }

    fun addSelectionNext(editor: Editor): Boolean {
        val doc = editor.document
        val text = doc.charsSequence
        if (text.isEmpty()) return false

        val primary = editor.caretModel.primaryCaret
        if (!primary.hasSelection()) {
            val wordRange = findWordAtOffset(text, primary.offset) ?: return false
            primary.setSelection(wordRange.first, wordRange.second)
            primary.moveToOffset(wordRange.second)
            val selectedText = text.subSequence(wordRange.first, wordRange.second).toString()
            HelixRegisterManager.set('/', HelixRegisterEntry(selectedText))
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
            return true
        }

        val selectedText = primary.selectedText ?: return false
        val regex = createOccurrenceRegex(selectedText)
        val allMatches = regex.findAll(text).map { Pair(it.range.first, it.range.last + 1) }.toList()
        if (allMatches.isEmpty()) return false

        val existingSelections = editor.caretModel.allCarets.map {
            Pair(it.selectionStart, it.selectionEnd)
        }.toSet()

        val unselectedMatches = allMatches.filter { it !in existingSelections }
        if (unselectedMatches.isEmpty()) return false

        val searchFrom = primary.selectionEnd
        val nextMatch = unselectedMatches.firstOrNull { it.first >= searchFrom } ?: unselectedMatches.first()

        val newCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(nextMatch.second))
        if (newCaret != null) {
            newCaret.moveToOffset(nextMatch.second)
            newCaret.setSelection(nextMatch.first, nextMatch.second)
        } else {
            val currentSnapshots = HelixCaretUtils.captureCarets(editor).toMutableList()
            currentSnapshots.add(HelixCaretSnapshot(nextMatch.second, nextMatch.first, nextMatch.second))
            HelixCaretUtils.restoreCarets(editor, currentSnapshots)
        }

        HelixRegisterManager.set('/', HelixRegisterEntry(selectedText))
        editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(nextMatch.second), ScrollType.MAKE_VISIBLE)
        return true
    }

    fun addSelectionPrev(editor: Editor): Boolean {
        val doc = editor.document
        val text = doc.charsSequence
        if (text.isEmpty()) return false

        val primary = editor.caretModel.primaryCaret
        if (!primary.hasSelection()) {
            val wordRange = findWordAtOffset(text, primary.offset) ?: return false
            primary.setSelection(wordRange.first, wordRange.second)
            primary.moveToOffset(wordRange.second)
            val selectedText = text.subSequence(wordRange.first, wordRange.second).toString()
            HelixRegisterManager.set('/', HelixRegisterEntry(selectedText))
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
            return true
        }

        val selectedText = primary.selectedText ?: return false
        val regex = createOccurrenceRegex(selectedText)
        val allMatches = regex.findAll(text).map { Pair(it.range.first, it.range.last + 1) }.toList()
        if (allMatches.isEmpty()) return false

        val existingSelections = editor.caretModel.allCarets.map {
            Pair(it.selectionStart, it.selectionEnd)
        }.toSet()

        val unselectedMatches = allMatches.filter { it !in existingSelections }
        if (unselectedMatches.isEmpty()) return false

        val searchBefore = primary.selectionStart
        val prevMatch = unselectedMatches.lastOrNull { it.second <= searchBefore } ?: unselectedMatches.last()

        val newCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(prevMatch.second))
        if (newCaret != null) {
            newCaret.moveToOffset(prevMatch.second)
            newCaret.setSelection(prevMatch.first, prevMatch.second)
        } else {
            val currentSnapshots = HelixCaretUtils.captureCarets(editor).toMutableList()
            currentSnapshots.add(HelixCaretSnapshot(prevMatch.second, prevMatch.first, prevMatch.second))
            HelixCaretUtils.restoreCarets(editor, currentSnapshots)
        }

        HelixRegisterManager.set('/', HelixRegisterEntry(selectedText))
        editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(prevMatch.second), ScrollType.MAKE_VISIBLE)
        return true
    }

    fun selectAllOccurrences(editor: Editor): Boolean {
        val doc = editor.document
        val text = doc.charsSequence
        if (text.isEmpty()) return false

        val primary = editor.caretModel.primaryCaret
        val selectedText = if (primary.hasSelection()) {
            primary.selectedText ?: ""
        } else {
            val wordRange = findWordAtOffset(text, primary.offset) ?: return false
            text.subSequence(wordRange.first, wordRange.second).toString()
        }
        if (selectedText.isEmpty()) return false

        val regex = createOccurrenceRegex(selectedText)
        val allMatches = regex.findAll(text).map { Pair(it.range.first, it.range.last + 1) }.toList()
        if (allMatches.isEmpty()) return false

        HelixCaretUtils.applyCarets(editor, allMatches)
        HelixRegisterManager.set('/', HelixRegisterEntry(selectedText))
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        return true
    }

    private fun createOccurrenceRegex(selectedText: String): Regex {
        val pattern = if (isWord(selectedText)) {
            """\b${Regex.escape(selectedText)}\b"""
        } else {
            Regex.escape(selectedText)
        }
        return try {
            Regex(pattern)
        } catch (_: Exception) {
            Regex(Regex.escape(selectedText))
        }
    }

    private fun findWordAtOffset(text: CharSequence, offset: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null
        var safeOffset = offset.coerceIn(0, text.length - 1)
        if (!isWordChar(text[safeOffset]) && safeOffset > 0 && isWordChar(text[safeOffset - 1])) {
            safeOffset--
        }
        if (!isWordChar(text[safeOffset])) return null
        var start = safeOffset
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = safeOffset
        while (end < text.length && isWordChar(text[end])) end++
        return if (end > start) Pair(start, end) else null
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    private fun isWord(s: String): Boolean = s.isNotEmpty() && s.all { isWordChar(it) }
}
