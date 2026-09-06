package jp.titze.intellij.helix.jumplist

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager

data class HelixJumpEntry(
    val virtualFile: VirtualFile,
    val anchorMarker: RangeMarker,
    val caretMarker: RangeMarker,
    val line: Int,
    val column: Int,
    val previewText: String
) {
    val isValid: Boolean
        get() = virtualFile.isValid && anchorMarker.isValid && caretMarker.isValid

    val anchorOffset: Int
        get() = if (anchorMarker.isValid) anchorMarker.startOffset else 0

    val caretOffset: Int
        get() = if (caretMarker.isValid) caretMarker.startOffset else 0
}

@Service(Service.Level.PROJECT)
class HelixJumpListService(private val project: Project) {

    private val entries = mutableListOf<HelixJumpEntry>()
    private var currentIndex: Int = -1
    private val maxEntries = 100

    @Synchronized
    fun getEntries(): List<HelixJumpEntry> {
        return entries.toList()
    }

    @Synchronized
    fun getCurrentIndex(): Int = currentIndex

    @Synchronized
    fun clear() {
        entries.clear()
        currentIndex = -1
    }

    @Synchronized
    fun recordCurrent(editor: Editor, force: Boolean = false): Boolean {
        if (editor.isDisposed) return false
        val doc = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(doc) ?: return false

        val caret = editor.caretModel.primaryCaret
        val caretOffset = caret.offset.coerceIn(0, doc.textLength)
        val anchorOffset =
            (if (caret.hasSelection()) caret.leadSelectionOffset else caretOffset).coerceIn(0, doc.textLength)

        val lineNum = doc.getLineNumber(caretOffset)
        val lineStart = doc.getLineStartOffset(lineNum)
        val lineEnd = doc.getLineEndOffset(lineNum)
        val line = lineNum + 1
        val column = caretOffset - lineStart + 1
        val preview = doc.getText(TextRange(lineStart, lineEnd)).trim()

        if (!force && currentIndex in entries.indices) {
            val last = entries[currentIndex]
            if (last.virtualFile == virtualFile && last.line == line) {
                return false
            }
        }

        // If we previously jumped back, truncate the forward entries
        if (currentIndex in 0 until entries.size - 1) {
            val toRemove = entries.size - 1 - currentIndex
            repeat(toRemove) {
                entries.removeAt(entries.size - 1)
            }
        }

        val anchorMarker = doc.createRangeMarker(anchorOffset, anchorOffset).apply { isGreedyToRight = true }
        val caretMarker = doc.createRangeMarker(caretOffset, caretOffset).apply { isGreedyToRight = true }

        val entry = HelixJumpEntry(
            virtualFile = virtualFile,
            anchorMarker = anchorMarker,
            caretMarker = caretMarker,
            line = line,
            column = column,
            previewText = preview
        )

        entries.add(entry)
        if (entries.size > maxEntries) {
            entries.removeAt(0)
        }
        currentIndex = entries.size - 1

        if (force) {
            showNotification("Jump list: saved (line $line)")
        }

        return true
    }

    @Synchronized
    fun jumpBackward(editor: Editor, count: Int = 1): Boolean {
        if (entries.isEmpty()) return false

        // If at the latest recorded jump, check if the user moved away from it.
        // If so, record the current position first so we can jump forward back to it later.
        if (currentIndex == entries.size - 1) {
            val doc = editor.document
            val vFile = FileDocumentManager.getInstance().getFile(doc)
            val currentLine = doc.getLineNumber(editor.caretModel.primaryCaret.offset) + 1
            val topEntry = entries[currentIndex]
            if (vFile != topEntry.virtualFile || currentLine != topEntry.line) {
                recordCurrent(editor, force = false)
            }
        }

        var targetIndex = currentIndex - count
        if (targetIndex < 0) {
            targetIndex = 0
        }

        while (targetIndex in entries.indices) {
            val entry = entries[targetIndex]
            if (entry.isValid) {
                currentIndex = targetIndex
                return navigateTo(entry)
            } else {
                entries.removeAt(targetIndex)
                if (targetIndex >= entries.size) {
                    targetIndex = entries.size - 1
                }
            }
        }

        return false
    }

    @Synchronized
    fun jumpForward(editor: Editor, count: Int = 1): Boolean {
        if (entries.isEmpty()) return false
        if (currentIndex >= entries.size - 1) return false

        var targetIndex = currentIndex + count
        if (targetIndex >= entries.size) {
            targetIndex = entries.size - 1
        }

        while (targetIndex in entries.indices) {
            val entry = entries[targetIndex]
            if (entry.isValid) {
                currentIndex = targetIndex
                return navigateTo(entry)
            } else {
                entries.removeAt(targetIndex)
                targetIndex = targetIndex.coerceAtMost(entries.size - 1)
            }
        }

        return false
    }

    @Synchronized
    fun jumpTo(index: Int): Boolean {
        if (index !in entries.indices) return false
        val entry = entries[index]
        if (!entry.isValid) return false
        currentIndex = index
        return navigateTo(entry)
    }

    private fun navigateTo(entry: HelixJumpEntry): Boolean {
        if (!entry.virtualFile.isValid) return false
        val fileEditorManager = FileEditorManager.getInstance(project)
        val descriptor = OpenFileDescriptor(project, entry.virtualFile, entry.caretOffset)
        val targetEditor = fileEditorManager.openTextEditor(descriptor, true) ?: return false

        val caret = targetEditor.caretModel.primaryCaret
        val maxLen = targetEditor.document.textLength
        val anchor = entry.anchorOffset.coerceIn(0, maxLen)
        val targetCaret = entry.caretOffset.coerceIn(0, maxLen)

        caret.moveToOffset(targetCaret)
        if (anchor != targetCaret) {
            caret.setSelection(anchor, targetCaret)
        } else {
            caret.removeSelection()
        }
        targetEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        return true
    }

    private fun showNotification(message: String) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        statusBar?.info = message
    }

    companion object {
        fun getInstance(project: Project): HelixJumpListService {
            return project.getService(HelixJumpListService::class.java)
        }
    }
}
