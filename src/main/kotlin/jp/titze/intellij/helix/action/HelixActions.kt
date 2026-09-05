package jp.titze.intellij.helix.action

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

object HelixActions {

    fun deleteSelection(editor: Editor, enterInsert: Boolean = false) {
        val project = editor.project
        val state = HelixStateManager.getOrCreate(editor)

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.selectionStart }
            val yankPieces = mutableListOf<String>()

            val doc = editor.document
            for (caret in carets) {
                if (caret.hasSelection()) {
                    var text = caret.selectedText ?: ""
                    val startLine = doc.getLineNumber(caret.selectionStart)
                    val endLine = doc.getLineNumber((caret.selectionEnd - 1).coerceAtLeast(0))
                    val isWholeLines = caret.selectionStart == doc.getLineStartOffset(startLine) &&
                            caret.selectionEnd == (if (endLine + 1 < doc.lineCount) doc.getLineStartOffset(endLine + 1) else doc.getLineEndOffset(endLine))
                    if (isWholeLines && !text.endsWith("\n")) {
                        text += "\n"
                    }
                    yankPieces.add(text)
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    editor.document.deleteString(start, end)
                    caret.removeSelection()
                    caret.moveToOffset(start)
                } else {
                    val offset = caret.offset
                    if (offset < editor.document.textLength) {
                        val text = editor.document.getText(TextRange(offset, offset + 1))
                        yankPieces.add(text)
                        editor.document.deleteString(offset, offset + 1)
                        caret.moveToOffset(offset)
                    }
                }
            }

            if (yankPieces.isNotEmpty()) {
                val fullYank = yankPieces.reversed().joinToString("\n")
                state.yankRegister = fullYank
                setClipboard(fullYank)
            }
        }

        if (enterInsert) {
            state.setMode(HelixMode.INSERT)
        }
    }

    fun yankSelection(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        val pieces = mutableListOf<String>()
        val doc = editor.document

        editor.caretModel.runForEachCaret { caret ->
            if (caret.hasSelection()) {
                var text = caret.selectedText ?: ""
                val startLine = doc.getLineNumber(caret.selectionStart)
                val endLine = doc.getLineNumber((caret.selectionEnd - 1).coerceAtLeast(0))
                val isWholeLines = caret.selectionStart == doc.getLineStartOffset(startLine) &&
                        caret.selectionEnd == (if (endLine + 1 < doc.lineCount) doc.getLineStartOffset(endLine + 1) else doc.getLineEndOffset(endLine))
                if (isWholeLines && !text.endsWith("\n")) {
                    text += "\n"
                }
                pieces.add(text)
            } else {
                val offset = caret.offset
                if (offset < editor.document.textLength) {
                    pieces.add(editor.document.getText(TextRange(offset, offset + 1)))
                }
            }
        }

        if (pieces.isNotEmpty()) {
            val yanked = pieces.joinToString("\n")
            state.yankRegister = yanked
            setClipboard(yanked)
        }
    }

    fun paste(editor: Editor, after: Boolean = true) {
        val project = editor.project
        val rawClipboard = getClipboard() ?: return
        val textToPaste = rawClipboard.replace("\r\n", "\n").replace("\r", "\n")
        val isLinewise = textToPaste.endsWith("\n") || textToPaste.contains("\n")
        val doc = editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.offset }
            for (caret in carets) {
                if (isLinewise) {
                    val hasSel = caret.hasSelection()
                    val selStart = caret.selectionStart
                    val selEnd = caret.selectionEnd

                    val curLine = if (hasSel && caret.offset in selStart..selEnd) {
                        if (after) {
                            doc.getLineNumber((selEnd - 1).coerceAtLeast(0))
                        } else {
                            doc.getLineNumber(selStart)
                        }
                    } else {
                        doc.getLineNumber(caret.offset.coerceIn(0, doc.textLength))
                    }

                    if (after) {
                        if (doc.textLength == 0) {
                            val content = if (textToPaste.endsWith("\n")) textToPaste else textToPaste + "\n"
                            doc.insertString(0, content)
                            caret.removeSelection()
                            caret.moveToOffset(0)
                        } else if (curLine < doc.lineCount - 1) {
                            val insertOffset = doc.getLineStartOffset(curLine + 1)
                            val content = if (textToPaste.endsWith("\n")) textToPaste else textToPaste + "\n"
                            doc.insertString(insertOffset, content)
                            caret.removeSelection()
                            caret.moveToOffset(insertOffset)
                        } else {
                            val lineEndOffset = doc.getLineEndOffset(curLine)
                            val cleanText = textToPaste.removeSuffix("\n")
                            val content = "\n" + cleanText
                            doc.insertString(lineEndOffset, content)
                            caret.removeSelection()
                            caret.moveToOffset(lineEndOffset + 1)
                        }
                    } else {
                        val insertOffset = doc.getLineStartOffset(curLine)
                        val content = if (textToPaste.endsWith("\n")) textToPaste else textToPaste + "\n"
                        doc.insertString(insertOffset, content)
                        caret.removeSelection()
                        caret.moveToOffset(insertOffset)
                    }
                } else {
                    val offset = if (after) {
                        if (caret.hasSelection()) {
                            (caret.selectionEnd).coerceAtMost(doc.textLength)
                        } else {
                            val lineEnd = doc.getLineEndOffset(doc.getLineNumber(caret.offset))
                            (caret.offset + 1).coerceAtMost(lineEnd)
                        }
                    } else {
                        caret.selectionStart
                    }
                    doc.insertString(offset, textToPaste)
                    val newOffset = offset + textToPaste.length
                    caret.removeSelection()
                    caret.moveToOffset(newOffset)
                }
            }
        }
    }

    fun replaceWithClipboard(editor: Editor) {
        val project = editor.project
        val textToPaste = getClipboard() ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.offset }
            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    editor.document.replaceString(start, end, textToPaste)
                    caret.setSelection(start, start + textToPaste.length)
                    caret.moveToOffset(start)
                } else {
                    val offset = caret.offset
                    if (offset < editor.document.textLength) {
                        editor.document.replaceString(offset, offset + 1, textToPaste)
                        caret.setSelection(offset, offset + textToPaste.length)
                        caret.moveToOffset(offset)
                    } else {
                        editor.document.insertString(offset, textToPaste)
                        caret.setSelection(offset, offset + textToPaste.length)
                        caret.moveToOffset(offset)
                    }
                }
            }
        }
    }

    fun replaceChar(editor: Editor, replacement: Char, count: Int = 1) {
        val project = editor.project
        val doc = editor.document
        if (doc.textLength == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }

            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    val selectedText = doc.charsSequence.subSequence(start, end)
                    val sb = StringBuilder(selectedText.length)
                    for (i in 0 until selectedText.length) {
                        val c = selectedText[i]
                        if ((c == '\n' || c == '\r') && replacement != '\n' && replacement != '\r') {
                            sb.append(c)
                        } else {
                            sb.append(replacement)
                        }
                    }
                    val replacedString = sb.toString()
                    doc.replaceString(start, end, replacedString)
                    caret.setSelection(start, start + replacedString.length)
                    caret.moveToOffset(start)
                } else {
                    val offset = caret.offset
                    if (offset < doc.textLength) {
                        val lineNum = doc.getLineNumber(offset)
                        val lineEnd = doc.getLineEndOffset(lineNum)
                        val replaceCount = if (doc.charsSequence[offset] == '\n' || doc.charsSequence[offset] == '\r') {
                            1
                        } else {
                            count.coerceAtLeast(1).coerceAtMost((lineEnd - offset).coerceAtLeast(1))
                        }
                        val sb = StringBuilder(replaceCount)
                        repeat(replaceCount) {
                            sb.append(replacement)
                        }
                        val replacedString = sb.toString()
                        val replaceEnd = (offset + replaceCount).coerceAtMost(doc.textLength)
                        doc.replaceString(offset, replaceEnd, replacedString)
                        if (isSelect) {
                            caret.setSelection(offset, offset + replacedString.length)
                        } else {
                            caret.removeSelection()
                        }
                        caret.moveToOffset(offset)
                    }
                }
            }
        }
    }

    fun joinLines(editor: Editor, count: Int = 1) {
        val project = editor.project
        val doc = editor.document
        if (doc.lineCount <= 1) return

        val state = HelixStateManager.getOrCreate(editor)

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.selectionStart }
            val processedLines = mutableSetOf<Int>()

            for (caret in carets) {
                val selStart = caret.selectionStart
                val selEnd = caret.selectionEnd
                val startLine = doc.getLineNumber(selStart)
                val endLine = if (selEnd > selStart) {
                    doc.getLineNumber((selEnd - 1).coerceAtLeast(0))
                } else {
                    startLine
                }

                val targetEndLine = if (startLine < endLine) {
                    endLine
                } else {
                    (startLine + count.coerceAtLeast(1)).coerceAtMost(doc.lineCount - 1)
                }

                if (targetEndLine <= startLine) {
                    continue
                }

                if (!processedLines.add(startLine)) {
                    continue
                }

                val replaceStart = doc.getLineStartOffset(startLine)
                val replaceEnd = doc.getLineEndOffset(targetEndLine)

                val sb = StringBuilder()
                var joinOffsetInResult = -1

                for (lineIdx in startLine..targetEndLine) {
                    val lineStart = doc.getLineStartOffset(lineIdx)
                    val lineEnd = doc.getLineEndOffset(lineIdx)
                    val lineText = doc.getText(TextRange(lineStart, lineEnd))

                    if (lineIdx == startLine) {
                        val trimmedTrailing = lineText.trimEnd(' ', '\t', '\r')
                        sb.append(trimmedTrailing)
                        if (trimmedTrailing.isNotEmpty()) {
                            joinOffsetInResult = sb.length
                        }
                    } else {
                        val trimmed = lineText.trim(' ', '\t', '\r')
                        if (trimmed.isNotEmpty()) {
                            if (sb.isNotEmpty()) {
                                if (joinOffsetInResult == -1) {
                                    joinOffsetInResult = sb.length
                                }
                                sb.append(' ')
                            }
                            sb.append(trimmed)
                        }
                    }
                }

                val replacement = sb.toString()
                doc.replaceString(replaceStart, replaceEnd, replacement)

                val targetOffset = if (joinOffsetInResult != -1) {
                    (replaceStart + joinOffsetInResult).coerceAtMost(replaceStart + replacement.length)
                } else {
                    replaceStart
                }

                if (state.mode == HelixMode.SELECT) {
                    caret.setSelection(replaceStart, replaceStart + replacement.length)
                    caret.moveToOffset(replaceStart + replacement.length)
                } else {
                    caret.removeSelection()
                    caret.moveToOffset(targetOffset)
                }
            }
        }
    }

    fun enterInsert(editor: Editor) {
        editor.caretModel.runForEachCaret { it.removeSelection() }
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)
    }

    fun enterInsertAppend(editor: Editor) {
        val maxLen = editor.document.textLength
        editor.caretModel.runForEachCaret { caret ->
            val newOffset = if (caret.hasSelection()) {
                caret.selectionEnd
            } else {
                (caret.offset + 1).coerceAtMost(maxLen)
            }
            caret.removeSelection()
            caret.moveToOffset(newOffset)
        }
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)
    }

    fun enterInsertLineStart(editor: Editor) {
        val doc = editor.document
        val text = doc.charsSequence
        editor.caretModel.runForEachCaret { caret ->
            val line = doc.getLineNumber(caret.offset)
            val lineStart = doc.getLineStartOffset(line)
            val lineEnd = doc.getLineEndOffset(line)
            var target = lineStart
            while (target < lineEnd && text[target].isWhitespace() && text[target] != '\n') {
                target++
            }
            caret.removeSelection()
            caret.moveToOffset(target)
        }
        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)
    }

    fun enterInsertLineEnd(editor: Editor) {
        val doc = editor.document
        editor.caretModel.runForEachCaret { caret ->
            val line = doc.getLineNumber(caret.offset)
            val lineEnd = doc.getLineEndOffset(line)
            caret.removeSelection()
            caret.moveToOffset(lineEnd)
        }
        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)
    }

    fun toggleSelectMode(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode == HelixMode.SELECT) {
            state.setMode(HelixMode.NORMAL)
            editor.caretModel.runForEachCaret { it.removeSelection() }
        } else {
            state.setMode(HelixMode.SELECT)
        }
    }

    fun enterNormalMode(editor: Editor) {
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.NORMAL)
        state.clearPendingSequence()
    }

    fun selectRegex(editor: Editor, pattern: String): Boolean {
        if (pattern.isEmpty()) return false
        val doc = editor.document
        val text = doc.charsSequence
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            Regex(Regex.escape(pattern))
        }

        val carets = editor.caretModel.allCarets
        val rangesToSearch = carets.map { caret ->
            if (caret.hasSelection()) {
                Pair(caret.selectionStart, caret.selectionEnd)
            } else {
                val offset = caret.offset
                Pair(offset, (offset + 1).coerceAtMost(doc.textLength))
            }
        }

        val allMatches = mutableListOf<Pair<Int, Int>>()
        for ((rangeStart, rangeEnd) in rangesToSearch) {
            if (rangeStart >= rangeEnd) continue
            val targetSub = text.subSequence(rangeStart, rangeEnd).toString()
            var currentPos = 0
            while (currentPos < targetSub.length) {
                val match = regex.find(targetSub, currentPos) ?: break
                val mStart = rangeStart + match.range.first
                val mEnd = rangeStart + match.range.last + 1
                if (mEnd > mStart) {
                    allMatches.add(Pair(mStart, mEnd))
                    currentPos = match.range.last + 1
                } else {
                    currentPos++
                }
            }
        }

        if (allMatches.isEmpty()) return false

        applyCarets(editor, allMatches.distinct().sortedBy { it.first })
        return true
    }

    fun countRegexMatches(editor: Editor, pattern: String): Int {
        if (pattern.isEmpty()) return 0
        val doc = editor.document
        val text = doc.charsSequence
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            try {
                Regex(Regex.escape(pattern))
            } catch (e2: Exception) {
                return 0
            }
        }

        val carets = editor.caretModel.allCarets
        val rangesToSearch = carets.map { caret ->
            if (caret.hasSelection()) {
                Pair(caret.selectionStart, caret.selectionEnd)
            } else {
                val offset = caret.offset
                Pair(offset, (offset + 1).coerceAtMost(doc.textLength))
            }
        }

        var count = 0
        for ((rangeStart, rangeEnd) in rangesToSearch) {
            if (rangeStart >= rangeEnd) continue
            val targetSub = text.subSequence(rangeStart, rangeEnd).toString()
            var currentPos = 0
            while (currentPos < targetSub.length) {
                val match = regex.find(targetSub, currentPos) ?: break
                if (match.range.last >= match.range.first) {
                    count++
                    currentPos = match.range.last + 1
                } else {
                    currentPos++
                }
            }
        }
        return count
    }

    fun splitSelection(editor: Editor, pattern: String): Boolean {
        if (pattern.isEmpty()) return false
        val doc = editor.document
        val text = doc.charsSequence
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            Regex(Regex.escape(pattern))
        }

        val carets = editor.caretModel.allCarets
        val newSelections = mutableListOf<Pair<Int, Int>>()

        for (caret in carets) {
            val rangeStart = if (caret.hasSelection()) caret.selectionStart else caret.offset
            val rangeEnd = if (caret.hasSelection()) caret.selectionEnd else (caret.offset + 1).coerceAtMost(doc.textLength)
            if (rangeStart >= rangeEnd) continue

            val targetSub = text.subSequence(rangeStart, rangeEnd).toString()
            var lastIdx = 0
            for (match in regex.findAll(targetSub)) {
                val segStart = rangeStart + lastIdx
                val segEnd = rangeStart + match.range.first
                if (segEnd > segStart) {
                    newSelections.add(Pair(segStart, segEnd))
                }
                lastIdx = match.range.last + 1
            }
            val finalStart = rangeStart + lastIdx
            if (rangeEnd > finalStart) {
                newSelections.add(Pair(finalStart, rangeEnd))
            }
        }

        if (newSelections.isEmpty()) return false
        applyCarets(editor, newSelections.distinct().sortedBy { it.first })
        return true
    }

    fun applyCarets(editor: Editor, matches: List<Pair<Int, Int>>) {
        if (matches.isEmpty()) return
        val caretStates = matches.map { (start, end) ->
            com.intellij.openapi.editor.CaretState(
                editor.offsetToLogicalPosition(end),
                editor.offsetToLogicalPosition(start),
                editor.offsetToLogicalPosition(end)
            )
        }
        val success = try {
            editor.caretModel.setCaretsAndSelections(caretStates)
            true
        } catch (e: Exception) {
            false
        }
        if (!success || editor.caretModel.caretCount != matches.size) {
            editor.caretModel.removeSecondaryCarets()
            val primary = editor.caretModel.primaryCaret
            val first = matches.first()
            primary.moveToOffset(first.second)
            primary.setSelection(first.first, first.second)
            for (match in matches.drop(1)) {
                val caret = editor.caretModel.addCaret(editor.offsetToVisualPosition(match.second))
                if (caret != null) {
                    caret.moveToOffset(match.second)
                    caret.setSelection(match.first, match.second)
                }
            }
        }
        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
    }

    data class HelixCaretSnapshot(
        val offset: Int,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    fun captureCarets(editor: Editor): List<HelixCaretSnapshot> {
        return editor.caretModel.allCarets.map {
            HelixCaretSnapshot(
                offset = it.offset,
                selectionStart = if (it.hasSelection()) it.selectionStart else it.offset,
                selectionEnd = if (it.hasSelection()) it.selectionEnd else it.offset
            )
        }
    }

    fun restoreCarets(editor: Editor, snapshot: List<HelixCaretSnapshot>) {
        if (snapshot.isEmpty()) return
        val caretStates = snapshot.map {
            com.intellij.openapi.editor.CaretState(
                editor.offsetToLogicalPosition(it.offset),
                editor.offsetToLogicalPosition(it.selectionStart),
                editor.offsetToLogicalPosition(it.selectionEnd)
            )
        }
        val success = try {
            editor.caretModel.setCaretsAndSelections(caretStates)
            true
        } catch (e: Exception) {
            false
        }
        if (!success || editor.caretModel.caretCount != snapshot.size) {
            editor.caretModel.removeSecondaryCarets()
            val primary = editor.caretModel.primaryCaret
            val first = snapshot.first()
            primary.moveToOffset(first.offset)
            primary.setSelection(first.selectionStart, first.selectionEnd)
            for (item in snapshot.drop(1)) {
                val caret = editor.caretModel.addCaret(editor.offsetToVisualPosition(item.offset))
                if (caret != null) {
                    caret.moveToOffset(item.offset)
                    caret.setSelection(item.selectionStart, item.selectionEnd)
                }
            }
        }
        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
    }

    fun previewSearch(
        editor: Editor,
        pattern: String,
        backward: Boolean = false,
        count: Int = 1,
        baseSnapshot: List<HelixCaretSnapshot>
    ): Boolean {
        if (pattern.isEmpty()) {
            restoreCarets(editor, baseSnapshot)
            return false
        }
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = text.length
        if (textLen == 0) {
            restoreCarets(editor, baseSnapshot)
            return false
        }

        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            try {
                Regex(Regex.escape(pattern))
            } catch (e2: Exception) {
                restoreCarets(editor, baseSnapshot)
                return false
            }
        }

        val matches = mutableListOf<Pair<Int, Int>>()
        var currentPos = 0
        while (currentPos < textLen) {
            val match = regex.find(text, currentPos) ?: break
            val start = match.range.first
            val end = match.range.last + 1
            if (end > start) {
                matches.add(Pair(start, end))
                currentPos = end
            } else {
                currentPos++
            }
        }

        if (matches.isEmpty()) {
            restoreCarets(editor, baseSnapshot)
            return false
        }

        val newCaretRanges = mutableListOf<Pair<Int, Int>>()
        val totalMatches = matches.size

        for (caret in baseSnapshot) {
            val targetMatch: Pair<Int, Int>
            if (!backward) {
                val searchFrom = if (caret.selectionEnd > caret.selectionStart) caret.selectionEnd else caret.offset
                val baseIdx = matches.indexOfFirst { it.first >= searchFrom }
                val firstIdx = if (baseIdx >= 0) baseIdx else 0
                val targetIdx = (firstIdx + (count.coerceAtLeast(1) - 1)) % totalMatches
                targetMatch = matches[targetIdx]
            } else {
                val searchFrom = if (caret.selectionEnd > caret.selectionStart) caret.selectionStart else caret.offset
                val baseIdx = matches.indexOfLast { it.second <= searchFrom }
                val firstIdx = if (baseIdx >= 0) baseIdx else (totalMatches - 1)
                val targetIdx = Math.floorMod(firstIdx - (count.coerceAtLeast(1) - 1), totalMatches)
                targetMatch = matches[targetIdx]
            }
            newCaretRanges.add(targetMatch)
        }

        applyCarets(editor, newCaretRanges.distinct().sortedBy { it.first })
        return true
    }

    fun previewSelectRegex(
        editor: Editor,
        pattern: String,
        baseSnapshot: List<HelixCaretSnapshot>
    ): Boolean {
        if (pattern.isEmpty()) {
            restoreCarets(editor, baseSnapshot)
            return false
        }
        val doc = editor.document
        val text = doc.charsSequence
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            try {
                Regex(Regex.escape(pattern))
            } catch (e2: Exception) {
                restoreCarets(editor, baseSnapshot)
                return false
            }
        }

        val rangesToSearch = baseSnapshot.map { caret ->
            if (caret.selectionEnd > caret.selectionStart) {
                Pair(caret.selectionStart, caret.selectionEnd)
            } else {
                val offset = caret.offset
                Pair(offset, (offset + 1).coerceAtMost(doc.textLength))
            }
        }

        val allMatches = mutableListOf<Pair<Int, Int>>()
        for ((rangeStart, rangeEnd) in rangesToSearch) {
            if (rangeStart >= rangeEnd) continue
            val targetSub = text.subSequence(rangeStart, rangeEnd).toString()
            var currentPos = 0
            while (currentPos < targetSub.length) {
                val match = regex.find(targetSub, currentPos) ?: break
                val mStart = rangeStart + match.range.first
                val mEnd = rangeStart + match.range.last + 1
                if (mEnd > mStart) {
                    allMatches.add(Pair(mStart, mEnd))
                    currentPos = match.range.last + 1
                } else {
                    currentPos++
                }
            }
        }

        if (allMatches.isEmpty()) {
            restoreCarets(editor, baseSnapshot)
            return false
        }

        applyCarets(editor, allMatches.distinct().sortedBy { it.first })
        return true
    }

    fun previewSplitRegex(
        editor: Editor,
        pattern: String,
        baseSnapshot: List<HelixCaretSnapshot>
    ): Boolean {
        if (pattern.isEmpty()) {
            restoreCarets(editor, baseSnapshot)
            return false
        }
        val doc = editor.document
        val text = doc.charsSequence
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            try {
                Regex(Regex.escape(pattern))
            } catch (e2: Exception) {
                restoreCarets(editor, baseSnapshot)
                return false
            }
        }

        val rangesToSearch = baseSnapshot.map { caret ->
            if (caret.selectionEnd > caret.selectionStart) {
                Pair(caret.selectionStart, caret.selectionEnd)
            } else {
                val offset = caret.offset
                Pair(offset, (offset + 1).coerceAtMost(doc.textLength))
            }
        }

        val newSelections = mutableListOf<Pair<Int, Int>>()
        for ((rangeStart, rangeEnd) in rangesToSearch) {
            if (rangeStart >= rangeEnd) continue

            val targetSub = text.subSequence(rangeStart, rangeEnd).toString()
            var lastIdx = 0
            for (match in regex.findAll(targetSub)) {
                val segStart = rangeStart + lastIdx
                val segEnd = rangeStart + match.range.first
                if (segEnd > segStart) {
                    newSelections.add(Pair(segStart, segEnd))
                }
                lastIdx = match.range.last + 1
            }
            val finalStart = rangeStart + lastIdx
            if (rangeEnd > finalStart) {
                newSelections.add(Pair(finalStart, rangeEnd))
            }
        }

        if (newSelections.isEmpty()) {
            restoreCarets(editor, baseSnapshot)
            return false
        }

        applyCarets(editor, newSelections.distinct().sortedBy { it.first })
        return true
    }

    fun countRegexMatchesInSnapshot(editor: Editor, pattern: String, baseSnapshot: List<HelixCaretSnapshot>): Int {
        if (pattern.isEmpty()) return 0
        val doc = editor.document
        val text = doc.charsSequence
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            try {
                Regex(Regex.escape(pattern))
            } catch (e2: Exception) {
                return 0
            }
        }

        val rangesToSearch = baseSnapshot.map { caret ->
            if (caret.selectionEnd > caret.selectionStart) {
                Pair(caret.selectionStart, caret.selectionEnd)
            } else {
                val offset = caret.offset
                Pair(offset, (offset + 1).coerceAtMost(doc.textLength))
            }
        }

        var count = 0
        for ((rangeStart, rangeEnd) in rangesToSearch) {
            if (rangeStart >= rangeEnd) continue
            val targetSub = text.subSequence(rangeStart, rangeEnd).toString()
            var currentPos = 0
            while (currentPos < targetSub.length) {
                val match = regex.find(targetSub, currentPos) ?: break
                if (match.range.last >= match.range.first) {
                    count++
                    currentPos = match.range.last + 1
                } else {
                    currentPos++
                }
            }
        }
        return count
    }

    var lastSearchPattern: String? = null
    var lastSearchBackward: Boolean = false

    fun countDocumentRegexMatches(editor: Editor, pattern: String): Int {
        if (pattern.isEmpty()) return 0
        val text = editor.document.charsSequence
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            try {
                Regex(Regex.escape(pattern))
            } catch (e2: Exception) {
                return 0
            }
        }
        var count = 0
        var currentPos = 0
        while (currentPos < text.length) {
            val match = regex.find(text, currentPos) ?: break
            if (match.range.last >= match.range.first) {
                count++
                currentPos = match.range.last + 1
            } else {
                currentPos++
            }
        }
        return count
    }

    fun search(
        editor: Editor,
        pattern: String,
        backward: Boolean = false,
        count: Int = 1,
        updateDirection: Boolean = true
    ): Boolean {
        if (pattern.isEmpty()) return false
        lastSearchPattern = pattern
        if (updateDirection) {
            lastSearchBackward = backward
        }

        val doc = editor.document
        val text = doc.charsSequence
        val textLen = text.length
        if (textLen == 0) return false

        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            Regex(Regex.escape(pattern))
        }

        val matches = mutableListOf<Pair<Int, Int>>()
        var currentPos = 0
        while (currentPos < textLen) {
            val match = regex.find(text, currentPos) ?: break
            val start = match.range.first
            val end = match.range.last + 1
            if (end > start) {
                matches.add(Pair(start, end))
                currentPos = end
            } else {
                currentPos++
            }
        }

        if (matches.isEmpty()) return false

        val newCaretRanges = mutableListOf<Pair<Int, Int>>()
        val totalMatches = matches.size

        for (caret in editor.caretModel.allCarets) {
            val targetMatch: Pair<Int, Int>
            if (!backward) {
                val searchFrom = if (caret.hasSelection()) caret.selectionEnd else caret.offset
                val baseIdx = matches.indexOfFirst { it.first >= searchFrom }
                val firstIdx = if (baseIdx >= 0) baseIdx else 0
                val targetIdx = (firstIdx + (count.coerceAtLeast(1) - 1)) % totalMatches
                targetMatch = matches[targetIdx]
            } else {
                val searchFrom = if (caret.hasSelection()) caret.selectionStart else caret.offset
                val baseIdx = matches.indexOfLast { it.second <= searchFrom }
                val firstIdx = if (baseIdx >= 0) baseIdx else (totalMatches - 1)
                val targetIdx = Math.floorMod(firstIdx - (count.coerceAtLeast(1) - 1), totalMatches)
                targetMatch = matches[targetIdx]
            }
            newCaretRanges.add(targetMatch)
        }

        applyCarets(editor, newCaretRanges.distinct().sortedBy { it.first })
        return true
    }

    fun searchNext(editor: Editor, count: Int = 1): Boolean {
        val pattern = lastSearchPattern ?: return false
        return search(editor, pattern, backward = lastSearchBackward, count = count, updateDirection = false)
    }

    fun searchPrev(editor: Editor, count: Int = 1): Boolean {
        val pattern = lastSearchPattern ?: return false
        return search(editor, pattern, backward = !lastSearchBackward, count = count, updateDirection = false)
    }

    fun searchSelection(editor: Editor): Boolean {
        val primary = editor.caretModel.primaryCaret
        val pattern = if (primary.hasSelection()) {
            Regex.escape(primary.selectedText ?: "")
        } else {
            val doc = editor.document
            val text = doc.charsSequence
            if (text.isEmpty()) return false
            val offset = primary.offset.coerceIn(0, text.length - 1)
            if (!text[offset].isLetterOrDigit() && text[offset] != '_') return false
            var start = offset
            while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
            var end = offset
            while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
            """\b${Regex.escape(text.substring(start, end))}\b"""
        }
        if (pattern.isEmpty()) return false
        return search(editor, pattern, backward = false, count = 1)
    }

    private fun setClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    private fun getClipboard(): String? {
        val transferable = CopyPasteManager.getInstance().contents ?: return null
        return try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                transferable.getTransferData(DataFlavor.stringFlavor) as? String
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
