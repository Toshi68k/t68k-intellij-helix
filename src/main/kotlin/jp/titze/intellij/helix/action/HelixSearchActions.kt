package jp.titze.intellij.helix.action

import com.intellij.openapi.editor.Editor

object HelixSearchActions {

    var lastSearchPattern: String? = null
    var lastSearchBackward: Boolean = false

    fun previewSearch(
        editor: Editor,
        pattern: String,
        backward: Boolean = false,
        count: Int = 1,
        baseSnapshot: List<HelixCaretSnapshot>,
    ): Boolean {
        if (pattern.isEmpty()) {
            HelixCaretUtils.restoreCarets(editor, baseSnapshot)
            return false
        }
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = text.length
        if (textLen == 0) {
            HelixCaretUtils.restoreCarets(editor, baseSnapshot)
            return false
        }

        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            try {
                Regex(Regex.escape(pattern))
            } catch (e2: Exception) {
                HelixCaretUtils.restoreCarets(editor, baseSnapshot)
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
            HelixCaretUtils.restoreCarets(editor, baseSnapshot)
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

        HelixCaretUtils.applyCarets(editor, newCaretRanges.distinct().sortedBy { it.first })
        return true
    }

    fun search(
        editor: Editor,
        pattern: String,
        backward: Boolean = false,
        count: Int = 1,
        updateDirection: Boolean = true,
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

        HelixCaretUtils.applyCarets(editor, newCaretRanges.distinct().sortedBy { it.first })
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
}
