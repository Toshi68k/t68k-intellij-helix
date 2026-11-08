// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import jp.titze.intellij.helix.settings.HelixLineNumberMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode

/**
 * Manages gutter line number display and smart mode-based numeration switching
 * (Absolute vs. Relative vs. Hybrid vs. Smart insert fallback).
 */
object HelixLineNumberManager {

    private val ORIGINAL_LINE_NUMBERS_SHOWN = Key.create<Boolean>("HELIX_ORIGINAL_LINE_NUMBERS_SHOWN")
    private val ORIGINAL_NUMERATION_TYPE =
        Key.create<EditorSettings.LineNumerationType>("HELIX_ORIGINAL_NUMERATION_TYPE")

    fun applyLineNumbers(editor: Editor, mode: HelixMode) {
        if (editor.isDisposed) return

        val configMode = HelixSettings.instance.lineNumberMode
        if (configMode == HelixLineNumberMode.IDE_DEFAULT) {
            restoreOriginalSettings(editor)
            return
        }

        rememberOriginalSettings(editor)

        val settings = editor.settings
        when (configMode) {
            HelixLineNumberMode.OFF -> {
                if (settings.isLineNumbersShown) {
                    settings.isLineNumbersShown = false
                    repaintGutter(editor)
                }
            }

            HelixLineNumberMode.ABSOLUTE -> {
                updateNumeration(editor, true, EditorSettings.LineNumerationType.ABSOLUTE)
            }

            HelixLineNumberMode.RELATIVE -> {
                updateNumeration(editor, true, EditorSettings.LineNumerationType.RELATIVE)
            }

            HelixLineNumberMode.HYBRID -> {
                updateNumeration(editor, true, EditorSettings.LineNumerationType.HYBRID)
            }

            HelixLineNumberMode.SMART -> {
                val targetNumeration = if (mode == HelixMode.INSERT) {
                    EditorSettings.LineNumerationType.ABSOLUTE
                } else {
                    EditorSettings.LineNumerationType.RELATIVE
                }
                updateNumeration(editor, true, targetNumeration)
            }

            HelixLineNumberMode.IDE_DEFAULT -> Unit
        }
    }

    fun toggleRelative() {
        val current = HelixSettings.instance.lineNumberMode
        val nextMode = when (current) {
            HelixLineNumberMode.RELATIVE,
            HelixLineNumberMode.HYBRID,
            HelixLineNumberMode.SMART,
            -> HelixLineNumberMode.ABSOLUTE

            else -> HelixLineNumberMode.SMART
        }
        HelixSettings.instance.lineNumberMode = nextMode
        HelixEditorEligibility.refreshAllEditors()
    }

    private fun updateNumeration(
        editor: Editor,
        showNumbers: Boolean,
        numerationType: EditorSettings.LineNumerationType,
    ) {
        val settings = editor.settings
        var changed = false
        if (settings.isLineNumbersShown != showNumbers) {
            settings.isLineNumbersShown = showNumbers
            changed = true
        }
        if (settings.lineNumerationType != numerationType) {
            settings.lineNumerationType = numerationType
            changed = true
        }
        if (changed) {
            repaintGutter(editor)
        }
    }

    private fun rememberOriginalSettings(editor: Editor) {
        if (editor.getUserData(ORIGINAL_LINE_NUMBERS_SHOWN) == null) {
            editor.putUserData(ORIGINAL_LINE_NUMBERS_SHOWN, editor.settings.isLineNumbersShown)
            editor.putUserData(ORIGINAL_NUMERATION_TYPE, editor.settings.lineNumerationType)
        }
    }

    private fun restoreOriginalSettings(editor: Editor) {
        val origShown = editor.getUserData(ORIGINAL_LINE_NUMBERS_SHOWN)
        if (origShown != null) {
            val settings = editor.settings
            settings.isLineNumbersShown = origShown
            settings.lineNumerationType = editor.getUserData(ORIGINAL_NUMERATION_TYPE)
                ?: EditorSettings.LineNumerationType.ABSOLUTE
            editor.putUserData(ORIGINAL_LINE_NUMBERS_SHOWN, null)
            editor.putUserData(ORIGINAL_NUMERATION_TYPE, null)
            repaintGutter(editor)
        }
    }

    private fun repaintGutter(editor: Editor) {
        if (!editor.isDisposed) {
            (editor as? EditorEx)?.gutterComponentEx?.repaint()
        }
    }
}
