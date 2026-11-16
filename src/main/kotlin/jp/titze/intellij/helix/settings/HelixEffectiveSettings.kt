// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import jp.titze.intellij.helix.config.HelixEditorOptions
import jp.titze.intellij.helix.config.HelixOptionOverride
import jp.titze.intellij.helix.config.OptionStatus
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.state.HelixStateManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for effective editor settings, implementing layered precedence:
 * :set-option (session only) › TOML [editor] › Settings UI (persisted) › built-in default
 */
object HelixEffectiveSettings {

    @Volatile
    private var tomlOverrides: Map<String, HelixOptionOverride> = emptyMap()

    private val sessionOverrides = ConcurrentHashMap<String, Any?>()
    private val changeListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in changeListeners) {
            try {
                listener()
            } catch (_: Throwable) {
                // Ignore listener exceptions
            }
        }
    }

    fun setTomlOverrides(overrides: Map<String, HelixOptionOverride>) {
        tomlOverrides = overrides
        notifyListeners()
    }

    fun applyTomlOverrides(overrides: Map<String, HelixOptionOverride>) {
        setTomlOverrides(overrides)
    }

    fun setSessionOption(key: String, value: Any?) {
        val norm = HelixEditorOptions.normalizeKey(key)
        val def = HelixEditorOptions.findDefinition(norm)
        val canonicalKey = def?.key ?: norm
        sessionOverrides[canonicalKey] = value
        notifyListeners()
    }

    fun setSessionOverride(key: String, value: Any?) {
        setSessionOption(key, value)
    }

    fun clearSessionOverrides() {
        sessionOverrides.clear()
        notifyListeners()
    }

    fun clearSessionOverride(key: String) {
        val norm = HelixEditorOptions.normalizeKey(key)
        val def = HelixEditorOptions.findDefinition(norm)
        val canonicalKey = def?.key ?: norm
        sessionOverrides.remove(canonicalKey)
        notifyListeners()
    }

    fun clearTomlOverrides() {
        tomlOverrides = emptyMap()
        notifyListeners()
    }

    fun clearAllOverrides() {
        clearSessionOverrides()
        clearTomlOverrides()
    }

    fun isOverridden(key: String): Boolean = getOverride(key) != null

    fun getOverride(key: String): HelixOptionOverride? {
        val norm = HelixEditorOptions.normalizeKey(key)
        val def = HelixEditorOptions.findDefinition(norm)
        val canonicalKey = def?.key ?: norm
        val override = tomlOverrides[canonicalKey]
        return if (override != null && override.status == OptionStatus.APPLIED) override else null
    }

    fun getAllOverrides(): List<HelixOptionOverride> = tomlOverrides.values.toList()

    // Effective settings properties

    val lineNumberMode: HelixLineNumberMode
        get() {
            sessionOverrides["line-number"]?.let { if (it is HelixLineNumberMode) return it }
            getOverride("line-number")?.value?.let { if (it is HelixLineNumberMode) return it }
            return HelixSettings.instance.lineNumberMode
        }

    val colorModes: Boolean
        get() {
            sessionOverrides["color-modes"]?.let { if (it is Boolean) return it }
            getOverride("color-modes")?.value?.let { if (it is Boolean) return it }
            return HelixSettings.instance.colorModes
        }

    val cursorTint: Boolean
        get() {
            sessionOverrides["cursor-tint"]?.let { if (it is Boolean) return it }
            getOverride("cursor-tint")?.value?.let { if (it is Boolean) return it }
            return HelixSettings.instance.cursorTint
        }

    val cursorShapeNormal: HelixCursorShape
        get() {
            sessionOverrides["cursor-shape.normal"]?.let { if (it is HelixCursorShape) return it }
            getOverride("cursor-shape.normal")?.value?.let { if (it is HelixCursorShape) return it }
            return HelixSettings.instance.cursorShapeNormal
        }

    val cursorShapeInsert: HelixCursorShape
        get() {
            sessionOverrides["cursor-shape.insert"]?.let { if (it is HelixCursorShape) return it }
            getOverride("cursor-shape.insert")?.value?.let { if (it is HelixCursorShape) return it }
            return HelixSettings.instance.cursorShapeInsert
        }

    val cursorShapeSelect: HelixCursorShape
        get() {
            sessionOverrides["cursor-shape.select"]?.let { if (it is HelixCursorShape) return it }
            getOverride("cursor-shape.select")?.value?.let { if (it is HelixCursorShape) return it }
            return HelixSettings.instance.cursorShapeSelect
        }

    val searchSmartCase: Boolean
        get() {
            sessionOverrides["search.smart-case"]?.let { if (it is Boolean) return it }
            getOverride("search.smart-case")?.value?.let { if (it is Boolean) return it }
            return HelixSettings.instance.searchSmartCase
        }

    val searchWrapAround: Boolean
        get() {
            sessionOverrides["search.wrap-around"]?.let { if (it is Boolean) return it }
            getOverride("search.wrap-around")?.value?.let { if (it is Boolean) return it }
            return HelixSettings.instance.searchWrapAround
        }

    val jumpLabelAlphabet: String
        get() {
            sessionOverrides["jump-label-alphabet"]?.let { if (it is String && it.isNotEmpty()) return it }
            getOverride("jump-label-alphabet")?.value?.let { if (it is String && it.isNotEmpty()) return it }
            return HelixSettings.instance.jumpLabelAlphabet
        }

    val scrolloff: Int?
        get() {
            sessionOverrides["scrolloff"]?.let { if (it is Int) return it }
            getOverride("scrolloff")?.value?.let { if (it is Int) return it }
            return null
        }

    val cursorline: Boolean?
        get() {
            sessionOverrides["cursorline"]?.let { if (it is Boolean) return it }
            getOverride("cursorline")?.value?.let { if (it is Boolean) return it }
            return null
        }

    @Suppress("UNCHECKED_CAST")
    val rulers: List<Int>
        get() {
            sessionOverrides["rulers"]?.let { if (it is List<*>) return it as List<Int> }
            getOverride("rulers")?.value?.let { if (it is List<*>) return it as List<Int> }
            return emptyList()
        }

    val softWrap: Boolean?
        get() {
            sessionOverrides["soft-wrap.enable"]?.let { if (it is Boolean) return it }
            getOverride("soft-wrap.enable")?.value?.let { if (it is Boolean) return it }
            return null
        }

    val whitespaceRender: Boolean?
        get() {
            sessionOverrides["whitespace.render"]?.let { if (it is Boolean) return it }
            getOverride("whitespace.render")?.value?.let { if (it is Boolean) return it }
            return null
        }

    val indentGuides: Boolean?
        get() {
            sessionOverrides["indent-guides.render"]?.let { if (it is Boolean) return it }
            getOverride("indent-guides.render")?.value?.let { if (it is Boolean) return it }
            return null
        }

    fun applyAllToEditor(editor: Editor) {
        if (editor.isDisposed) return
        scrolloff?.let { editor.settings.verticalScrollOffset = it }
        cursorline?.let { editor.settings.isCaretRowShown = it }
        val activeRulers = rulers
        if (activeRulers.isNotEmpty()) {
            editor.settings.setSoftMargins(activeRulers)
        }
        indentGuides?.let { editor.settings.isIndentGuidesShown = it }
        softWrap?.let { editor.settings.isUseSoftWraps = it }
        whitespaceRender?.let { editor.settings.isWhitespacesShown = it }
        val state = HelixStateManager.get(editor)
        if (state != null) {
            HelixStateManager.updateCursor(editor, state.mode)
        }
    }

    fun applyAllToAllEditors() {
        EditorFactory.getInstance().allEditors.forEach { editor ->
            if (HelixEditorEligibility.isActive(editor)) {
                applyAllToEditor(editor)
            }
        }
    }
}
