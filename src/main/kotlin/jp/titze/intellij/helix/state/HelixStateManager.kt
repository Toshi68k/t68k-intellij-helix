// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.state

import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.util.Key
import jp.titze.intellij.helix.editor.HelixLineNumberManager
import jp.titze.intellij.helix.settings.HelixCursorShape
import jp.titze.intellij.helix.settings.HelixEffectiveSettings
import jp.titze.intellij.helix.ui.HelixTheme
import java.awt.Color

object HelixStateManager {
    private val HELIX_STATE_KEY = Key.create<HelixEditorState>("HELIX_STATE_KEY")
    private val ORIGINAL_CARET_COLOR_STORED = Key.create<Boolean>("ORIGINAL_CARET_COLOR_STORED")
    private val ORIGINAL_CARET_COLOR_KEY = Key.create<Color>("ORIGINAL_CARET_COLOR_KEY")

    const val THICKNESS_BLOCK = 1.0f
    const val THICKNESS_BAR = 0.25f
    const val THICKNESS_UNDERLINE = 0.20f
    const val THICKNESS_HIDDEN = 0.0f

    fun resolveHostEditor(editor: Editor): Editor {
        var cur = editor
        while (cur is EditorWindow) {
            cur = cur.delegate
        }
        return cur
    }

    fun get(editor: Editor): HelixEditorState? = resolveHostEditor(editor).getUserData(HELIX_STATE_KEY)

    fun getOrCreate(editor: Editor): HelixEditorState {
        val hostEditor = resolveHostEditor(editor)
        var state = hostEditor.getUserData(HELIX_STATE_KEY)
        if (state == null) {
            synchronized(hostEditor) {
                state = hostEditor.getUserData(HELIX_STATE_KEY)
                if (state == null) {
                    val newState = HelixEditorState(hostEditor)
                    hostEditor.putUserData(HELIX_STATE_KEY, newState)
                    newState.addListener { updateCursor(hostEditor, it.mode) }
                    val listener = object : CaretListener {
                        override fun caretAdded(event: CaretEvent) {
                            if (hostEditor.isDisposed) return
                            val s = get(hostEditor) ?: return
                            updateCursor(hostEditor, s.mode)
                        }
                    }
                    val disposable = hostEditor as? Disposable
                    if (disposable != null) {
                        hostEditor.caretModel.addCaretListener(listener, disposable)
                    } else {
                        hostEditor.caretModel.addCaretListener(listener)
                    }
                    updateCursor(hostEditor, newState.mode)
                    state = newState
                }
            }
        }
        return state!!
    }

    fun updateCursor(editor: Editor, mode: HelixMode) {
        val hostEditor = resolveHostEditor(editor)
        val app = ApplicationManager.getApplication()
        if (app != null && !app.isDispatchThread) {
            app.invokeLater({
                if (!hostEditor.isDisposed) {
                    updateCursor(hostEditor, mode)
                }
            }, ModalityState.any())
            return
        }

        if (hostEditor.isDisposed) return
        val shape = when (mode) {
            HelixMode.NORMAL -> HelixEffectiveSettings.cursorShapeNormal
            HelixMode.INSERT -> HelixEffectiveSettings.cursorShapeInsert
            HelixMode.SELECT -> HelixEffectiveSettings.cursorShapeSelect
        }

        hostEditor.settings.isBlockCursor = (shape == HelixCursorShape.BLOCK)

        val tintColor = if (HelixEffectiveSettings.cursorTint) {
            when (mode) {
                HelixMode.NORMAL -> HelixTheme.MODE_NOR_CARET
                HelixMode.INSERT -> HelixTheme.MODE_INS_CARET
                HelixMode.SELECT -> HelixTheme.MODE_SEL_CARET
            }
        } else {
            null
        }

        val visualShape = when (shape) {
            HelixCursorShape.BLOCK -> CaretVisualAttributes.Shape.BLOCK
            HelixCursorShape.BAR -> CaretVisualAttributes.Shape.BAR
            HelixCursorShape.UNDERLINE -> CaretVisualAttributes.Shape.UNDERSCORE
            HelixCursorShape.HIDDEN -> CaretVisualAttributes.Shape.BAR
        }
        val thickness = when (shape) {
            HelixCursorShape.BLOCK -> THICKNESS_BLOCK
            HelixCursorShape.BAR -> THICKNESS_BAR
            HelixCursorShape.UNDERLINE -> THICKNESS_UNDERLINE
            HelixCursorShape.HIDDEN -> THICKNESS_HIDDEN
        }

        val visualAttributes = CaretVisualAttributes(
            tintColor,
            CaretVisualAttributes.Weight.NORMAL,
            visualShape,
            thickness,
        )

        hostEditor.caretModel.allCarets.forEach { caret ->
            caret.visualAttributes = visualAttributes
        }

        updateCursorColor(hostEditor, mode)
        HelixLineNumberManager.applyLineNumbers(hostEditor, mode)
        if (!hostEditor.isDisposed) {
            hostEditor.contentComponent.repaint()
        }
    }

    private fun updateCursorColor(editor: Editor, mode: HelixMode) {
        val colorsScheme = try {
            editor.colorsScheme
        } catch (_: UnsupportedOperationException) {
            return
        }
        if (HelixEffectiveSettings.cursorTint) {
            if (editor.getUserData(ORIGINAL_CARET_COLOR_STORED) != true) {
                editor.putUserData(ORIGINAL_CARET_COLOR_STORED, true)
                editor.putUserData(ORIGINAL_CARET_COLOR_KEY, colorsScheme.getColor(EditorColors.CARET_COLOR))
            }

            val targetColor = when (mode) {
                HelixMode.NORMAL -> HelixTheme.MODE_NOR_CARET
                HelixMode.INSERT -> HelixTheme.MODE_INS_CARET
                HelixMode.SELECT -> HelixTheme.MODE_SEL_CARET
            }
            colorsScheme.setColor(EditorColors.CARET_COLOR, targetColor)
        } else {
            if (editor.getUserData(ORIGINAL_CARET_COLOR_STORED) == true) {
                val orig = editor.getUserData(ORIGINAL_CARET_COLOR_KEY)
                colorsScheme.setColor(EditorColors.CARET_COLOR, orig)
                editor.putUserData(ORIGINAL_CARET_COLOR_STORED, false)
                editor.putUserData(ORIGINAL_CARET_COLOR_KEY, null)
            }
        }
    }
}
