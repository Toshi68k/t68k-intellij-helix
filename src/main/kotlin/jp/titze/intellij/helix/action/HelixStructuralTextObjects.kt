package jp.titze.intellij.helix.action

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.ArrayDeque

object HelixStructuralTextObjects {

    private val TEST_ANNOTATIONS = listOf("@Test", "@ParameterizedTest", "@RepeatedTest", "#[test]")

    fun findTestRange(editor: Editor, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val project = editor.project
        val doc = editor.document

        if (project != null) {
            val psiPair = try {
                runReadAction {
                    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction null
                    val offset = caret.offset.coerceIn(0, (doc.textLength - 1).coerceAtLeast(0))
                    var elem: PsiElement? = psiFile.findElementAt(offset)
                    while (elem != null && elem !is PsiFile) {
                        if (isTestMethod(elem, psiFile)) {
                            val fullRange = elem.textRange
                            if (!inside) {
                                return@runReadAction Pair(fullRange.startOffset, fullRange.endOffset)
                            }
                            val bodyPair = findEnclosingBracketsInElement(doc, elem, '{', '}')
                            if (bodyPair != null) {
                                return@runReadAction bodyPair
                            }
                            return@runReadAction Pair(fullRange.startOffset, fullRange.endOffset)
                        }
                        elem = elem.parent
                    }
                    null
                }
            } catch (_: Throwable) {
                null
            }
            if (psiPair != null) return psiPair
        }

        return findTestRangeFallback(doc, caret, inside)
    }

    private fun isTestMethod(elem: PsiElement, psiFile: PsiFile): Boolean {
        val typeName = elem.javaClass.simpleName
        val isMethod = (
            typeName.contains("Method", ignoreCase = true) ||
                typeName.contains("Function", ignoreCase = true)
            ) &&
            !typeName.contains("Body", ignoreCase = true) &&
            !typeName.contains("Call", ignoreCase = true) &&
            !typeName.contains("Expr", ignoreCase = true) &&
            !typeName.contains("List", ignoreCase = true)
        if (!isMethod) return false

        val text = elem.text
        if (TEST_ANNOTATIONS.any { text.contains(it) }) {
            return true
        }

        val name = elem.text.substringBefore('(').trim()
        val isNamedTest = name.contains("test", ignoreCase = true) || name.contains("Test")
        if (isNamedTest) return true

        val fileName = psiFile.name
        return fileName.contains("Test", ignoreCase = true) || fileName.contains("Spec", ignoreCase = true)
    }

    private fun findTestRangeFallback(doc: Document, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val text = doc.charsSequence.toString()
        val offset = caret.offset.coerceIn(0, (doc.textLength - 1).coerceAtLeast(0))
        val testRegex = Regex("""(@Test[\s\S]*?)?(?:fun|def|void)\s+\w+""")
        val matches = testRegex.findAll(text).toList()
        for (match in matches.reversed()) {
            val matchStart = match.range.first
            if (matchStart <= offset) {
                val range = extractFallbackTestRange(text, matchStart, match.range.last, offset, inside)
                if (range != null) return range
            }
        }
        return null
    }

    private fun extractFallbackTestRange(
        text: String,
        matchStart: Int,
        matchLast: Int,
        offset: Int,
        inside: Boolean,
    ): Pair<Int, Int>? {
        val openBrace = text.indexOf('{', matchLast)
        if (openBrace == -1 || openBrace >= text.length) return null
        val closeBrace = findMatchingClosingBrace(text, openBrace)
        if (closeBrace == -1 || offset > closeBrace) return null

        return if (inside) {
            Pair(openBrace + 1, closeBrace)
        } else {
            Pair(matchStart, closeBrace + 1)
        }
    }

    private fun findMatchingClosingBrace(text: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++

                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private data class OpenTag(val name: String, val startOffset: Int, val endOffset: Int)

    fun findXmlElementRange(editor: Editor, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val doc = editor.document
        val text = doc.charsSequence.toString()
        val offset = caret.offset.coerceIn(0, doc.textLength)

        val tagPattern = Regex("""<(/)?([a-zA-Z0-9_\-:]+)(?:\s+[^>]*?)?(/)?>""")
        val stack = ArrayDeque<OpenTag>()
        var bestRange: Pair<Int, Int>? = null
        var bestInsideRange: Pair<Int, Int>? = null
        var bestSpan = Int.MAX_VALUE

        for (match in tagPattern.findAll(text)) {
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2]
            val isSelfClosing = match.groupValues[3] == "/"
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1

            if (isSelfClosing && offset in matchStart..matchEnd) {
                return resolveSelfClosingTagRange(matchStart, matchEnd, tagName, inside)
            }
            if (isSelfClosing) continue

            if (!isClosing) {
                stack.push(OpenTag(tagName, matchStart, matchEnd))
            } else {
                val matched = findMatchingOpenTag(stack, tagName, matchStart, matchEnd, offset)
                if (matched != null && (matchEnd - matched.first.first) < bestSpan) {
                    bestSpan = matchEnd - matched.first.first
                    bestRange = matched.first
                    bestInsideRange = matched.second
                }
            }
        }

        return if (inside) bestInsideRange ?: bestRange else bestRange
    }

    private fun resolveSelfClosingTagRange(
        matchStart: Int,
        matchEnd: Int,
        tagName: String,
        inside: Boolean,
    ): Pair<Int, Int> {
        val innerStart = matchStart + 1 + tagName.length
        val innerEnd = matchEnd - 2
        return if (inside) {
            Pair(innerStart.coerceAtMost(innerEnd), innerEnd)
        } else {
            Pair(matchStart, matchEnd)
        }
    }

    private fun findMatchingOpenTag(
        stack: ArrayDeque<OpenTag>,
        tagName: String,
        matchStart: Int,
        matchEnd: Int,
        offset: Int,
    ): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        while (stack.isNotEmpty()) {
            val open = stack.pop()
            if (open.name.equals(tagName, ignoreCase = true)) {
                if (offset in open.startOffset..matchEnd) {
                    val fullRange = Pair(open.startOffset, matchEnd)
                    val insideRange = Pair(open.endOffset, matchStart)
                    return Pair(fullRange, insideRange)
                }
                break
            }
        }
        return null
    }

    fun findVcsChangeRange(editor: Editor, caret: Caret, inside: Boolean): Pair<Int, Int>? {
        val doc = editor.document
        val offset = caret.offset.coerceIn(0, (doc.textLength - 1).coerceAtLeast(0))

        val project = editor.project
        if (project != null) {
            val caretLine = doc.getLineNumber(offset)
            val lineRange = findVcsTrackerLineRange(project, doc, caretLine)
            if (lineRange != null) {
                val (line1, line2) = lineRange
                val startLine = line1
                val endLine = maxOf(line1, line2 - 1)
                val startOffset = doc.getLineStartOffset(startLine)
                val endOffset = doc.getLineEndOffset(endLine)
                return if (inside) {
                    Pair(startOffset, endOffset)
                } else {
                    val endNl = if (endLine + 1 < doc.lineCount) doc.getLineStartOffset(endLine + 1) else endOffset
                    Pair(startOffset, endNl)
                }
            }
        }

        return findDiffHunkRange(doc, offset, inside)
    }

    private fun findVcsTrackerLineRange(project: Project, doc: Document, caretLine: Int): Pair<Int, Int>? {
        return try {
            val managerClass = Class.forName("com.intellij.openapi.vcs.impl.LineStatusTrackerManager")
            val getInstanceMethod = managerClass.getMethod("getInstance", Project::class.java)
            val manager = getInstanceMethod.invoke(null, project) ?: return null
            val getTrackerMethod = manager.javaClass.getMethod("getLineStatusTracker", Document::class.java)
            val tracker = getTrackerMethod.invoke(manager, doc) ?: return null
            val getRangesMethod = tracker.javaClass.getMethod("getRanges")
            val ranges = getRangesMethod.invoke(tracker) as? List<*> ?: return null
            for (range in ranges) {
                val match = extractTrackerMatchingRange(range, caretLine)
                if (match != null) return match
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractTrackerMatchingRange(range: Any?, caretLine: Int): Pair<Int, Int>? {
        if (range == null) return null
        val line1 = range.javaClass.getMethod("getLine1").invoke(range) as? Int ?: return null
        val line2 = range.javaClass.getMethod("getLine2").invoke(range) as? Int ?: return null
        val matches = (caretLine in line1 until line2) || (line1 == line2 && caretLine == line1)
        return if (matches) Pair(line1, line2) else null
    }

    private fun findDiffHunkRange(doc: Document, offset: Int, inside: Boolean): Pair<Int, Int>? {
        val text = doc.charsSequence.toString()
        val caretLine = doc.getLineNumber(offset)

        var hunkStartLine = -1
        for (line in caretLine downTo 0) {
            val lineStart = doc.getLineStartOffset(line)
            val lineEnd = doc.getLineEndOffset(line)
            val lineStr = text.substring(lineStart, lineEnd)
            if (lineStr.startsWith("@@")) {
                hunkStartLine = line
                break
            }
        }
        if (hunkStartLine == -1) return null

        var hunkEndLine = doc.lineCount - 1
        for (line in hunkStartLine + 1 until doc.lineCount) {
            val lineStart = doc.getLineStartOffset(line)
            val lineEnd = doc.getLineEndOffset(line)
            val lineStr = text.substring(lineStart, lineEnd)
            if (lineStr.startsWith("@@") || lineStr.startsWith("diff --git")) {
                hunkEndLine = line - 1
                break
            }
        }

        val startOffset = doc.getLineStartOffset(hunkStartLine)
        val endOffset = doc.getLineEndOffset(hunkEndLine)

        return if (inside) {
            val contentStartLine = (hunkStartLine + 1).coerceAtMost(hunkEndLine)
            val contentStart = doc.getLineStartOffset(contentStartLine)
            Pair(contentStart, endOffset)
        } else {
            val endWithNl = if (hunkEndLine + 1 < doc.lineCount) doc.getLineStartOffset(hunkEndLine + 1) else endOffset
            Pair(startOffset, endWithNl)
        }
    }

    private fun findEnclosingBracketsInElement(
        doc: Document,
        elem: PsiElement,
        openChar: Char,
        closeChar: Char,
    ): Pair<Int, Int>? {
        val text = doc.charsSequence
        val range = elem.textRange
        val start = range.startOffset
        val end = range.endOffset
        val openIdx = (start until end).firstOrNull { text[it] == openChar } ?: return null
        val closeIdx = (end - 1 downTo openIdx + 1).firstOrNull { text[it] == closeChar } ?: return null
        return Pair(openIdx + 1, closeIdx)
    }
}
