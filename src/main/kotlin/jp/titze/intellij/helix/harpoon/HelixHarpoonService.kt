// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.harpoon

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
import jp.titze.intellij.helix.keymap.HelixKeyHandler

data class HelixHarpoonEntry(
    val virtualFile: VirtualFile,
    val marker: RangeMarker,
    val line: Int,
    val column: Int,
    val previewText: String,
) {
    val isValid: Boolean
        get() = virtualFile.isValid && marker.isValid

    val offset: Int
        get() = if (marker.isValid) marker.startOffset else 0
}

@Service(Service.Level.PROJECT)
class HelixHarpoonService(private val project: Project) {

    private val slots = Array<HelixHarpoonEntry?>(SLOT_COUNT) { null }

    @Synchronized
    fun getSlots(): List<HelixHarpoonEntry?> = slots.toList()

    @Synchronized
    fun getSlot(index: Int): HelixHarpoonEntry? {
        if (index !in 0 until SLOT_COUNT) return null
        val entry = slots[index]
        return if (entry != null && entry.isValid) entry else null
    }

    @Synchronized
    fun setSlot(index: Int, editor: Editor): Boolean {
        if (index !in 0 until SLOT_COUNT || editor.isDisposed) return false
        val doc = editor.document
        val vFile = FileDocumentManager.getInstance().getFile(doc) ?: return false

        val caret = editor.caretModel.primaryCaret
        val offset = caret.offset.coerceIn(0, doc.textLength)
        val marker = doc.createRangeMarker(offset, offset).apply {
            isGreedyToLeft = true
            isGreedyToRight = true
        }

        val lineNum = doc.getLineNumber(offset)
        val lineStart = doc.getLineStartOffset(lineNum)
        val lineEnd = doc.getLineEndOffset(lineNum)
        val line = lineNum + 1
        val col = offset - lineStart + 1
        val preview = doc.getText(TextRange(lineStart, lineEnd)).trim()

        slots[index] = HelixHarpoonEntry(
            virtualFile = vFile,
            marker = marker,
            line = line,
            column = col,
            previewText = preview,
        )
        return true
    }

    @Synchronized
    fun pinCurrent(editor: Editor): Int? {
        if (editor.isDisposed) return null
        val doc = editor.document
        val vFile = FileDocumentManager.getInstance().getFile(doc) ?: return null

        val existingIdx = slots.indexOfFirst { it != null && it.isValid && it.virtualFile == vFile }
        val targetIdx = if (existingIdx >= 0) {
            existingIdx
        } else {
            val emptyIdx = slots.indexOfFirst { it == null || !it.isValid }
            if (emptyIdx >= 0) emptyIdx else 0
        }

        return if (setSlot(targetIdx, editor)) targetIdx + 1 else null
    }

    @Synchronized
    fun clearSlot(index: Int): Boolean {
        if (index !in 0 until SLOT_COUNT) return false
        slots[index]?.marker?.dispose()
        slots[index] = null
        return true
    }

    @Synchronized
    fun clearAll() {
        for (i in 0 until SLOT_COUNT) {
            slots[i]?.marker?.dispose()
            slots[i] = null
        }
    }

    @Synchronized
    fun swapSlots(i: Int, j: Int): Boolean {
        if (i !in 0 until SLOT_COUNT || j !in 0 until SLOT_COUNT || i == j) return false
        val temp = slots[i]
        slots[i] = slots[j]
        slots[j] = temp
        return true
    }

    fun jumpToSlot(index: Int, editor: Editor): Boolean {
        val entry = getSlot(index) ?: return false
        HelixKeyHandler.recordJump(editor)

        val targetOffset = entry.offset
        val descriptor = OpenFileDescriptor(project, entry.virtualFile, targetOffset)
        descriptor.navigate(true)
        val targetEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return false

        targetEditor.caretModel.moveToOffset(targetOffset)
        targetEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        return true
    }

    companion object {
        const val SLOT_COUNT = 5

        fun getInstance(project: Project): HelixHarpoonService = project.getService(HelixHarpoonService::class.java)
    }
}
