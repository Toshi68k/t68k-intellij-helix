// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.harpoon

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import jp.titze.intellij.helix.action.HelixReadAction
import jp.titze.intellij.helix.keymap.HelixKeyHandler

class HelixHarpoonEntry(
    val virtualFile: VirtualFile,
    val marker: RangeMarker,
    line: Int,
    column: Int,
    val previewText: String,
    val fileUrl: String = virtualFile.url,
) {
    private val initialLine: Int = line
    private val initialColumn: Int = column

    val line: Int
        get() = if (marker.isValid) {
            HelixReadAction.compute {
                val doc = marker.document
                doc.getLineNumber(marker.startOffset) + 1
            }
        } else {
            initialLine
        }

    val column: Int
        get() = if (marker.isValid) {
            HelixReadAction.compute {
                val doc = marker.document
                val lineNum = doc.getLineNumber(marker.startOffset)
                marker.startOffset - doc.getLineStartOffset(lineNum) + 1
            }
        } else {
            initialColumn
        }

    val isValid: Boolean
        get() = virtualFile.isValid && marker.isValid

    val offset: Int
        get() = if (marker.isValid) marker.startOffset else 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HelixHarpoonEntry) return false
        return virtualFile == other.virtualFile && offset == other.offset
    }

    override fun hashCode(): Int = 31 * virtualFile.hashCode() + offset.hashCode()
}

@Service(Service.Level.PROJECT)
@State(
    name = "jp.titze.intellij.helix.harpoon.HelixHarpoonService",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class HelixHarpoonService(private val project: Project) :
    PersistentStateComponent<HelixHarpoonState>,
    Disposable {

    private val slots = Array<HelixHarpoonEntry?>(SLOT_COUNT) { null }
    private val dormantStates = Array<HarpoonSlotState?>(SLOT_COUNT) { null }

    @Synchronized
    fun getSlots(): List<HelixHarpoonEntry?> {
        for (i in 0 until SLOT_COUNT) {
            hydrateSlotIfNeeded(i)
        }
        return slots.toList()
    }

    @Synchronized
    fun getSlot(index: Int): HelixHarpoonEntry? {
        if (index !in 0 until SLOT_COUNT) return null
        hydrateSlotIfNeeded(index)
        val entry = slots[index]
        return if (entry != null && entry.isValid) entry else null
    }

    @Synchronized
    fun getDormantSlot(index: Int): HarpoonSlotState? {
        if (index !in 0 until SLOT_COUNT) return null
        val dormant = dormantStates[index]
        if (dormant != null) return dormant
        val entry = slots[index]
        if (entry != null && !entry.isValid) {
            return HarpoonSlotState().apply {
                slotIndex = index
                fileUrl = entry.virtualFile.url
                line = entry.line
                column = entry.column
                previewText = entry.previewText
            }
        }
        return null
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

        slots[index]?.marker?.dispose()
        slots[index] = HelixHarpoonEntry(
            virtualFile = vFile,
            marker = marker,
            line = line,
            column = col,
            previewText = preview,
        )
        dormantStates[index] = null
        return true
    }

    @Synchronized
    fun pinCurrent(editor: Editor): Int? {
        if (editor.isDisposed) return null
        val doc = editor.document
        val vFile = FileDocumentManager.getInstance().getFile(doc) ?: return null

        val existingIdx = (0 until SLOT_COUNT).firstOrNull { idx ->
            hydrateSlotIfNeeded(idx)
            val entry = slots[idx]
            entry != null && entry.isValid && entry.virtualFile == vFile
        } ?: -1

        val targetIdx = if (existingIdx >= 0) {
            existingIdx
        } else {
            val emptyIdx = (0 until SLOT_COUNT).firstOrNull { idx -> isSlotEmpty(idx) } ?: -1
            if (emptyIdx >= 0) emptyIdx else 0
        }

        return if (setSlot(targetIdx, editor)) targetIdx + 1 else null
    }

    private fun isSlotEmpty(index: Int): Boolean {
        hydrateSlotIfNeeded(index)
        val entry = slots[index]
        val isEntryEmpty = entry == null || !entry.isValid
        return isEntryEmpty && getDormantSlot(index) == null
    }

    @Synchronized
    fun clearSlot(index: Int): Boolean {
        if (index !in 0 until SLOT_COUNT) return false
        slots[index]?.marker?.dispose()
        slots[index] = null
        dormantStates[index] = null
        return true
    }

    @Synchronized
    fun clearAll() {
        for (i in 0 until SLOT_COUNT) {
            slots[i]?.marker?.dispose()
            slots[i] = null
            dormantStates[i] = null
        }
    }

    @Synchronized
    fun swapSlots(i: Int, j: Int): Boolean {
        if (i !in 0 until SLOT_COUNT || j !in 0 until SLOT_COUNT || i == j) return false
        hydrateSlotIfNeeded(i)
        hydrateSlotIfNeeded(j)
        val tempSlot = slots[i]
        slots[i] = slots[j]
        slots[j] = tempSlot

        val tempDormant = dormantStates[i]
        dormantStates[i] = dormantStates[j]
        dormantStates[j] = tempDormant
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

    @Synchronized
    override fun getState(): HelixHarpoonState {
        val state = HelixHarpoonState()
        for (i in 0 until SLOT_COUNT) {
            val entry = slots[i]
            if (entry != null && entry.isValid) {
                state.slots.add(
                    HarpoonSlotState().apply {
                        slotIndex = i
                        fileUrl = entry.virtualFile.url
                        line = entry.line
                        column = entry.column
                        previewText = entry.previewText
                    },
                )
            } else {
                val dormant = getDormantSlot(i)
                if (dormant != null && dormant.fileUrl.isNotEmpty()) {
                    state.slots.add(dormant)
                }
            }
        }
        return state
    }

    @Synchronized
    override fun loadState(state: HelixHarpoonState) {
        for (i in 0 until SLOT_COUNT) {
            slots[i]?.marker?.dispose()
            slots[i] = null
            dormantStates[i] = null
        }
        for (slotState in state.slots) {
            val idx = slotState.slotIndex
            if (idx in 0 until SLOT_COUNT && slotState.fileUrl.isNotEmpty()) {
                dormantStates[idx] = slotState
            }
        }
    }

    private fun hydrateSlotIfNeeded(index: Int) {
        if (index !in 0 until SLOT_COUNT) return
        val existing = slots[index]
        if (existing != null) {
            if (existing.isValid) return
            dormantStates[index] = HarpoonSlotState().apply {
                slotIndex = index
                fileUrl = existing.fileUrl
                line = existing.line
                column = existing.column
                previewText = existing.previewText
            }
            existing.marker.dispose()
            slots[index] = null
        }

        val dormant = dormantStates[index] ?: return
        if (dormant.fileUrl.isEmpty()) return

        val vFile = HelixReadAction.compute {
            VirtualFileManager.getInstance().findFileByUrl(dormant.fileUrl)
        } ?: return

        if (!vFile.isValid) return

        HelixReadAction.compute {
            val doc = FileDocumentManager.getInstance().getDocument(vFile) ?: return@compute
            val lineCount = doc.lineCount
            val targetLine = (dormant.line - 1).coerceIn(0, (lineCount - 1).coerceAtLeast(0))
            val lineStart = doc.getLineStartOffset(targetLine)
            val lineEnd = doc.getLineEndOffset(targetLine)
            val targetOffset = (lineStart + (dormant.column - 1)).coerceIn(lineStart, lineEnd)

            val marker = doc.createRangeMarker(targetOffset, targetOffset).apply {
                isGreedyToLeft = true
                isGreedyToRight = true
            }

            val preview = dormant.previewText.ifEmpty {
                doc.getText(TextRange(lineStart, lineEnd)).trim()
            }

            slots[index]?.marker?.dispose()
            slots[index] = HelixHarpoonEntry(
                virtualFile = vFile,
                marker = marker,
                line = dormant.line,
                column = dormant.column,
                previewText = preview,
            )
            dormantStates[index] = null
        }
    }

    override fun dispose() {
        clearAll()
    }

    companion object {
        const val SLOT_COUNT = 5

        fun getInstance(project: Project): HelixHarpoonService = project.getService(HelixHarpoonService::class.java)
    }
}
