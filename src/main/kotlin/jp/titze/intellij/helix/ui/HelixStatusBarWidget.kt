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
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixEditorState
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
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

    var macroRecordingProvider: () -> Boolean = { isMacroRecording() }
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

    fun updateComponent() {
        val newIcon: Icon? = if (HelixSettings.instance.colorModes) {
            val state = currentState
            val editor = currentEditor
            if (editor != null && HelixEditorEligibility.isActive(editor) && state != null) {
                MODE_PILL_ICONS[state.mode]
            } else {
                null
            }
        } else {
            null
        }

        val newText = computeText()
        val newTooltip = computeTooltipText()

        val iconChanged = panel.icon != newIcon
        val textChanged = panel.text != newText
        val tooltipChanged = panel.toolTipText != newTooltip

        if (iconChanged || textChanged || tooltipChanged) {
            panel.icon = newIcon
            panel.text = newText
            panel.toolTipText = newTooltip
            myStatusBar?.updateWidget(ID())
        }
    }

    private class ModePillIcon(val mode: HelixMode) : Icon {
        override fun getIconWidth(): Int = JBUI.scale(36)
        override fun getIconHeight(): Int = JBUI.scale(16)

        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            if (g == null) return
            val g2 = g.create() as? Graphics2D ?: return
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                val bg = when (mode) {
                    HelixMode.NORMAL -> HelixTheme.MODE_NOR_BG
                    HelixMode.INSERT -> HelixTheme.MODE_INS_BG
                    HelixMode.SELECT -> HelixTheme.MODE_SEL_BG
                }
                val fg = when (mode) {
                    HelixMode.NORMAL -> HelixTheme.MODE_NOR_FG
                    HelixMode.INSERT -> HelixTheme.MODE_INS_FG
                    HelixMode.SELECT -> HelixTheme.MODE_SEL_FG
                }

                val w = iconWidth
                val h = iconHeight
                val arc = JBUI.scale(6)

                g2.color = bg
                g2.fillRoundRect(x, y, w, h, arc, arc)

                val font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scaleFontSize(10f).toFloat())
                g2.font = font
                g2.color = fg
                val fm = g2.fontMetrics
                val text = mode.shortCode
                val strW = fm.stringWidth(text)
                val textX = x + (w - strW) / 2
                val textY = y + (h - fm.height) / 2 + fm.ascent
                g2.drawString(text, textX, textY)
            } finally {
                g2.dispose()
            }
        }

        override fun equals(other: Any?): Boolean = other is ModePillIcon && other.mode == mode
        override fun hashCode(): Int = mode.hashCode()
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

        val modeStr = if (HelixSettings.instance.colorModes) "" else state.mode.shortCode
        val rest = "$recStr$selStr$regStr$countStr$prefix"
        return if (modeStr.isEmpty()) rest.trimStart() else "$modeStr$rest"
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

        private val MODE_PILL_ICONS: Map<HelixMode, ModePillIcon> =
            HelixMode.entries.associateWith { ModePillIcon(it) }

        private val macroManagerClass: Class<*>? by lazy {
            try {
                Class.forName("com.intellij.ide.actionMacro.ActionMacroManager")
            } catch (_: Throwable) {
                null
            }
        }

        private val getInstanceMethod by lazy {
            try {
                macroManagerClass?.getMethod("getInstance")
            } catch (_: Throwable) {
                null
            }
        }

        private val isRecordingMethod by lazy {
            try {
                macroManagerClass?.getMethod("isRecording")
            } catch (_: Throwable) {
                null
            }
        }

        fun isMacroRecording(): Boolean = try {
            val manager = getInstanceMethod?.invoke(null)
            if (manager != null) {
                isRecordingMethod?.invoke(manager) as? Boolean ?: false
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }

        fun updateAllWidgets() {
            ProjectManager.getInstance().openProjects.forEach { project ->
                if (!project.isDisposed) {
                    val statusBar = WindowManager.getInstance().getStatusBar(project)
                    val widget = statusBar?.getWidget(WIDGET_ID) as? HelixStatusBarWidget
                    widget?.updateComponent()
                }
            }
        }
    }
}
