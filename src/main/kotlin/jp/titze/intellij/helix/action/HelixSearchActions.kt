// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

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

        val regex = compileRegex(pattern)

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

        for ((offset, selectionStart, selectionEnd) in baseSnapshot) {
            val searchFrom = if (!backward) {
                if (selectionEnd > selectionStart) selectionEnd else offset
            } else {
                if (selectionEnd > selectionStart) selectionStart else offset
            }
            val targetMatch = getTargetMatch(matches, searchFrom, backward, count)
            if (targetMatch != null) {
                newCaretRanges.add(targetMatch)
            }
        }

        if (newCaretRanges.isEmpty()) {
            HelixCaretUtils.restoreCarets(editor, baseSnapshot)
            return false
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

        val regex = compileRegex(pattern)

        val matches = collectMatches(regex, text, textLen)
        if (matches.isEmpty()) return false

        val newCaretRanges = mutableListOf<Pair<Int, Int>>()

        for (caret in editor.caretModel.allCarets) {
            val searchFrom = if (!backward) {
                if (caret.hasSelection()) caret.selectionEnd else caret.offset
            } else {
                if (caret.hasSelection()) caret.selectionStart else caret.offset
            }
            val targetMatch = getTargetMatch(matches, searchFrom, backward, count)
            if (targetMatch != null) {
                newCaretRanges.add(targetMatch)
            }
        }

        if (newCaretRanges.isEmpty()) return false

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

        val regex = compileRegex(pattern)

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

    internal fun compileRegex(pattern: String): Regex {
        val options = if (jp.titze.intellij.helix.settings.HelixEffectiveSettings.searchSmartCase &&
            !pattern.any { it.isUpperCase() }
        ) {
            setOf(RegexOption.IGNORE_CASE)
        } else {
            emptySet()
        }
        return try {
            Regex(pattern, options)
        } catch (_: Exception) {
            try {
                Regex(Regex.escape(pattern), options)
            } catch (_: Exception) {
                Regex(Regex.escape(pattern))
            }
        }
    }

    private fun getTargetMatch(
        matches: List<Pair<Int, Int>>,
        searchFrom: Int,
        backward: Boolean,
        count: Int,
    ): Pair<Int, Int>? {
        val totalMatches = matches.size
        if (totalMatches == 0) return null
        val wrap = jp.titze.intellij.helix.settings.HelixEffectiveSettings.searchWrapAround
        val steps = count.coerceAtLeast(1) - 1

        return if (!backward) {
            val baseIdx = matches.indexOfFirst { it.first >= searchFrom }
            if (baseIdx >= 0) {
                val targetIdx = baseIdx + steps
                if (wrap) {
                    matches[targetIdx % totalMatches]
                } else {
                    if (targetIdx < totalMatches) matches[targetIdx] else null
                }
            } else {
                if (wrap) {
                    matches[steps % totalMatches]
                } else {
                    null
                }
            }
        } else {
            val baseIdx = matches.indexOfLast { it.second <= searchFrom }
            if (baseIdx >= 0) {
                val targetIdx = baseIdx - steps
                if (wrap) {
                    matches[Math.floorMod(targetIdx, totalMatches)]
                } else {
                    if (targetIdx >= 0) matches[targetIdx] else null
                }
            } else {
                if (wrap) {
                    matches[Math.floorMod(totalMatches - 1 - steps, totalMatches)]
                } else {
                    null
                }
            }
        }
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
        val pool = available.ifEmpty { matches }
        val total = pool.size
        if (total == 0) return null

        val searchFrom = if (!backward) {
            if (primary.hasSelection()) primary.selectionEnd else primary.offset
        } else {
            if (primary.hasSelection()) primary.selectionStart else primary.offset
        }
        return getTargetMatch(pool, searchFrom, backward, count)
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
