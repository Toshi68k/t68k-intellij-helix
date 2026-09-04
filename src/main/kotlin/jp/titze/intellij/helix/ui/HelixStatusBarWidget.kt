package jp.titze.intellij.helix.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
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

class HelixStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private var myStatusBar: StatusBar? = null
    private var currentState: HelixEditorState? = null
    private val stateListener: (HelixEditorState) -> Unit = {
        myStatusBar?.updateWidget(ID())
    }

    override fun ID(): String = WIDGET_ID

    override fun install(statusBar: StatusBar) {
        myStatusBar = statusBar
        val busConnection = project.messageBus.connect(this)
        busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                updateCurrentEditor()
            }
        })
        updateCurrentEditor()
    }

    private fun updateCurrentEditor() {
        currentState?.removeListener(stateListener)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor != null) {
            val state = HelixStateManager.getOrCreate(editor)
            currentState = state
            state.addListener(stateListener)
        } else {
            currentState = null
        }
        myStatusBar?.updateWidget(ID())
    }

    override fun dispose() {
        currentState?.removeListener(stateListener)
        currentState = null
        myStatusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val state = currentState ?: return ""
        val count = state.count
        val countStr = if (count != null) " $count" else ""
        val seq = state.pendingSequence
        val prefix = if (seq.isNotEmpty()) " $seq-" else ""
        return "${state.mode.shortCode}$countStr$prefix"
    }

    override fun getAlignment(): Float = 0.5f

    override fun getTooltipText(): String = "Helix Mode: ${currentState?.mode?.displayName ?: "None"}"

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    companion object {
        const val WIDGET_ID = "jp.titze.intellij.helix.status"
    }
}
