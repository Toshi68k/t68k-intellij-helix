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
        jp.titze.intellij.helix.ui.HelixPromptHistory.add(
            jp.titze.intellij.helix.ui.HelixPromptCategory.SEARCH,
            pattern,
        )

        val doc = editor.document
        val text = doc.charsSequence
        val textLen = text.length
        if (textLen == 0) return false

        val regex = try {
            Regex(pattern)
        } catch (_: Exception) {
            Regex(Regex.escape(pattern))
        }

        val matches = collectMatches(regex, text, textLen)
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
        val state = jp.titze.intellij.helix.state.HelixStateManager.getOrCreate(editor)
        return if (state.mode == jp.titze.intellij.helix.state.HelixMode.SELECT) {
            extendSearch(editor, pattern, backward = lastSearchBackward, count = count)
        } else {
            search(editor, pattern, backward = lastSearchBackward, count = count, updateDirection = false)
        }
    }

    fun searchPrev(editor: Editor, count: Int = 1): Boolean {
        val pattern = lastSearchPattern ?: return false
        val state = jp.titze.intellij.helix.state.HelixStateManager.getOrCreate(editor)
        return if (state.mode == jp.titze.intellij.helix.state.HelixMode.SELECT) {
            extendSearch(editor, pattern, backward = !lastSearchBackward, count = count)
        } else {
            search(editor, pattern, backward = !lastSearchBackward, count = count, updateDirection = false)
        }
    }

    private fun extendSearch(editor: Editor, pattern: String, backward: Boolean, count: Int): Boolean {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = text.length
        if (textLen == 0) return false

        val regex = try {
            Regex(pattern)
        } catch (_: Exception) {
            Regex(Regex.escape(pattern))
        }

        val matches = collectMatches(regex, text, textLen)
        if (matches.isEmpty()) return false

        val existingRanges = editor.caretModel.allCarets.map {
            Pair(
                if (it.hasSelection()) it.selectionStart else it.offset,
                if (it.hasSelection()) it.selectionEnd else it.offset,
            )
        }

        val primary = editor.caretModel.primaryCaret
        val targetMatch = findNextUnselectedMatch(matches, existingRanges, primary, backward, count) ?: return false

        val combined = (existingRanges + listOf(targetMatch)).distinct().sortedBy { it.first }
        HelixCaretUtils.applyCarets(editor, combined)
        return true
    }

    private fun collectMatches(regex: Regex, text: CharSequence, textLen: Int): List<Pair<Int, Int>> {
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
        return matches
    }

    private fun findNextUnselectedMatch(
        matches: List<Pair<Int, Int>>,
        existing: List<Pair<Int, Int>>,
        primary: com.intellij.openapi.editor.Caret,
        backward: Boolean,
        count: Int,
    ): Pair<Int, Int>? {
        val existingSet = existing.toSet()
        val available = matches.filter { it !in existingSet }
        val pool = if (available.isNotEmpty()) available else matches
        val total = pool.size
        if (total == 0) return null

        val steps = (count.coerceAtLeast(1) - 1)
        return if (!backward) {
            val searchFrom = if (primary.hasSelection()) primary.selectionEnd else primary.offset
            val baseIdx = pool.indexOfFirst { it.first >= searchFrom }
            val firstIdx = if (baseIdx >= 0) baseIdx else 0
            pool[(firstIdx + steps) % total]
        } else {
            val searchFrom = if (primary.hasSelection()) primary.selectionStart else primary.offset
            val baseIdx = pool.indexOfLast { it.second <= searchFrom }
            val firstIdx = if (baseIdx >= 0) baseIdx else (total - 1)
            pool[Math.floorMod(firstIdx - steps, total)]
        }
    }

    fun searchSelection(editor: Editor, detectWordBoundaries: Boolean = true): Boolean {
        val primary = editor.caretModel.primaryCaret
        val pattern = if (primary.hasSelection()) {
            val selected = primary.selectedText ?: ""
            if (detectWordBoundaries && isWord(selected)) {
                """\b${Regex.escape(selected)}\b"""
            } else {
                Regex.escape(selected)
            }
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
            val word = text.substring(start, end)
            if (detectWordBoundaries) """\b${Regex.escape(word)}\b""" else Regex.escape(word)
        }
        if (pattern.isEmpty()) return false
        return search(editor, pattern, backward = false, count = 1)
    }

    private fun isWord(s: String): Boolean = s.isNotEmpty() && s.all { it.isLetterOrDigit() || it == '_' }
}
