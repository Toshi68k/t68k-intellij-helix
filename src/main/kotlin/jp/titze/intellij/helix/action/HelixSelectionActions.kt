package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange

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

    fun rotateSelectionsContents(editor: Editor, forward: Boolean) {
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

        val newTexts = List(n) { i ->
            if (forward) {
                texts[(i - 1 + n) % n]
            } else {
                texts[(i + 1) % n]
            }
        }

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
    }
}
