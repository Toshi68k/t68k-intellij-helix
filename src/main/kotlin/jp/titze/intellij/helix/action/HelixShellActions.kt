package jp.titze.intellij.helix.action

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.command.HelixDirectoryManager
import jp.titze.intellij.helix.shell.HelixShellExecutor
import jp.titze.intellij.helix.shell.HelixShellResult
import jp.titze.intellij.helix.ui.HelixPromptType

object HelixShellActions {

    fun executePromptCommand(
        editor: Editor,
        type: HelixPromptType,
        command: String,
        baseSnapshot: List<HelixCaretSnapshot>,
    ) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        when (type) {
            HelixPromptType.PIPE -> pipeSelections(editor, trimmed, baseSnapshot)
            HelixPromptType.INSERT_OUTPUT -> insertOutput(editor, trimmed, append = false, baseSnapshot)
            HelixPromptType.APPEND_OUTPUT -> insertOutput(editor, trimmed, append = true, baseSnapshot)
            HelixPromptType.KEEP_PIPE -> keepPipeSelections(editor, trimmed, baseSnapshot)
            HelixPromptType.PIPE_TO -> pipeToSelections(editor, trimmed, baseSnapshot)
            else -> Unit
        }
    }

    fun pipeSelections(editor: Editor, command: String, baseSnapshot: List<HelixCaretSnapshot>? = null): Boolean {
        val snapshots = baseSnapshot ?: HelixCaretUtils.captureCarets(editor)
        if (snapshots.isEmpty()) return false

        val doc = editor.document
        val cwd = HelixDirectoryManager.getCurrentDirectory(editor)

        val inputsAndSnapshots = snapshots.map { snap ->
            val text = getSelectionText(doc, snap)
            snap to text
        }

        val results = mutableListOf<Pair<HelixCaretSnapshot, String>>()
        for ((snap, input) in inputsAndSnapshots) {
            val res = HelixShellExecutor.execute(command, input, cwd)
            if (res.exitCode != 0) {
                notifyError(editor, command, res)
                return false
            }
            results.add(snap to adjustTrailingNewline(input, res.stdout))
        }

        applyReplacements(editor, results)
        HelixDirectoryManager.setStatus(editor, "Piped ${results.size} selection(s) through '$command'")
        return true
    }

    fun insertOutput(
        editor: Editor,
        command: String,
        append: Boolean = false,
        baseSnapshot: List<HelixCaretSnapshot>? = null,
    ): Boolean {
        val snapshots = baseSnapshot ?: HelixCaretUtils.captureCarets(editor)
        if (snapshots.isEmpty()) return false

        val cwd = HelixDirectoryManager.getCurrentDirectory(editor)
        val res = HelixShellExecutor.execute(command, null, cwd)
        if (res.exitCode != 0) {
            notifyError(editor, command, res)
            return false
        }

        val output = trimTrailingNewline(res.stdout)
        val doc = editor.document
        val sortedSnapshots = snapshots.sortedByDescending { it.selectionStart }
        val newSnapshots = mutableListOf<HelixCaretSnapshot>()

        WriteCommandAction.runWriteCommandAction(
            editor.project,
            "Helix Shell Output",
            null,
            Runnable {
                for (snap in sortedSnapshots) {
                    val insertOffset = if (append) {
                        if (snap.selectionEnd > snap.selectionStart) snap.selectionEnd else snap.offset
                    } else {
                        snap.selectionStart
                    }
                    val safeOffset = insertOffset.coerceIn(0, doc.textLength)
                    shiftSnapshots(newSnapshots, output.length)
                    doc.insertString(safeOffset, output)
                    val newEnd = safeOffset + output.length
                    newSnapshots.add(HelixCaretSnapshot(newEnd, safeOffset, newEnd))
                }
                HelixCaretUtils.restoreCarets(editor, newSnapshots.sortedBy { it.offset })
            },
        )

        val actionName = if (append) "Appended" else "Inserted"
        HelixDirectoryManager.setStatus(editor, "$actionName output of '$command'")
        return true
    }

    fun keepPipeSelections(editor: Editor, command: String, baseSnapshot: List<HelixCaretSnapshot>? = null): Boolean {
        val snapshots = baseSnapshot ?: HelixCaretUtils.captureCarets(editor)
        if (snapshots.isEmpty()) return false

        val doc = editor.document
        val cwd = HelixDirectoryManager.getCurrentDirectory(editor)

        val keptSnapshots = snapshots.filter { snap ->
            val input = getSelectionText(doc, snap)
            val res = HelixShellExecutor.execute(command, input, cwd)
            res.exitCode == 0
        }

        if (keptSnapshots.isEmpty()) {
            HelixDirectoryManager.setStatus(editor, "0 selections matched exit code 0")
            return false
        }

        HelixCaretUtils.restoreCarets(editor, keptSnapshots)
        HelixDirectoryManager.setStatus(editor, "${keptSnapshots.size} selection(s) kept")
        return true
    }

    fun pipeToSelections(editor: Editor, command: String, baseSnapshot: List<HelixCaretSnapshot>? = null): Boolean {
        val snapshots = baseSnapshot ?: HelixCaretUtils.captureCarets(editor)
        if (snapshots.isEmpty()) return false

        val doc = editor.document
        val cwd = HelixDirectoryManager.getCurrentDirectory(editor)

        for (snap in snapshots) {
            val input = getSelectionText(doc, snap)
            val res = HelixShellExecutor.execute(command, input, cwd)
            if (res.exitCode != 0) {
                notifyError(editor, command, res)
                return false
            }
        }

        HelixDirectoryManager.setStatus(editor, "Piped ${snapshots.size} selection(s) to '$command'")
        return true
    }

    fun runShellCommand(editor: Editor, command: String) {
        val cwd = HelixDirectoryManager.getCurrentDirectory(editor)
        ApplicationManager.getApplication().executeOnPooledThread {
            val res = HelixShellExecutor.execute(command, null, cwd)
            ApplicationManager.getApplication().invokeLater {
                if (res.exitCode == 0) {
                    val msg = if (res.stdout.isBlank()) "Command finished: $command" else res.stdout.trim()
                    HelixDirectoryManager.setStatus(editor, msg)
                } else {
                    val errorDetail = res.stderr.ifBlank { "Exit code ${res.exitCode}" }
                    HelixDirectoryManager.setStatus(editor, "Command failed: ${errorDetail.trim()}")
                }
            }
        }
    }

    private fun getSelectionText(doc: com.intellij.openapi.editor.Document, snap: HelixCaretSnapshot): String =
        if (snap.selectionEnd > snap.selectionStart) {
            doc.getText(TextRange(snap.selectionStart, snap.selectionEnd.coerceAtMost(doc.textLength)))
        } else {
            val end = (snap.offset + 1).coerceAtMost(doc.textLength)
            if (snap.offset < doc.textLength) doc.getText(TextRange(snap.offset, end)) else ""
        }

    private fun applyReplacements(editor: Editor, results: List<Pair<HelixCaretSnapshot, String>>) {
        val doc = editor.document
        val sorted = results.sortedByDescending { it.first.selectionStart }
        val newSnapshots = mutableListOf<HelixCaretSnapshot>()

        WriteCommandAction.runWriteCommandAction(
            editor.project,
            "Helix Shell Pipe",
            null,
            Runnable {
                for ((snap, newText) in sorted) {
                    val start = snap.selectionStart
                    val end = if (snap.selectionEnd > snap.selectionStart) {
                        snap.selectionEnd.coerceAtMost(doc.textLength)
                    } else {
                        (snap.offset + 1).coerceAtMost(doc.textLength)
                    }
                    val delta = newText.length - (end - start)
                    shiftSnapshots(newSnapshots, delta)

                    doc.replaceString(start, end, newText)
                    val newEnd = start + newText.length
                    newSnapshots.add(HelixCaretSnapshot(newEnd, start, newEnd))
                }
                HelixCaretUtils.restoreCarets(editor, newSnapshots.sortedBy { it.offset })
            },
        )
    }

    private fun shiftSnapshots(snapshots: MutableList<HelixCaretSnapshot>, delta: Int) {
        if (delta == 0) return
        for (i in snapshots.indices) {
            val s = snapshots[i]
            snapshots[i] = HelixCaretSnapshot(
                offset = s.offset + delta,
                selectionStart = s.selectionStart + delta,
                selectionEnd = s.selectionEnd + delta,
            )
        }
    }

    private fun adjustTrailingNewline(input: String, output: String): String {
        if (!input.endsWith("\n") && output.endsWith("\n")) {
            return trimTrailingNewline(output)
        }
        return output
    }

    private fun trimTrailingNewline(output: String): String = output.removeSuffix("\r\n").removeSuffix("\n")

    private fun notifyError(editor: Editor, command: String, res: HelixShellResult) {
        val detail = res.stderr.ifBlank { "Exit code ${res.exitCode}" }.trim()
        HelixDirectoryManager.setStatus(editor, "Command '$command' failed: $detail")
    }
}
