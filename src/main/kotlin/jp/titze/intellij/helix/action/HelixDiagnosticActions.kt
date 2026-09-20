package jp.titze.intellij.helix.action

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.TextRange
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixVisualFeedback

object HelixDiagnosticActions {

    private data class DiagnosticItem(val description: String, val severityValue: Int, val range: TextRange)

    fun yankDiagnostic(editor: Editor): Boolean {
        val project = editor.project ?: return false
        val doc = editor.document
        val caretOffset = editor.caretModel.offset
        val line = doc.getLineNumber(caretOffset)
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)

        val items = mutableListOf<DiagnosticItem>()
        val seenDescriptions = mutableSetOf<String>()

        DaemonCodeAnalyzerEx.processHighlights(
            doc,
            project,
            HighlightSeverity.INFORMATION,
            lineStart,
            lineEnd,
        ) { info ->
            val desc = info.description ?: cleanHtml(info.toolTip)
            if (!desc.isNullOrBlank() && seenDescriptions.add(desc)) {
                items.add(
                    DiagnosticItem(
                        description = desc,
                        severityValue = info.severity.myVal,
                        range = TextRange(info.actualStartOffset, info.actualEndOffset),
                    ),
                )
            }
            true
        }

        collectHighlighters(editor, lineStart, lineEnd, items, seenDescriptions)

        if (items.isEmpty()) return false

        val caretItems = items.filter { caretOffset in it.range.startOffset..it.range.endOffset }
        val candidates = if (caretItems.isNotEmpty()) caretItems else items
        val sorted = candidates.sortedByDescending { it.severityValue }
        val combinedText = sorted.map { it.description }.distinct().joinToString("\n")

        val state = HelixStateManager.getOrCreate(editor)
        val targetRegister = state.selectedRegister
        state.clearSelectedRegister()

        HelixRegisterManager.setClipboard(combinedText)
        HelixRegisterManager.recordYank(
            text = combinedText,
            isLinewise = false,
            pieces = listOf(combinedText),
            register = targetRegister,
        )

        val flashRanges = sorted.map { it.range }
        HelixVisualFeedback.flashYank(editor, flashRanges)
        return true
    }

    private fun collectHighlighters(
        editor: Editor,
        lineStart: Int,
        lineEnd: Int,
        items: MutableList<DiagnosticItem>,
        seenDescriptions: MutableSet<String>,
    ) {
        val project = editor.project ?: return
        val doc = editor.document
        val highlighters = mutableListOf<RangeHighlighter>()
        highlighters.addAll(editor.markupModel.allHighlighters)
        DocumentMarkupModel.forDocument(doc, project, false)?.let {
            highlighters.addAll(it.allHighlighters)
        }

        for (highlighter in highlighters) {
            if (highlighter.endOffset < lineStart || highlighter.startOffset > lineEnd) {
                continue
            }
            val info = HighlightInfo.fromRangeHighlighter(highlighter)
            val desc = info?.description ?: extractTooltip(highlighter.errorStripeTooltip)
            if (!desc.isNullOrBlank() && seenDescriptions.add(desc)) {
                val severity = info?.severity?.myVal ?: HighlightSeverity.WARNING.myVal
                items.add(
                    DiagnosticItem(
                        description = desc,
                        severityValue = severity,
                        range = TextRange(highlighter.startOffset, highlighter.endOffset),
                    ),
                )
            }
        }
    }

    private fun extractTooltip(tooltip: Any?): String? = when (tooltip) {
        null -> null
        is HighlightInfo -> tooltip.description ?: cleanHtml(tooltip.toolTip)
        is String -> cleanHtml(tooltip)
        else -> cleanHtml(tooltip.toString())
    }

    private fun cleanHtml(html: String?): String? {
        if (html == null) return null
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .trim()
    }
}
