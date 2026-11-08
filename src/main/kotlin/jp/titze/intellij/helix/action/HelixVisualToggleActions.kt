// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.action

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.editor.HelixLineNumberManager
import jp.titze.intellij.helix.settings.HelixLineNumberMode
import jp.titze.intellij.helix.settings.HelixSettings

/**
 * Handles visual and chrome toggle operations for editor soft wrap, inlay hints,
 * sticky context lines, line numbers, whitespace, and breadcrumbs.
 */
object HelixVisualToggleActions {

    fun toggleSoftWrap(editor: Editor, enable: Boolean? = null): Boolean {
        val current = editor.settings.isUseSoftWraps
        val target = enable ?: !current
        if (enable == null || enable != current) {
            val executed = HelixActionDelegate.executeAction("EditorToggleUseSoftWraps", editor)
            if (!executed || editor.settings.isUseSoftWraps != target) {
                editor.settings.isUseSoftWraps = target
            }
        }
        return true
    }

    fun toggleInlayHints(editor: Editor): Boolean =
        HelixActionDelegate.executeAction("ToggleInlayHintsGloballyAction", editor)

    fun toggleStickyContext(editor: Editor): Boolean =
        HelixActionDelegate.executeAction("EditorGutterToggleGlobalStickyLines", editor)

    fun toggleLineNumbers(editor: Editor, enable: Boolean? = null): Boolean {
        val current = editor.settings.isLineNumbersShown
        val target = enable ?: !current
        if (enable == null || enable != current) {
            val executed = HelixActionDelegate.executeAction("EditorToggleShowLineNumbers", editor)
            if (!executed || editor.settings.isLineNumbersShown != target) {
                editor.settings.isLineNumbersShown = target
            }
        }
        return true
    }

    fun toggleRelativeLineNumbers(): Boolean {
        HelixLineNumberManager.toggleRelative()
        return true
    }

    fun toggleWhitespace(editor: Editor, enable: Boolean? = null): Boolean {
        val current = editor.settings.isWhitespacesShown
        val target = enable ?: !current
        if (enable == null || enable != current) {
            val executed = HelixActionDelegate.executeAction("EditorToggleShowWhitespaces", editor)
            if (!executed || editor.settings.isWhitespacesShown != target) {
                editor.settings.isWhitespacesShown = target
            }
        }
        return true
    }

    fun toggleBreadcrumbs(editor: Editor, enable: Boolean? = null): Boolean {
        val externalizable = EditorSettingsExternalizable.getInstance()
        val current = externalizable.isBreadcrumbsShown
        val target = enable ?: !current
        if (enable == null || enable != current) {
            val executed = HelixActionDelegate.executeAction("EditorToggleShowBreadcrumbs", editor)
            if (!executed || externalizable.isBreadcrumbsShown != target) {
                externalizable.isBreadcrumbsShown = target
            }
        }
        return true
    }

    fun handleSetOption(key: String, value: String, editor: Editor): Boolean {
        val normalizedKey = key.trim().lowercase().replace('_', '-')
        val booleanVal = parseBooleanValue(value)

        return when (normalizedKey) {
            "soft-wrap", "soft-wrap.enable", "wrap" -> {
                if (booleanVal == null) return false
                toggleSoftWrap(editor, booleanVal)
            }

            "inlay-hints", "inlay-hints.enable", "hints" -> {
                if (booleanVal == null) return false
                toggleInlayHints(editor)
            }

            "whitespace", "whitespace.render", "whitespaces" -> {
                if (booleanVal == null) return false
                toggleWhitespace(editor, booleanVal)
            }

            "line-number", "line-numbers", "numbers" -> {
                val mode = parseLineNumberMode(value)
                if (mode != null) {
                    HelixSettings.instance.lineNumberMode = mode
                    HelixEditorEligibility.refreshAllEditors()
                    true
                } else if (booleanVal != null) {
                    toggleLineNumbers(editor, booleanVal)
                } else {
                    false
                }
            }

            "sticky-context", "sticky-context.enable", "sticky-lines" -> {
                if (booleanVal == null) return false
                toggleStickyContext(editor)
            }

            "breadcrumbs", "breadcrumbs.enable" -> {
                if (booleanVal == null) return false
                toggleBreadcrumbs(editor, booleanVal)
            }

            "color-modes", "color-modes.enable" -> {
                if (booleanVal == null) return false
                HelixSettings.instance.colorModes = booleanVal
                HelixEditorEligibility.refreshAllEditors()
                true
            }

            "cursor-tint", "cursor-tint.enable", "cursor.tint" -> {
                if (booleanVal == null) return false
                HelixSettings.instance.cursorTint = booleanVal
                HelixEditorEligibility.refreshAllEditors()
                true
            }

            else -> false
        }
    }

    fun handleToggleOption(key: String, editor: Editor): Boolean {
        val normalizedKey = key.trim().lowercase().replace('_', '-')
        return when (normalizedKey) {
            "soft-wrap", "soft-wrap.enable", "wrap" -> toggleSoftWrap(editor)

            "inlay-hints", "inlay-hints.enable", "hints" -> toggleInlayHints(editor)

            "whitespace", "whitespace.render", "whitespaces" -> toggleWhitespace(editor)

            "line-number", "line-numbers", "numbers" -> toggleLineNumbers(editor)

            "relative-line-number", "relative-line-numbers", "relative-numbers", "rnu" ->
                toggleRelativeLineNumbers()

            "sticky-context", "sticky-context.enable", "sticky-lines" -> toggleStickyContext(editor)

            "breadcrumbs", "breadcrumbs.enable" -> toggleBreadcrumbs(editor)

            "color-modes", "color-modes.enable" -> {
                HelixSettings.instance.colorModes = !HelixSettings.instance.colorModes
                HelixEditorEligibility.refreshAllEditors()
                true
            }

            "cursor-tint", "cursor-tint.enable", "cursor.tint" -> {
                HelixSettings.instance.cursorTint = !HelixSettings.instance.cursorTint
                HelixEditorEligibility.refreshAllEditors()
                true
            }

            else -> false
        }
    }

    private fun parseLineNumberMode(value: String): HelixLineNumberMode? = when (value.trim().lowercase()) {
        "relative" -> HelixLineNumberMode.RELATIVE
        "smart", "smart-relative" -> HelixLineNumberMode.SMART
        "hybrid" -> HelixLineNumberMode.HYBRID
        "absolute" -> HelixLineNumberMode.ABSOLUTE
        "default", "ide", "ide-default" -> HelixLineNumberMode.IDE_DEFAULT
        "off", "none" -> HelixLineNumberMode.OFF
        else -> null
    }

    private fun parseBooleanValue(rawVal: String): Boolean? = when (rawVal.trim().lowercase()) {
        "true", "on", "yes", "enable", "enabled", "all", "absolute" -> true
        "false", "off", "no", "disable", "disabled", "none" -> false
        else -> null
    }
}
