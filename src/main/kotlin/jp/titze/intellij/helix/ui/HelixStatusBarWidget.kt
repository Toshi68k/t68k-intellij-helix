package jp.titze.intellij.helix.ui

import com.intellij.ide.actionMacro.ActionMacroManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.state.HelixEditorState
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.event.MouseEvent

class HelixStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = HelixStatusBarWidget.WIDGET_ID
    override fun getDisplayName(): String = "Helix Mode"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = HelixStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class HelixStatusBarWidget(private val project: Project) :
    StatusBarWidget,
    StatusBarWidget.TextPresentation {

    private var myStatusBar: StatusBar? = null
    private var currentState: HelixEditorState? = null
    private var currentEditor: Editor? = null

    var macroRecordingProvider: () -> Boolean = {
        try {
            ActionMacroManager.getInstance().isRecording
        } catch (_: Throwable) {
            false
        }
    }

    private val stateListener: (HelixEditorState) -> Unit = {
        myStatusBar?.updateWidget(ID())
    }

    private val caretListener = object : CaretListener {
        override fun caretAdded(event: CaretEvent) {
            myStatusBar?.updateWidget(ID())
        }

        override fun caretRemoved(event: CaretEvent) {
            myStatusBar?.updateWidget(ID())
        }
    }

    override fun ID(): String = WIDGET_ID

    override fun install(statusBar: StatusBar) {
        myStatusBar = statusBar
        val busConnection = project.messageBus.connect(this)
        busConnection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    updateCurrentEditor()
                }
            },
        )
        updateCurrentEditor()
    }

    fun attachEditor(editor: Editor?) {
        currentState?.removeListener(stateListener)
        currentEditor?.caretModel?.removeCaretListener(caretListener)

        if (editor != null && !editor.isDisposed) {
            val state = HelixStateManager.getOrCreate(editor)
            currentState = state
            currentEditor = editor
            state.addListener(stateListener)
            editor.caretModel.addCaretListener(caretListener)
        } else {
            currentState = null
            currentEditor = null
        }
        myStatusBar?.updateWidget(ID())
    }

    private fun updateCurrentEditor() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        attachEditor(editor)
    }

    override fun dispose() {
        currentState?.removeListener(stateListener)
        currentEditor?.caretModel?.removeCaretListener(caretListener)
        currentState = null
        currentEditor = null
        myStatusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val state = currentState ?: return ""
        val editor = currentEditor
        val caretCount = editor?.caretModel?.caretCount ?: 1
        val recStr = if (macroRecordingProvider()) " [REC]" else ""
        val selStr = if (caretCount > 1) " $caretCount sel" else ""
        val reg = state.selectedRegister
        val regStr = if (reg != null) " reg: $reg" else ""
        val count = state.count
        val countStr = if (count != null) " $count" else ""
        val seq = state.pendingSequence
        val prefix = if (seq.isNotEmpty()) " $seq-" else ""
        return "${state.mode.shortCode}$recStr$selStr$regStr$countStr$prefix"
    }

    override fun getAlignment(): Float = 0.5f

    override fun getTooltipText(): String {
        val state = currentState ?: return "Helix: Inactive"
        val parts = mutableListOf<String>()
        parts.add("Helix Mode: ${state.mode.displayName}")
        val editor = currentEditor
        val caretCount = editor?.caretModel?.caretCount ?: 1
        if (caretCount > 1) {
            parts.add("$caretCount selections")
        }
        val reg = state.selectedRegister
        if (reg != null) {
            parts.add("Register: \"$reg")
        }
        if (macroRecordingProvider()) {
            parts.add("Recording Macro")
        }
        val count = state.count
        if (count != null) {
            parts.add("Count: $count")
        }
        if (state.pendingSequence.isNotEmpty()) {
            parts.add("Pending: ${state.pendingSequence}-")
        }
        return parts.joinToString(" | ")
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        val editor = currentEditor ?: FileEditorManager.getInstance(project).selectedTextEditor ?: return@Consumer
        if (editor.isDisposed) return@Consumer
        val state = currentState ?: HelixStateManager.getOrCreate(editor)
        if (state.selectedRegister != null) {
            HelixRegistersPopup.show(editor)
        } else {
            HelixCommandPopup.show(editor)
        }
    }

    companion object {
        const val WIDGET_ID = "jp.titze.intellij.helix.status"
    }
}
