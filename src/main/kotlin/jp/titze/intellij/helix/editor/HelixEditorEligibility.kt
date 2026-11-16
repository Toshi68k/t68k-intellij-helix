// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.textarea.TextComponentEditor
import com.intellij.ui.EditorTextField
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixStatusBarWidget
import java.awt.Component

/**
 * Single source of truth for whether Helix modal editing applies to a given editor.
 *
 * Consulted across typed action handling, editor action handling, IDE event dispatching,
 * file editor listeners, and AnAction updates to respect master enabled state and configured editor scope.
 */
object HelixEditorEligibility {

    private val TERMINAL_PREFIXES = listOf("org.jetbrains.plugins.terminal", "com.intellij.terminal")
    private val COMMIT_PREFIXES = listOf("com.intellij.openapi.vcs.ui.CommitMessage", "com.intellij.vcs.commit")
    private val AI_PROMPT_PREFIXES = listOf(
        "com.intellij.air",
        "org.jetbrains.air",
        "com.intellij.ml.llm",
    )
    private const val MAX_ANCESTOR_DEPTH = 12

    fun isActive(editor: Editor?): Boolean {
        if (editor == null) return false
        val target = HelixStateManager.resolveHostEditor(editor)
        if (target !== editor) {
            return isActive(target)
        }
        val settings = HelixSettings.instance
        if (!settings.enabled) return false
        if (editor is TextComponentEditor) return false
        if (editor.isOneLineMode || editor.isViewer || editor.isDisposed) return false
        if (hasAncestorClass(editor, TERMINAL_PREFIXES)) return false
        if (isCommitEditor(editor)) return settings.activateInCommitMessage
        if (isAiPromptEditor(editor)) return settings.activateInAiPrompt
        if (isEditorTextField(editor) && !settings.activateInOtherEditors) return false

        val kind = editor.editorKind
        val isCodeEditor = kind == EditorKind.MAIN_EDITOR ||
            (kind == EditorKind.UNTYPED && editor.project != null)

        return when {
            isCodeEditor -> true
            kind == EditorKind.DIFF -> settings.activateInDiff
            kind == EditorKind.CONSOLE -> settings.activateInConsole
            else -> settings.activateInOtherEditors
        }
    }

    /**
     * Re-evaluates all open editors after settings or mode toggling changes.
     */
    fun refreshAllEditors() {
        EditorFactory.getInstance().allEditors.forEach { editor ->
            if (isActive(editor)) {
                val state = HelixStateManager.getOrCreate(editor)
                HelixStateManager.updateCursor(editor, state.mode)
                state.notifyListeners()
            } else {
                deactivate(editor)
            }
        }
        HelixStatusBarWidget.updateAllWidgets()
    }

    fun deactivate(editor: Editor) {
        val hostEditor = HelixStateManager.resolveHostEditor(editor)
        val state = HelixStateManager.get(hostEditor) ?: return
        state.clearPendingSequence()
        state.clearCount()
        state.clearSelectedRegister()
        val resetUi = {
            if (!hostEditor.isDisposed) {
                hostEditor.settings.isBlockCursor = EditorSettingsExternalizable.getInstance().isBlockCursor
                hostEditor.caretModel.allCarets.forEach { caret ->
                    caret.visualAttributes = com.intellij.openapi.editor.CaretVisualAttributes.getDefault()
                }
            }
        }
        val app = ApplicationManager.getApplication()
        if (app != null && !app.isDispatchThread) {
            app.invokeLater(resetUi, ModalityState.any())
        } else {
            resetUi()
        }
    }

    fun deactivateAll() {
        EditorFactory.getInstance().allEditors.forEach { editor ->
            deactivate(editor)
        }
    }

    private val MANAGED_BY_FIELD by lazy {
        try {
            val field = EditorTextField::class.java.getDeclaredField("MANAGED_BY_FIELD")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(null) as? com.intellij.openapi.util.Key<Boolean>
        } catch (_: Throwable) {
            null
        }
    }

    fun isCommitEditor(editor: Editor): Boolean = hasAncestorClass(editor, COMMIT_PREFIXES)

    fun isAiPromptEditor(editor: Editor): Boolean = hasAncestorClass(editor, AI_PROMPT_PREFIXES)

    fun isEditorTextField(editor: Editor): Boolean {
        if (EditorTextField.managesEditor(editor)) return true
        val key = MANAGED_BY_FIELD
        return key != null && editor.getUserData(key) == true
    }

    private fun hasAncestorClass(editor: Editor, prefixes: List<String>): Boolean {
        var component: Component? = editor.component
        var depth = 0
        while (component != null && depth < MAX_ANCESTOR_DEPTH) {
            val className = component.javaClass.name
            if (prefixes.any { className.startsWith(it) }) return true
            component = component.parent
            depth++
        }
        return false
    }
}
