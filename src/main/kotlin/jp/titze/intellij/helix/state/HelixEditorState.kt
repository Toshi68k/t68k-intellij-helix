package jp.titze.intellij.helix.state

import com.intellij.openapi.editor.Editor

class HelixEditorState(val editor: Editor) {
    var mode: HelixMode = HelixMode.NORMAL
        private set

    private val pendingBuffer = StringBuilder()
    private val listeners = mutableListOf<(HelixEditorState) -> Unit>()

    var yankRegister: String? = null
    var isYankLineWise: Boolean = false

    val pendingSequence: String
        get() = pendingBuffer.toString()

    fun setMode(newMode: HelixMode) {
        if (mode != newMode) {
            mode = newMode
            clearPendingSequence()
            notifyListeners()
        }
    }

    fun appendKey(ch: Char) {
        pendingBuffer.append(ch)
        notifyListeners()
    }

    fun clearPendingSequence() {
        if (pendingBuffer.isNotEmpty()) {
            pendingBuffer.clear()
            notifyListeners()
        }
    }

    fun addListener(listener: (HelixEditorState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (HelixEditorState) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            listener(this)
        }
    }
}
