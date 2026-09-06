package jp.titze.intellij.helix.action

import com.intellij.openapi.editor.Editor

object HelixRegexActions {

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

        HelixCaretUtils.applyCarets(editor, allMatches.distinct().sortedBy { it.first })
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
            val rangeEnd = if (caret.hasSelection()) {
                caret.selectionEnd
            } else {
                (caret.offset + 1).coerceAtMost(
                    doc.textLength,
                )
            }
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
        HelixCaretUtils.applyCarets(editor, newSelections.distinct().sortedBy { it.first })
        return true
    }

    fun previewSelectRegex(editor: Editor, pattern: String, baseSnapshot: List<HelixCaretSnapshot>): Boolean {
        if (pattern.isEmpty()) {
            HelixCaretUtils.restoreCarets(editor, baseSnapshot)
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
                HelixCaretUtils.restoreCarets(editor, baseSnapshot)
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
            HelixCaretUtils.restoreCarets(editor, baseSnapshot)
            return false
        }

        HelixCaretUtils.applyCarets(editor, allMatches.distinct().sortedBy { it.first })
        return true
    }

    fun previewSplitRegex(editor: Editor, pattern: String, baseSnapshot: List<HelixCaretSnapshot>): Boolean {
        if (pattern.isEmpty()) {
            HelixCaretUtils.restoreCarets(editor, baseSnapshot)
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
                HelixCaretUtils.restoreCarets(editor, baseSnapshot)
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
            HelixCaretUtils.restoreCarets(editor, baseSnapshot)
            return false
        }

        HelixCaretUtils.applyCarets(editor, newSelections.distinct().sortedBy { it.first })
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
}
