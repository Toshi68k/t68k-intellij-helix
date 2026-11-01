// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.state.HelixEditorState
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

class HelixStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = HelixStatusBarWidget.WIDGET_ID
    override fun getDisplayName(): String = HelixBundle.message("status.widget.displayName")
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = HelixStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class HelixStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private var myStatusBar: StatusBar? = null
    private var currentState: HelixEditorState? = null
    private var currentEditor: Editor? = null

    private val panel = object : TextPanel.WithIconAndArrows() {
        init {
            border = JBUI.CurrentTheme.StatusBar.Widget.border()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    handleClick()
                }
            })
        }
    }

    init {
        updateComponent()
    }

    fun getText(): String = panel.text.orEmpty()

    fun getTooltipText(): String? = panel.toolTipText

    fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        handleClick()
    }

    internal fun handleClick() {
        val editor = currentEditor ?: FileEditorManager.getInstance(project).selectedTextEditor ?: return
        if (editor.isDisposed) return
        val state = currentState ?: HelixStateManager.getOrCreate(editor)
        if (state.selectedRegister != null) {
            HelixRegistersPopup.show(editor)
        } else {
            HelixCommandPopup.show(editor)
        }
    }

    var macroRecordingProvider: () -> Boolean = {
        try {
            val clazz = Class.forName("com.intellij.ide.actionMacro.ActionMacroManager")
            val manager = clazz.getMethod("getInstance").invoke(null)
            clazz.getMethod("isRecording").invoke(manager) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }
        set(value) {
            field = value
            updateComponent()
        }

    private val stateListener: (HelixEditorState) -> Unit = {
        updateComponent()
    }

    private val caretListener = object : CaretListener {
        override fun caretAdded(event: CaretEvent) {
            updateComponent()
        }

        override fun caretRemoved(event: CaretEvent) {
            updateComponent()
        }
    }

    override fun ID(): String = WIDGET_ID

    override fun getComponent(): JComponent = panel

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

        if (editor != null && !editor.isDisposed && HelixEditorEligibility.isActive(editor)) {
            val state = HelixStateManager.getOrCreate(editor)
            currentState = state
            currentEditor = editor
            state.addListener(stateListener)
            editor.caretModel.addCaretListener(caretListener)
        } else {
            currentState = null
            currentEditor = editor
        }
        updateComponent()
    }

    private fun updateCurrentEditor() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        attachEditor(editor)
    }

    private fun updateComponent() {
        panel.text = computeText()
        panel.toolTipText = computeTooltipText()
        myStatusBar?.updateWidget(ID())
    }

    private fun computeText(): String {
        val editor = currentEditor
        if (editor == null || !HelixEditorEligibility.isActive(editor)) {
            return "OFF"
        }
        val state = currentState ?: return "OFF"
        val caretCount = editor.caretModel.caretCount
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

    private fun computeTooltipText(): String {
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

    override fun dispose() {
        currentState?.removeListener(stateListener)
        currentEditor?.caretModel?.removeCaretListener(caretListener)
        currentState = null
        currentEditor = null
        myStatusBar = null
    }

    companion object {
        const val WIDGET_ID = "jp.titze.intellij.helix.status"
    }
}
