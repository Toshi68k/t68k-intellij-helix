package jp.titze.intellij.helix.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor

object HelixInsertTracker {

    private val currentBuffer = StringBuilder()
    var lastInsertedText: String? = null
        private set
    private var isRecording = false

    fun startInsert() {
        currentBuffer.clear()
        isRecording = true
    }

    fun recordChar(c: Char) {
        if (isRecording) {
            currentBuffer.append(c)
        }
    }

    fun recordText(text: String) {
        if (isRecording) {
            currentBuffer.append(text)
        }
    }

    fun finishInsert() {
        if (isRecording) {
            if (currentBuffer.isNotEmpty()) {
                lastInsertedText = currentBuffer.toString()
            }
            isRecording = false
            currentBuffer.clear()
        }
    }

    fun reset() {
        currentBuffer.clear()
        lastInsertedText = null
        isRecording = false
    }

    fun repeatLastInsert(editor: Editor, count: Int = 1): Boolean {
        val text = lastInsertedText ?: return false
        if (text.isEmpty()) return false
        val project = editor.project
        val doc = editor.document
        val fullText = if (count > 1) text.repeat(count) else text

        WriteCommandAction.runWriteCommandAction(project) {
            val carets = editor.caretModel.allCarets.sortedByDescending {
                if (it.hasSelection()) it.selectionStart else it.offset
            }
            for (caret in carets) {
                if (caret.hasSelection()) {
                    val start = caret.selectionStart
                    val end = caret.selectionEnd
                    doc.replaceString(start, end, fullText)
                    caret.removeSelection()
                    caret.moveToOffset(start + fullText.length)
                } else {
                    val offset = caret.offset.coerceIn(0, doc.textLength)
                    doc.insertString(offset, fullText)
                    caret.moveToOffset(offset + fullText.length)
                }
            }
        }
        return true
    }
}
