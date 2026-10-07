package jp.titze.intellij.helix.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixStateManager
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
    private const val MAX_ANCESTOR_DEPTH = 12

    fun isActive(editor: Editor?): Boolean {
        if (editor == null) return false
        val settings = HelixSettings.instance
        if (!settings.enabled) return false
        if (editor.isOneLineMode || editor.isViewer || editor.isDisposed) return false
        if (hasAncestorClass(editor, TERMINAL_PREFIXES)) return false
        if (hasAncestorClass(editor, COMMIT_PREFIXES)) return settings.activateInCommitMessage

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
            } else {
                deactivate(editor)
            }
        }
    }

    fun deactivate(editor: Editor) {
        val state = HelixStateManager.get(editor) ?: return
        state.clearPendingSequence()
        state.clearCount()
        state.clearSelectedRegister()
        editor.settings.isBlockCursor = EditorSettingsExternalizable.getInstance().isBlockCursor
    }

    fun deactivateAll() {
        EditorFactory.getInstance().allEditors.forEach { editor ->
            deactivate(editor)
        }
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
