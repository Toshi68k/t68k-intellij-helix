// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.config

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import jp.titze.intellij.helix.action.HelixVisualToggleActions
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.settings.HelixCursorShape
import jp.titze.intellij.helix.settings.HelixEffectiveSettings
import jp.titze.intellij.helix.settings.HelixLineNumberMode
import jp.titze.intellij.helix.settings.HelixSettings
import java.io.File
import javax.swing.SwingConstants

enum class OptionStatus(val displayName: String) {
    APPLIED("Applied"),
    NOT_APPLICABLE("Not applicable"),
    UNSUPPORTED("Unsupported"),
    INVALID("Invalid"),
    IGNORED("Ignored"),
}

data class HelixOptionOverride(
    val key: String,
    val value: Any?,
    val rawValueString: String,
    val sourceFile: File?,
    val lineNumber: Int,
    val status: OptionStatus,
    val diagnosticMessage: String? = null,
)

interface EditorOptionDef<T> {
    val key: String
    val aliases: List<String>
    fun parse(raw: Any?): T?
    fun validate(raw: Any?): String?
    fun apply(value: T, editor: Editor?): Boolean
}

object HelixEditorOptions {

    val NOT_APPLICABLE_KEYS = setOf(
        "true-color",
        "mouse",
        "middle-click-paste",
        "scroll-lines",
        "shell",
        "cursorcolumn",
        "gutters",
        "auto-completion",
        "auto-format",
        "auto-save",
        "idle-timeout",
        "completion-timeout",
        "preview-completion-insert",
        "completion-trigger-len",
        "auto-info",
        "undercurl",
        "text-width",
        "workspace-lsp-roots",
        "default-line-ending",
        "insert-final-newline",
        "rainbow-brackets",
    )

    val NOT_APPLICABLE_PREFIXES = listOf("lsp.", "file-picker.", "statusline.", "auto-pairs.")

    private val options = mutableListOf<EditorOptionDef<*>>()
    private val keyToOptionMap = mutableMapOf<String, EditorOptionDef<*>>()

    init {
        register(LineNumberOptionDef)
        register(ColorModesOptionDef)
        register(CursorTintOptionDef)
        register(ScrolloffOptionDef)
        register(CursorlineOptionDef)
        register(RulersOptionDef)
        register(SoftWrapOptionDef)
        register(WhitespaceOptionDef)
        register(IndentGuidesOptionDef)
        register(CursorShapeNormalOptionDef)
        register(CursorShapeInsertOptionDef)
        register(CursorShapeSelectOptionDef)
        register(SearchSmartCaseOptionDef)
        register(SearchWrapAroundOptionDef)
        register(JumpLabelAlphabetOptionDef)
        register(BufferlineOptionDef)
        register(InlayHintsOptionDef)
        register(StickyContextOptionDef)
        register(BreadcrumbsOptionDef)
    }

    private fun register(def: EditorOptionDef<*>) {
        options.add(def)
        keyToOptionMap[normalizeKey(def.key)] = def
        for (alias in def.aliases) {
            keyToOptionMap[normalizeKey(alias)] = def
        }
    }

    fun normalizeKey(key: String): String = key.trim().lowercase().replace('_', '-')

    fun findDefinition(key: String): EditorOptionDef<*>? = keyToOptionMap[normalizeKey(key)]

    fun isKnownKey(key: String): Boolean = findDefinition(key) != null

    fun isNotApplicable(key: String): Boolean {
        val norm = normalizeKey(key)
        if (norm in NOT_APPLICABLE_KEYS) return true
        return NOT_APPLICABLE_PREFIXES.any { norm.startsWith(it) }
    }

    fun evaluateTomlOptions(
        parsedOptions: Map<String, HelixParsedOption>,
        file: File?,
    ): Map<String, HelixOptionOverride> {
        val result = LinkedHashMap<String, HelixOptionOverride>()

        for ((_, opt) in parsedOptions) {
            val normKey = normalizeKey(opt.key)
            val def = findDefinition(normKey)
            if (def != null) {
                val validationError = def.validate(opt.value)
                if (validationError != null) {
                    result[def.key] = HelixOptionOverride(
                        key = def.key,
                        value = null,
                        rawValueString = opt.rawString,
                        sourceFile = file,
                        lineNumber = opt.lineNumber,
                        status = OptionStatus.INVALID,
                        diagnosticMessage = validationError,
                    )
                } else {
                    val parsedVal = def.parse(opt.value)
                    result[def.key] = HelixOptionOverride(
                        key = def.key,
                        value = parsedVal,
                        rawValueString = opt.rawString,
                        sourceFile = file,
                        lineNumber = opt.lineNumber,
                        status = OptionStatus.APPLIED,
                    )
                }
            } else if (isNotApplicable(normKey)) {
                result[normKey] = HelixOptionOverride(
                    key = normKey,
                    value = opt.value,
                    rawValueString = opt.rawString,
                    sourceFile = file,
                    lineNumber = opt.lineNumber,
                    status = OptionStatus.NOT_APPLICABLE,
                )
            } else {
                result[normKey] = HelixOptionOverride(
                    key = normKey,
                    value = opt.value,
                    rawValueString = opt.rawString,
                    sourceFile = file,
                    lineNumber = opt.lineNumber,
                    status = OptionStatus.IGNORED,
                    diagnosticMessage = "Unknown [editor] option '$normKey' (ignored)",
                )
            }
        }

        return result
    }

    fun setOption(key: String, valueStr: String, editor: Editor? = null): Boolean {
        val def = findDefinition(key) ?: return false
        val parsed = def.parse(valueStr) ?: return false
        HelixEffectiveSettings.setSessionOption(def.key, parsed)
        syncWithHelixSettings(def.key, parsed)
        @Suppress("UNCHECKED_CAST")
        (def as EditorOptionDef<Any?>).apply(parsed, editor)
        return true
    }

    private fun syncWithHelixSettings(key: String, value: Any?) {
        val settings = HelixSettings.instance
        when (key) {
            "line-number" -> if (value is HelixLineNumberMode) settings.lineNumberMode = value
            "color-modes" -> if (value is Boolean) settings.colorModes = value
            "cursor-tint" -> if (value is Boolean) settings.cursorTint = value
            "cursor-shape.normal" -> if (value is HelixCursorShape) settings.cursorShapeNormal = value
            "cursor-shape.insert" -> if (value is HelixCursorShape) settings.cursorShapeInsert = value
            "cursor-shape.select" -> if (value is HelixCursorShape) settings.cursorShapeSelect = value
            "search.smart-case" -> if (value is Boolean) settings.searchSmartCase = value
            "search.wrap-around" -> if (value is Boolean) settings.searchWrapAround = value
            "jump-label-alphabet" -> if (value is String) settings.jumpLabelAlphabet = value
        }
    }

    fun parseBoolean(raw: Any?): Boolean? = when (raw) {
        is Boolean -> raw

        is String -> when (raw.trim().lowercase()) {
            "true", "on", "yes", "enable", "enabled", "all", "1" -> true
            "false", "off", "no", "disable", "disabled", "none", "0" -> false
            else -> null
        }

        is Number -> raw.toInt() != 0

        else -> null
    }

    private fun forEachTargetEditor(editor: Editor?, action: (Editor) -> Unit) {
        if (editor != null && !editor.isDisposed) {
            action(editor)
        } else {
            EditorFactory.getInstance().allEditors.forEach { ed ->
                if (HelixEditorEligibility.isActive(ed)) {
                    action(ed)
                }
            }
        }
    }

    // Definitions for individual options

    object LineNumberOptionDef : EditorOptionDef<HelixLineNumberMode> {
        override val key: String = "line-number"
        override val aliases: List<String> = listOf("line-numbers", "numbers")

        override fun parse(raw: Any?): HelixLineNumberMode? = when (raw) {
            is HelixLineNumberMode -> raw

            is Boolean -> if (raw) HelixLineNumberMode.ABSOLUTE else HelixLineNumberMode.OFF

            is String -> when (raw.trim().lowercase()) {
                "relative" -> HelixLineNumberMode.RELATIVE
                "smart", "smart-relative" -> HelixLineNumberMode.SMART
                "hybrid" -> HelixLineNumberMode.HYBRID
                "absolute" -> HelixLineNumberMode.ABSOLUTE
                "default", "ide", "ide-default" -> HelixLineNumberMode.IDE_DEFAULT
                "off", "none" -> HelixLineNumberMode.OFF
                "true", "on" -> HelixLineNumberMode.ABSOLUTE
                "false" -> HelixLineNumberMode.OFF
                else -> null
            }

            else -> null
        }

        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid line-number mode '$raw'"

        override fun apply(value: HelixLineNumberMode, editor: Editor?): Boolean {
            HelixEditorEligibility.refreshAllEditors()
            return true
        }
    }

    object ColorModesOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "color-modes"
        override val aliases: List<String> = listOf("color-modes.enable")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid boolean for color-modes"

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            HelixEditorEligibility.refreshAllEditors()
            return true
        }
    }

    object CursorTintOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "cursor-tint"
        override val aliases: List<String> = listOf("cursor-tint.enable", "cursor.tint")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid boolean for cursor-tint"

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            HelixEditorEligibility.refreshAllEditors()
            return true
        }
    }

    object ScrolloffOptionDef : EditorOptionDef<Int> {
        override val key: String = "scrolloff"
        override val aliases: List<String> = emptyList()

        override fun parse(raw: Any?): Int? = when (raw) {
            is Number -> raw.toInt().coerceAtLeast(0)
            is String -> raw.trim().toIntOrNull()?.coerceAtLeast(0)
            else -> null
        }

        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid scrolloff integer '$raw'"

        override fun apply(value: Int, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                ed.settings.verticalScrollOffset = value
            }
            return true
        }
    }

    object CursorlineOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "cursorline"
        override val aliases: List<String> = emptyList()

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid boolean for cursorline"

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                ed.settings.isCaretRowShown = value
            }
            return true
        }
    }

    object RulersOptionDef : EditorOptionDef<List<Int>> {
        override val key: String = "rulers"
        override val aliases: List<String> = emptyList()

        override fun parse(raw: Any?): List<Int>? = when (raw) {
            is List<*> -> raw.mapNotNull {
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.trim().toIntOrNull()
                    else -> null
                }
            }

            is Number -> listOf(raw.toInt())

            is String -> {
                val cleaned = raw.trim().removePrefix("[").removeSuffix("]").trim()
                if (cleaned.isEmpty()) {
                    emptyList()
                } else {
                    cleaned.split(",").mapNotNull { it.trim().toIntOrNull() }
                }
            }

            else -> null
        }

        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid rulers list '$raw'"

        override fun apply(value: List<Int>, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                ed.settings.setSoftMargins(value)
            }
            return true
        }
    }

    object SoftWrapOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "soft-wrap.enable"
        override val aliases: List<String> = listOf("soft-wrap", "wrap")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid boolean for soft-wrap"

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                HelixVisualToggleActions.toggleSoftWrap(ed, value)
            }
            return true
        }
    }

    object WhitespaceOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "whitespace.render"
        override val aliases: List<String> = listOf("whitespace", "whitespaces")

        override fun parse(raw: Any?): Boolean? = when (raw) {
            is Boolean -> raw

            is String -> when (raw.trim().lowercase()) {
                "all", "selection" -> true
                "none" -> false
                else -> parseBoolean(raw)
            }

            else -> parseBoolean(raw)
        }

        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid whitespace render option '$raw'"
        }

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                HelixVisualToggleActions.toggleWhitespace(ed, value)
            }
            return true
        }
    }

    object IndentGuidesOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "indent-guides.render"
        override val aliases: List<String> = listOf("indent-guides")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid boolean for indent-guides"
        }

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                ed.settings.isIndentGuidesShown = value
            }
            return true
        }
    }

    object CursorShapeNormalOptionDef : EditorOptionDef<HelixCursorShape> {
        override val key: String = "cursor-shape.normal"
        override val aliases: List<String> = listOf("cursor-shape-normal")

        override fun parse(raw: Any?): HelixCursorShape? = when (raw) {
            is HelixCursorShape -> raw
            is String -> HelixCursorShape.fromString(raw)
            else -> null
        }

        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid cursor-shape.normal '$raw'"
        }

        override fun apply(value: HelixCursorShape, editor: Editor?): Boolean {
            HelixEditorEligibility.refreshAllEditors()
            return true
        }
    }

    object CursorShapeInsertOptionDef : EditorOptionDef<HelixCursorShape> {
        override val key: String = "cursor-shape.insert"
        override val aliases: List<String> = listOf("cursor-shape-insert")

        override fun parse(raw: Any?): HelixCursorShape? = when (raw) {
            is HelixCursorShape -> raw
            is String -> HelixCursorShape.fromString(raw)
            else -> null
        }

        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid cursor-shape.insert '$raw'"
        }

        override fun apply(value: HelixCursorShape, editor: Editor?): Boolean {
            HelixEditorEligibility.refreshAllEditors()
            return true
        }
    }

    object CursorShapeSelectOptionDef : EditorOptionDef<HelixCursorShape> {
        override val key: String = "cursor-shape.select"
        override val aliases: List<String> = listOf("cursor-shape-select")

        override fun parse(raw: Any?): HelixCursorShape? = when (raw) {
            is HelixCursorShape -> raw
            is String -> HelixCursorShape.fromString(raw)
            else -> null
        }

        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid cursor-shape.select '$raw'"
        }

        override fun apply(value: HelixCursorShape, editor: Editor?): Boolean {
            HelixEditorEligibility.refreshAllEditors()
            return true
        }
    }

    object SearchSmartCaseOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "search.smart-case"
        override val aliases: List<String> = listOf("search-smart-case", "smart-case")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid boolean for search.smart-case"
        }

        override fun apply(value: Boolean, editor: Editor?): Boolean = true
    }

    object SearchWrapAroundOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "search.wrap-around"
        override val aliases: List<String> = listOf("search-wrap-around", "wrap-around")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid boolean for search.wrap-around"
        }

        override fun apply(value: Boolean, editor: Editor?): Boolean = true
    }

    object JumpLabelAlphabetOptionDef : EditorOptionDef<String> {
        override val key: String = "jump-label-alphabet"
        override val aliases: List<String> = emptyList()

        override fun parse(raw: Any?): String? = when (raw) {
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.length >= 2) trimmed else null
            }

            else -> null
        }

        override fun validate(raw: Any?): String? =
            if (parse(raw) != null) null else "jump-label-alphabet must contain at least 2 characters"

        override fun apply(value: String, editor: Editor?): Boolean = true
    }

    object BufferlineOptionDef : EditorOptionDef<String> {
        override val key: String = "bufferline"
        override val aliases: List<String> = emptyList()

        override fun parse(raw: Any?): String? = when (raw) {
            is String -> when (raw.trim().lowercase()) {
                "never" -> "never"
                "always" -> "always"
                "multiple" -> "multiple"
                else -> null
            }

            else -> null
        }

        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid bufferline mode '$raw' (expected 'never', 'always', or 'multiple')"
        }

        override fun apply(value: String, editor: Editor?): Boolean {
            val placement = if (value == "never") UISettings.TABS_NONE else SwingConstants.TOP
            val ui = UISettings.getInstance()
            ui.editorTabPlacement = placement
            ui.fireUISettingsChanged()
            return true
        }
    }

    object InlayHintsOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "inlay-hints.enable"
        override val aliases: List<String> = listOf("inlay-hints", "hints")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid boolean for inlay-hints"

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                HelixVisualToggleActions.toggleInlayHints(ed)
            }
            return true
        }
    }

    object StickyContextOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "sticky-context.enable"
        override val aliases: List<String> = listOf("sticky-context", "sticky-lines")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(
                raw,
            ) != null
        ) {
            null
        } else {
            "Invalid boolean for sticky-context"
        }

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                HelixVisualToggleActions.toggleStickyContext(ed)
            }
            return true
        }
    }

    object BreadcrumbsOptionDef : EditorOptionDef<Boolean> {
        override val key: String = "breadcrumbs.enable"
        override val aliases: List<String> = listOf("breadcrumbs")

        override fun parse(raw: Any?): Boolean? = parseBoolean(raw)
        override fun validate(raw: Any?): String? = if (parse(raw) != null) null else "Invalid boolean for breadcrumbs"

        override fun apply(value: Boolean, editor: Editor?): Boolean {
            forEachTargetEditor(editor) { ed ->
                HelixVisualToggleActions.toggleBreadcrumbs(ed, value)
            }
            return true
        }
    }
}
