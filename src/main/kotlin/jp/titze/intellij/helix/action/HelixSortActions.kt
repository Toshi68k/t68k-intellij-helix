// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixSortActions {

    private val NUMBER_REGEX = """-?\d+(?:\.\d+)?""".toRegex()

    @JvmOverloads
    fun sortLines(editor: Editor, reverse: Boolean = false, numeric: Boolean = false) {
        val project = editor.project ?: return
        val doc = editor.document
        if (doc.lineCount <= 1 || doc.textLength == 0) return

        WriteCommandAction.runWriteCommandAction(
            project,
            "Sort Lines",
            null,
            Runnable {
                val selectedRanges = collectLineRanges(editor)

                if (selectedRanges.isNotEmpty()) {
                    // Sort ranges in descending line order to preserve offsets
                    val sortedRanges = mergeAndSortDescending(selectedRanges)
                    for ((startLine, endLine) in sortedRanges) {
                        if (startLine >= endLine) continue
                        sortLineRange(doc, startLine, endLine, reverse, numeric)
                    }
                } else {
                    // No multi-line selection: sort entire buffer
                    val lastLine = doc.lineCount - 1
                    val isLastLineEmpty = lastLine > 0 &&
                        doc.getLineStartOffset(lastLine) == doc.getLineEndOffset(lastLine)
                    val effectiveEndLine = if (isLastLineEmpty) lastLine - 1 else lastLine
                    if (effectiveEndLine > 0) {
                        sortLineRange(doc, 0, effectiveEndLine, reverse, numeric)
                    }
                }
            },
        )
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode == HelixMode.SELECT) {
            state.setMode(HelixMode.NORMAL)
        }
    }

    private fun collectLineRanges(editor: Editor): List<Pair<Int, Int>> {
        val doc = editor.document
        val ranges = mutableListOf<Pair<Int, Int>>()

        for (caret in editor.caretModel.allCarets) {
            if (!caret.hasSelection()) continue
            val startLine = doc.getLineNumber(caret.selectionStart)
            val endOffset = caret.selectionEnd
            val endLine = if (endOffset > caret.selectionStart &&
                doc.getLineStartOffset(doc.getLineNumber(endOffset)) == endOffset
            ) {
                (doc.getLineNumber(endOffset) - 1).coerceAtLeast(startLine)
            } else {
                doc.getLineNumber(endOffset)
            }
            if (startLine < endLine) {
                ranges.add(Pair(startLine, endLine))
            }
        }
        return ranges
    }

    private fun mergeAndSortDescending(ranges: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (ranges.isEmpty()) return emptyList()
        val sortedAsc = ranges.sortedWith(compareBy({ it.first }, { it.second }))
        val merged = mutableListOf<Pair<Int, Int>>()

        var cur = sortedAsc[0]
        for (i in 1 until sortedAsc.size) {
            val next = sortedAsc[i]
            if (next.first <= cur.second) {
                cur = Pair(cur.first, maxOf(cur.second, next.second))
            } else {
                merged.add(cur)
                cur = next
            }
        }
        merged.add(cur)
        return merged.sortedByDescending { it.first }
    }

    private fun extractFirstNumber(s: String): Double? {
        val match = NUMBER_REGEX.find(s) ?: return null
        return match.value.toDoubleOrNull()
    }

    private fun sortLineRange(
        doc: com.intellij.openapi.editor.Document,
        startLine: Int,
        endLine: Int,
        reverse: Boolean,
        numeric: Boolean,
    ) {
        val lines = (startLine..endLine).map { lineIdx ->
            doc.getText(TextRange(doc.getLineStartOffset(lineIdx), doc.getLineEndOffset(lineIdx)))
        }
        val comparator = if (numeric) {
            Comparator<String> { a, b ->
                val numA = extractFirstNumber(a)
                val numB = extractFirstNumber(b)
                when {
                    numA != null && numB != null -> numA.compareTo(numB)
                    numA != null -> -1
                    numB != null -> 1
                    else -> a.compareTo(b)
                }
            }
        } else {
            Comparator { a, b -> a.compareTo(b) }
        }
        val finalComparator = if (reverse) comparator.reversed() else comparator
        val sorted = lines.sortedWith(finalComparator)
        val rangeStart = doc.getLineStartOffset(startLine)
        val rangeEnd = doc.getLineEndOffset(endLine)
        doc.replaceString(rangeStart, rangeEnd, sorted.joinToString("\n"))
    }
}
