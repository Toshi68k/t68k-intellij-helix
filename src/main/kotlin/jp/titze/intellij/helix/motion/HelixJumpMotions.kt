package jp.titze.intellij.helix.motion

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixSurroundActions
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixJumpMotions {

    /**
     * [p / ]p: Move to previous/next paragraph boundary (empty line)
     */
    fun moveParagraph(editor: Editor, forward: Boolean, count: Int = 1) {
        val doc = editor.document
        val textLen = doc.textLength
        if (textLen == 0) return

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else caret.offset
            var curLine = doc.getLineNumber(caret.offset)
            val lineCount = doc.lineCount

            repeat(count.coerceAtLeast(1)) {
                if (forward) {
                    if (curLine < lineCount - 1) {
                        if (!isLineBlank(doc, curLine)) {
                            while (curLine < lineCount - 1 && !isLineBlank(doc, curLine)) {
                                curLine++
                            }
                        } else {
                            while (curLine < lineCount - 1 && isLineBlank(doc, curLine)) {
                                curLine++
                            }
                            while (curLine < lineCount - 1 && !isLineBlank(doc, curLine)) {
                                curLine++
                            }
                        }
                    }
                } else {
                    if (curLine > 0) {
                        if (!isLineBlank(doc, curLine)) {
                            while (curLine > 0 && !isLineBlank(doc, curLine)) {
                                curLine--
                            }
                        } else {
                            while (curLine > 0 && isLineBlank(doc, curLine)) {
                                curLine--
                            }
                            while (curLine > 0 && !isLineBlank(doc, curLine)) {
                                curLine--
                            }
                        }
                    }
                }
            }

            val targetOffset = doc.getLineStartOffset(curLine.coerceIn(0, lineCount - 1))
            HelixMotionUtils.applyMotion(caret, anchor, targetOffset, isSelect)
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    private fun isLineBlank(doc: Document, line: Int): Boolean {
        if (line < 0 || line >= doc.lineCount) return true
        val start = doc.getLineStartOffset(line)
        val end = doc.getLineEndOffset(line)
        val chars = doc.charsSequence
        for (i in start until end) {
            if (!chars[i].isWhitespace()) return false
        }
        return true
    }

    /**
     * [Space / ]Space: Add empty line above/below without entering insert mode
     */
    fun addNewline(editor: Editor, below: Boolean, count: Int = 1) {
        val project = editor.project
        val doc = editor.document
        val totalCount = count.coerceAtLeast(1)

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending { it.offset }
            val addedText = "\n".repeat(totalCount)
            for (caret in carets) {
                val line = doc.getLineNumber(caret.offset)
                if (below) {
                    val lineEnd = doc.getLineEndOffset(line)
                    doc.insertString(lineEnd, "\n" + "\n".repeat(totalCount - 1))
                } else {
                    val lineStart = doc.getLineStartOffset(line)
                    doc.insertString(lineStart, addedText)
                }
            }
        }
    }

    /**
     * [c / ]c: Move to previous/next comment (selects comment block)
     */
    fun moveComment(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        var ranges = getMatchingRangesFromPsi(editor) { elem ->
            elem is PsiComment || elem.javaClass.simpleName.contains("Comment", ignoreCase = true)
        }

        if (ranges.isEmpty()) {
            ranges = findLineCommentRanges(editor.document)
        }

        return navigateToRange(editor, ranges, forward, count)
    }

    /**
     * [f / ]f: Move to previous/next function or method (selects function block)
     */
    fun moveFunction(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        val ranges = getMatchingRangesFromPsi(editor) { elem ->
            val name = elem.javaClass.simpleName
            val isFuncCandidate = name.contains("Method", ignoreCase = true) ||
                name.contains("Function", ignoreCase = true) ||
                name.contains("Constructor", ignoreCase = true)
            isFuncCandidate &&
                !name.contains("Literal", ignoreCase = true) &&
                !name.contains("Lambda", ignoreCase = true) &&
                !name.contains("Body", ignoreCase = true) &&
                !name.contains("Call", ignoreCase = true) &&
                !name.contains("Expr", ignoreCase = true) &&
                !name.contains("Reference", ignoreCase = true) &&
                !name.contains("List", ignoreCase = true) &&
                !name.contains("Parameter", ignoreCase = true) &&
                !name.contains("Type", ignoreCase = true) &&
                !name.contains("Argument", ignoreCase = true) &&
                !name.contains("Block", ignoreCase = true) &&
                !name.contains("Statement", ignoreCase = true)
        }

        val moved = navigateToRange(editor, ranges, forward, count)
        if (!moved) {
            val action = if (forward) "MethodDown" else "MethodUp"
            repeat(count.coerceAtLeast(1)) {
                HelixActionDelegate.executeAction(action, editor)
            }
            return true
        }
        return true
    }

    /**
     * [t / ]t: Move to previous/next class or type (selects class block)
     */
    fun moveType(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        var ranges = getMatchingRangesFromPsi(editor) { elem ->
            val name = elem.javaClass.simpleName
            val isTypeCandidate = (
                name.contains("Class", ignoreCase = true) ||
                    name.contains("Interface", ignoreCase = true) ||
                    name.contains("Struct", ignoreCase = true) ||
                    name.contains("Trait", ignoreCase = true) ||
                    name.contains("Enum", ignoreCase = true) ||
                    name.contains("Record", ignoreCase = true) ||
                    name.contains("Object", ignoreCase = true) ||
                    name.contains("TypeAlias", ignoreCase = true)
                )

            isTypeCandidate &&
                !name.contains("Literal", ignoreCase = true) &&
                !name.contains("Destruct", ignoreCase = true) &&
                !name.contains("Component", ignoreCase = true) &&
                !name.contains("Body", ignoreCase = true) &&
                !name.contains("Initializer", ignoreCase = true) &&
                !name.contains("List", ignoreCase = true) &&
                !name.contains("Header", ignoreCase = true) &&
                !name.contains("Access", ignoreCase = true) &&
                !name.contains("Expr", ignoreCase = true) &&
                !name.contains("Reference", ignoreCase = true) &&
                !name.contains("TypeElement", ignoreCase = true) &&
                !name.contains("Parameter", ignoreCase = true) &&
                !name.contains("Constant", ignoreCase = true) &&
                !name.contains("Entry", ignoreCase = true) &&
                !name.contains("Argument", ignoreCase = true) &&
                !name.contains("Statement", ignoreCase = true) &&
                !name.contains("Block", ignoreCase = true)
        }

        if (ranges.isEmpty()) {
            ranges = findClassRangesRegex(editor.document)
        }

        return navigateToRange(editor, ranges, forward, count)
    }

    /**
     * [a / ]a: Move to previous/next parameter (selects parameter block)
     */
    fun moveParameter(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        val ranges = getMatchingRangesFromPsi(editor) { elem ->
            val name = elem.javaClass.simpleName
            name.contains("Parameter", ignoreCase = true) &&
                !name.contains("List", ignoreCase = true) &&
                !name.contains("Type", ignoreCase = true)
        }

        return navigateToRange(editor, ranges, forward, count)
    }

    /**
     * [T / ]T: Move to previous/next test method (selects test block)
     */
    fun moveTest(editor: Editor, forward: Boolean, count: Int = 1): Boolean {
        val ranges = getMatchingRangesFromPsi(editor) { elem ->
            val name = elem.javaClass.simpleName
            val isMethodOrFunc =
                (name.contains("Method", ignoreCase = true) || name.contains("Function", ignoreCase = true)) &&
                    !name.contains("Body", ignoreCase = true) &&
                    !name.contains("Call", ignoreCase = true) &&
                    !name.contains("Expr", ignoreCase = true) &&
                    !name.contains("List", ignoreCase = true)
            if (isMethodOrFunc) {
                val text = elem.text
                text.contains("@Test") ||
                    text.contains("fun test", ignoreCase = true) ||
                    text.contains("def test", ignoreCase = true) ||
                    text.contains("void test", ignoreCase = true)
            } else {
                false
            }
        }

        val moved = navigateToRange(editor, ranges, forward, count)
        if (!moved) {
            HelixActionDelegate.executeAction("GotoTest", editor)
        }
        return true
    }

    /**
     * [d / ]d / [D / ]D: Diagnostic error navigation
     */
    fun moveDiagnostic(editor: Editor, forward: Boolean, count: Int = 1, toEnd: Boolean = false): Boolean {
        if (toEnd) {
            if (forward) {
                editor.caretModel.primaryCaret.moveToOffset(editor.document.textLength)
                HelixActionDelegate.executeAction("GotoPreviousError", editor)
            } else {
                editor.caretModel.primaryCaret.moveToOffset(0)
                HelixActionDelegate.executeAction("GotoNextError", editor)
            }
        } else {
            val action = if (forward) "GotoNextError" else "GotoPreviousError"
            repeat(count.coerceAtLeast(1)) {
                HelixActionDelegate.executeAction(action, editor)
            }
        }
        return true
    }

    /**
     * [g / ]g / [G / ]G: VCS change marker navigation
     */
    fun moveChange(editor: Editor, forward: Boolean, count: Int = 1, toEnd: Boolean = false): Boolean {
        if (toEnd) {
            if (forward) {
                if (!HelixActionDelegate.executeAction("VcsShowLastChangeMarker", editor)) {
                    editor.caretModel.primaryCaret.moveToOffset(editor.document.textLength)
                    HelixActionDelegate.executeAction("VcsShowPrevChangeMarker", editor)
                }
            } else {
                if (!HelixActionDelegate.executeAction("VcsShowFirstChangeMarker", editor)) {
                    editor.caretModel.primaryCaret.moveToOffset(0)
                    HelixActionDelegate.executeAction("VcsShowNextChangeMarker", editor)
                }
            }
        } else {
            val action = if (forward) "VcsShowNextChangeMarker" else "VcsShowPrevChangeMarker"
            repeat(count.coerceAtLeast(1)) {
                HelixActionDelegate.executeAction(action, editor)
            }
        }
        return true
    }

    private fun getMatchingRangesFromPsi(editor: Editor, predicate: (PsiElement) -> Boolean): List<TextRange> {
        val project = editor.project ?: return emptyList()
        val doc = editor.document
        return try {
            runReadAction {
                val psiFile =
                    PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction emptyList()
                collectMatchingRanges(psiFile, predicate)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun collectMatchingRanges(psiFile: PsiFile, predicate: (PsiElement) -> Boolean): List<TextRange> {
        val result = mutableListOf<TextRange>()
        fun dfs(element: PsiElement) {
            var child = element.firstChild
            while (child != null) {
                if (predicate(child)) {
                    val range = child.textRange
                    if (range != null && range.length > 0) {
                        result.add(range)
                    }
                }
                dfs(child)
                child = child.nextSibling
            }
        }
        dfs(psiFile)
        return result.distinctBy { it.startOffset to it.endOffset }.sortedBy { it.startOffset }
    }

    private fun findLineCommentRanges(doc: Document): List<TextRange> {
        val prefixes = listOf("//", "/*", "#", "--", "<!--")
        val ranges = mutableListOf<TextRange>()
        for (line in 0 until doc.lineCount) {
            val start = doc.getLineStartOffset(line)
            val end = doc.getLineEndOffset(line)
            val text = doc.charsSequence.subSequence(start, end).toString()
            for (prefix in prefixes) {
                val idx = text.indexOf(prefix)
                if (idx >= 0) {
                    ranges.add(TextRange(start + idx, end))
                    break
                }
            }
        }
        return ranges
    }

    private fun findClassRangesRegex(doc: Document): List<TextRange> {
        val modifiers = "(?:(?:public|private|protected|internal|abstract|final|open|data|sealed|value)\\s+)*"
        val types = "(?:class|interface|struct|trait|enum(?:\\s+class)?|record|object|typealias)"
        val regex = Regex(
            "^\\s*$modifiers$types\\s+([A-Za-z0-9_]+)",
            RegexOption.MULTILINE,
        )
        val text = doc.charsSequence
        return regex.findAll(text).map { match ->
            val start = match.range.first
            val line = doc.getLineNumber(start)
            val lineEnd = doc.getLineEndOffset(line)
            TextRange(start, lineEnd)
        }.toList()
    }

    private fun navigateToRange(editor: Editor, ranges: List<TextRange>, forward: Boolean, count: Int): Boolean {
        if (ranges.isEmpty()) return false
        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT
        var anyMoved = false

        HelixMotionUtils.runForEachCaret(editor) { caret ->
            val curOffset = caret.offset

            val targetRange = if (forward) {
                val refOffset = if (caret.hasSelection()) maxOf(curOffset, caret.selectionEnd) else curOffset
                val candidates = ranges.filter { it.startOffset >= refOffset }
                val idx = (count - 1).coerceAtMost(candidates.size - 1)
                if (candidates.isNotEmpty()) candidates[idx] else null
            } else {
                val refOffset = if (caret.hasSelection()) minOf(curOffset, caret.selectionStart) else curOffset
                val candidates = ranges.filter { it.startOffset < refOffset }.sortedByDescending { it.startOffset }
                val idx = (count - 1).coerceAtMost(candidates.size - 1)
                if (candidates.isNotEmpty()) candidates[idx] else null
            }

            if (targetRange != null) {
                if (isSelect) {
                    val anchor = if (caret.hasSelection()) caret.leadSelectionOffset else curOffset
                    val targetOffset = if (forward) targetRange.endOffset else targetRange.startOffset
                    caret.moveToOffset(targetOffset)
                    caret.setSelection(anchor, targetOffset)
                } else {
                    caret.moveToOffset(targetRange.startOffset)
                    caret.setSelection(targetRange.startOffset, targetRange.endOffset)
                }
                anyMoved = true
            }
        }

        if (anyMoved) {
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
        return anyMoved
    }

    /**
     * mm: Move to matching bracket (paren, brace, square bracket, angle bracket)
     */
    fun matchBrackets(editor: Editor, count: Int = 1): Boolean {
        val doc = editor.document
        val text = doc.charsSequence
        val textLen = text.length
        if (textLen == 0) return false

        editor.project?.let { HelixJumpListService.getInstance(it).recordCurrent(editor) }

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        val action = ActionManager.getInstance().getAction("EditorMatchBrace") as? EditorAction
        val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)

        var anyMoved = false

        repeat(count) {
            HelixMotionUtils.runForEachCaret(editor) { caret ->
                val curOffset = caret.offset
                val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else curOffset

                var targetOffset: Int? = null

                if (action != null) {
                    val beforeOffset = caret.offset
                    try {
                        runReadAction {
                            action.handler.execute(editor, caret, dataContext)
                        }
                    } catch (_: Throwable) {
                    }
                    if (caret.offset != beforeOffset) {
                        targetOffset = caret.offset
                    }
                }

                if (targetOffset == null) {
                    targetOffset = findMatchingBracketFallback(text, textLen, curOffset)
                }

                if (targetOffset != null && targetOffset != curOffset) {
                    HelixMotionUtils.applyMotion(caret, anchor, targetOffset, isSelect)
                    anyMoved = true
                } else if (isSelect && targetOffset != null) {
                    caret.setSelection(anchor, targetOffset)
                    anyMoved = true
                }
            }
        }

        if (anyMoved) {
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
        return anyMoved
    }

    private fun findMatchingBracketFallback(text: CharSequence, textLen: Int, offset: Int): Int? {
        val charAtOffset = if (offset in 0 until textLen) text[offset] else null
        val match1 = findPairMatch(text, offset, charAtOffset)
        if (match1 != null) return match1

        if (offset > 0) {
            val charBefore = text[offset - 1]
            val match2 = findPairMatch(text, offset - 1, charBefore)
            if (match2 != null) return match2
        }

        return null
    }

    private fun findPairMatch(text: CharSequence, index: Int, ch: Char?): Int? = when (ch) {
        '(' -> HelixSurroundActions.findMatchingClose(text, index, '(', ')')
        '[' -> HelixSurroundActions.findMatchingClose(text, index, '[', ']')
        '{' -> HelixSurroundActions.findMatchingClose(text, index, '{', '}')
        '<' -> HelixSurroundActions.findMatchingClose(text, index, '<', '>')
        ')' -> HelixSurroundActions.findMatchingOpen(text, index, '(', ')')
        ']' -> HelixSurroundActions.findMatchingOpen(text, index, '[', ']')
        '}' -> HelixSurroundActions.findMatchingOpen(text, index, '{', '}')
        '>' -> HelixSurroundActions.findMatchingOpen(text, index, '<', '>')
        else -> null
    }
}
