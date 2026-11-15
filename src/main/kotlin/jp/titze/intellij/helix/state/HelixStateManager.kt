// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.state

import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
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

    fun get(editor: Editor): HelixEditorState? = editor.getUserData(HELIX_STATE_KEY)

    fun getOrCreate(editor: Editor): HelixEditorState {
        var state = editor.getUserData(HELIX_STATE_KEY)
        if (state == null) {
            state = HelixEditorState(editor)
            editor.putUserData(HELIX_STATE_KEY, state)
            state.addListener { updateCursor(editor, it.mode) }
            updateCursor(editor, state.mode)
        }
        return state
    }

    fun updateCursor(editor: Editor, mode: HelixMode) {
        val shape = when (mode) {
            HelixMode.NORMAL -> HelixEffectiveSettings.cursorShapeNormal
            HelixMode.INSERT -> HelixEffectiveSettings.cursorShapeInsert
            HelixMode.SELECT -> HelixEffectiveSettings.cursorShapeSelect
        }

        editor.settings.isBlockCursor = (shape == HelixCursorShape.BLOCK)

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
        val thickness = if (shape == HelixCursorShape.HIDDEN) 0.0f else 1.0f

        val visualAttributes = CaretVisualAttributes(
            tintColor,
            CaretVisualAttributes.Weight.NORMAL,
            visualShape,
            thickness,
        )

        editor.caretModel.allCarets.forEach { caret ->
            caret.visualAttributes = visualAttributes
        }

        updateCursorColor(editor, mode)
        HelixLineNumberManager.applyLineNumbers(editor, mode)
        if (!editor.isDisposed) {
            editor.contentComponent.repaint()
        }
    }

    private fun updateCursorColor(editor: Editor, mode: HelixMode) {
        val colorsScheme = editor.colorsScheme
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
