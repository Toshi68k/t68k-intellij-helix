package jp.titze.intellij.helix.action

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

object HelixAstActions {

    private fun isTriviaOrDelimiter(element: PsiElement): Boolean {
        if (element is PsiWhiteSpace) return true
        val text = element.text.trim()
        if (text.isEmpty()) return true
        return text in DELIMITERS
    }

    private val DELIMITERS = setOf(",", ";", "(", ")", "{", "}", "[", "]")

    fun selectPrevSibling(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val doc = editor.document
        if (doc.textLength == 0) return false

        val newCarets = runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction null
            editor.caretModel.allCarets.mapNotNull { caret ->
                val target = findTargetElement(psiFile, doc.textLength, caret) ?: return@mapNotNull null
                val prev = findPreviousSignificantSibling(target)
                if (prev != null) {
                    val range = prev.textRange
                    HelixCaretSnapshot(range.endOffset, range.startOffset, range.endOffset)
                } else {
                    null
                }
            }
        } ?: return false

        return applyCaretSnapshots(editor, newCarets)
    }

    fun selectNextSibling(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val doc = editor.document
        if (doc.textLength == 0) return false

        val newCarets = runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction null
            editor.caretModel.allCarets.mapNotNull { caret ->
                val target = findTargetElement(psiFile, doc.textLength, caret) ?: return@mapNotNull null
                val next = findNextSignificantSibling(target)
                if (next != null) {
                    val range = next.textRange
                    HelixCaretSnapshot(range.endOffset, range.startOffset, range.endOffset)
                } else {
                    null
                }
            }
        } ?: return false

        return applyCaretSnapshots(editor, newCarets)
    }

    fun moveParentNodeStart(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val doc = editor.document
        if (doc.textLength == 0) return false

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        val newCarets = runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction null
            editor.caretModel.allCarets.mapNotNull { caret ->
                val curOffset = caret.offset
                val target = findTargetElement(psiFile, doc.textLength, caret) ?: return@mapNotNull null
                val parent = findParentNodeForStart(target, curOffset) ?: return@mapNotNull null
                val targetOffset = parent.textRange.startOffset.coerceIn(0, doc.textLength)
                val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else targetOffset
                val selStart = if (isSelect) minOf(anchor, targetOffset) else targetOffset
                val selEnd = if (isSelect) maxOf(anchor, targetOffset) else targetOffset
                HelixCaretSnapshot(targetOffset, selStart, selEnd)
            }
        } ?: return false

        return applyCaretSnapshots(editor, newCarets)
    }

    fun moveParentNodeEnd(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val doc = editor.document
        if (doc.textLength == 0) return false

        val state = HelixStateManager.getOrCreate(editor)
        val isSelect = state.mode == HelixMode.SELECT

        val newCarets = runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction null
            editor.caretModel.allCarets.mapNotNull { caret ->
                val curOffset = caret.offset
                val target = findTargetElement(psiFile, doc.textLength, caret) ?: return@mapNotNull null
                val parent = findParentNodeForEnd(target, curOffset) ?: return@mapNotNull null
                val parentEnd = parent.textRange.endOffset.coerceIn(0, doc.textLength)
                val targetOffset = if (isSelect) {
                    parentEnd
                } else {
                    (parentEnd - 1).coerceAtLeast(parent.textRange.startOffset)
                }
                val anchor = if (isSelect && caret.hasSelection()) caret.leadSelectionOffset else targetOffset
                val selStart = if (isSelect) minOf(anchor, targetOffset) else targetOffset
                val selEnd = if (isSelect) maxOf(anchor, targetOffset) else targetOffset
                HelixCaretSnapshot(targetOffset, selStart, selEnd)
            }
        } ?: return false

        return applyCaretSnapshots(editor, newCarets)
    }

    private fun findParentNodeForStart(target: PsiElement, curOffset: Int): PsiElement? {
        var parent = target.parent
        while (parent != null && parent !is PsiFile && parent.textRange.startOffset == curOffset) {
            parent = parent.parent
        }
        if (parent == null || parent is PsiFile) return null
        return parent
    }

    private fun findParentNodeForEnd(target: PsiElement, curOffset: Int): PsiElement? {
        var parent = target.parent
        while (parent != null && parent !is PsiFile && (parent.textRange.endOffset - 1) <= curOffset) {
            parent = parent.parent
        }
        if (parent == null || parent is PsiFile) return null
        return parent
    }

    private fun findNextSignificantSibling(target: PsiElement): PsiElement? {
        var next = target.nextSibling
        while (next != null && isTriviaOrDelimiter(next)) {
            next = next.nextSibling
        }
        if (next != null) return next

        var parentNode = target.parent
        while (parentNode != null && parentNode !is PsiFile &&
            parentNode.textRange.endOffset == target.textRange.endOffset
        ) {
            var candidate = parentNode.nextSibling
            while (candidate != null && isTriviaOrDelimiter(candidate)) {
                candidate = candidate.nextSibling
            }
            if (candidate != null) return candidate
            parentNode = parentNode.parent
        }
        return null
    }

    private fun applyCaretSnapshots(editor: Editor, snapshots: List<HelixCaretSnapshot>): Boolean {
        if (snapshots.isEmpty()) return false
        val caretStates = snapshots.map {
            CaretState(
                editor.offsetToLogicalPosition(it.offset),
                editor.offsetToLogicalPosition(it.selectionStart),
                editor.offsetToLogicalPosition(it.selectionEnd),
            )
        }
        editor.caretModel.setCaretsAndSelections(caretStates)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        return true
    }

    private fun findPreviousSignificantSibling(target: PsiElement): PsiElement? {
        var prev = target.prevSibling
        while (prev != null && isTriviaOrDelimiter(prev)) {
            prev = prev.prevSibling
        }
        if (prev != null) return prev

        var parentNode = target.parent
        while (parentNode != null && parentNode !is PsiFile &&
            parentNode.textRange.startOffset == target.textRange.startOffset
        ) {
            var candidate = parentNode.prevSibling
            while (candidate != null && isTriviaOrDelimiter(candidate)) {
                candidate = candidate.prevSibling
            }
            if (candidate != null) return candidate
            parentNode = parentNode.parent
        }
        return null
    }

    fun selectAllSiblings(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val doc = editor.document
        if (doc.textLength == 0) return false

        val newCarets = runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction null
            val collected = mutableListOf<HelixCaretSnapshot>()
            for (caret in editor.caretModel.allCarets) {
                collected.addAll(collectSiblingsForCaret(psiFile, doc.textLength, caret))
            }
            collected.distinctBy { it.selectionStart to it.selectionEnd }.sortedBy { it.selectionStart }
        } ?: return false

        return applyCaretSnapshots(editor, newCarets)
    }

    private fun collectSiblingsForCaret(psiFile: PsiFile, textLength: Int, caret: Caret): List<HelixCaretSnapshot> {
        val target = findTargetElement(psiFile, textLength, caret) ?: return emptyList()
        val parent = target.parent ?: return emptyList()
        if (parent is PsiFile) return emptyList()

        return getSignificantChildren(parent).map { sibling ->
            val range = sibling.textRange
            HelixCaretSnapshot(range.endOffset, range.startOffset, range.endOffset)
        }
    }

    fun selectAllChildren(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val doc = editor.document
        if (doc.textLength == 0) return false

        val newCarets = runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return@runReadAction null
            val collected = mutableListOf<HelixCaretSnapshot>()
            for (caret in editor.caretModel.allCarets) {
                collected.addAll(collectChildrenForCaret(psiFile, doc.textLength, caret))
            }
            collected.distinctBy { it.selectionStart to it.selectionEnd }.sortedBy { it.selectionStart }
        } ?: return false

        return applyCaretSnapshots(editor, newCarets)
    }

    private fun collectChildrenForCaret(psiFile: PsiFile, textLength: Int, caret: Caret): List<HelixCaretSnapshot> {
        val target = findTargetElement(psiFile, textLength, caret) ?: return emptyList()
        return getSignificantChildren(target).map { child ->
            val range = child.textRange
            HelixCaretSnapshot(range.endOffset, range.startOffset, range.endOffset)
        }
    }

    private fun findTargetElement(psiFile: PsiFile, textLength: Int, caret: Caret): PsiElement? {
        if (caret.hasSelection()) {
            val start = caret.selectionStart
            val end = caret.selectionEnd
            val exact = PsiTreeUtil.findElementOfClassAtRange(psiFile, start, end, PsiElement::class.java)
            if (exact != null) return exact

            val l1 = psiFile.findElementAt(start.coerceIn(0, (textLength - 1).coerceAtLeast(0)))
            val l2 = psiFile.findElementAt((end - 1).coerceIn(0, (textLength - 1).coerceAtLeast(0)))
            var common = if (l1 != null && l2 != null) PsiTreeUtil.findCommonParent(l1, l2) else l1
            while (common != null && common !is PsiFile && isEncompassed(common, start, end)) {
                common = common.parent
            }
            return common
        }

        val offset = caret.offset.coerceIn(0, (textLength - 1).coerceAtLeast(0))
        var elem = psiFile.findElementAt(offset)
        if (elem is PsiWhiteSpace) {
            elem = elem.prevSibling ?: elem.nextSibling ?: elem
        }
        while (canClimbToParent(elem, psiFile)) {
            elem = elem?.parent
        }
        return elem
    }

    private fun canClimbToParent(elem: PsiElement?, psiFile: PsiFile): Boolean {
        val parent = elem?.parent ?: return false
        if (parent is PsiFile) return false
        val sameStart = parent.textRange.startOffset == elem.textRange.startOffset
        val smallerThanFile = parent.textRange.endOffset < psiFile.textRange.endOffset
        return sameStart && smallerThanFile
    }

    private fun isEncompassed(element: PsiElement, start: Int, end: Int): Boolean {
        val range = element.textRange
        return range.startOffset > start || range.endOffset < end
    }

    private fun getSignificantChildren(element: PsiElement): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        var child = element.firstChild
        while (child != null) {
            if (!isTriviaOrDelimiter(child) && child.textLength > 0) {
                result.add(child)
            }
            child = child.nextSibling
        }
        return result
    }
}
