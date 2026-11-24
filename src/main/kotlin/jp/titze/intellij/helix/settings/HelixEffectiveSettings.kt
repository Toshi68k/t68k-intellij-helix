// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import jp.titze.intellij.helix.config.HelixEditorOptions
import jp.titze.intellij.helix.config.HelixOptionOverride
import jp.titze.intellij.helix.config.OptionStatus
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.state.HelixStateManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for effective editor settings, implementing layered precedence:
 * :set-option (session only) › Project TOML (.helix/intellij.toml) › Global TOML (~/.config/helix/intellij.toml)
 * › Settings UI (persisted) › built-in default
 */
object HelixEffectiveSettings {

    @Volatile
    private var tomlOverrides: Map<String, HelixOptionOverride> = emptyMap()

    private val projectTomlOverrides = ConcurrentHashMap<String, Map<String, HelixOptionOverride>>()
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

    fun getProjectKey(project: Project): String = project.basePath ?: project.locationHash

    fun setProjectTomlOverrides(project: Project, overrides: Map<String, HelixOptionOverride>) {
        val key = getProjectKey(project)
        if (overrides.isEmpty()) {
            projectTomlOverrides.remove(key)
        } else {
            projectTomlOverrides[key] = overrides
        }
        notifyListeners()
    }

    fun clearProjectOverrides(project: Project) {
        projectTomlOverrides.remove(getProjectKey(project))
        notifyListeners()
    }

    fun clearAllProjectOverrides() {
        projectTomlOverrides.clear()
        notifyListeners()
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
        clearAllProjectOverrides()
    }

    fun isOverridden(key: String, project: Project? = null): Boolean = getOverride(key, project) != null

    fun getOverride(key: String, project: Project? = null): HelixOptionOverride? {
        val norm = HelixEditorOptions.normalizeKey(key)
        val def = HelixEditorOptions.findDefinition(norm)
        val canonicalKey = def?.key ?: norm
        if (project != null && !project.isDisposed) {
            val keyProj = getProjectKey(project)
            val projOverride = projectTomlOverrides[keyProj]?.get(canonicalKey)
            if (projOverride != null && projOverride.status == OptionStatus.APPLIED) {
                return projOverride
            }
        }
        val override = tomlOverrides[canonicalKey]
        return if (override != null && override.status == OptionStatus.APPLIED) override else null
    }

    fun getAllOverrides(project: Project? = null): List<HelixOptionOverride> {
        if (project == null || project.isDisposed) {
            return tomlOverrides.values.toList()
        }
        val projMap = projectTomlOverrides[getProjectKey(project)] ?: emptyMap()
        val merged = LinkedHashMap<String, HelixOptionOverride>(tomlOverrides)
        merged.putAll(projMap)
        return merged.values.toList()
    }

    fun getEffectiveValue(key: String, project: Project? = null): Any? {
        val norm = HelixEditorOptions.normalizeKey(key)
        val def = HelixEditorOptions.findDefinition(norm)
        val canonicalKey = def?.key ?: norm
        sessionOverrides[canonicalKey]?.let { return it }
        getOverride(canonicalKey, project)?.value?.let { return it }
        return when (canonicalKey) {
            "line-number" -> getEffectiveLineNumberMode(project)
            "color-modes" -> getEffectiveColorModes(project)
            "cursor-tint" -> getEffectiveCursorTint(project)
            "cursor-shape.normal" -> getEffectiveCursorShapeNormal(project)
            "cursor-shape.insert" -> getEffectiveCursorShapeInsert(project)
            "cursor-shape.select" -> getEffectiveCursorShapeSelect(project)
            "search.smart-case" -> getEffectiveSearchSmartCase(project)
            "search.wrap-around" -> getEffectiveSearchWrapAround(project)
            "jump-label-alphabet" -> getEffectiveJumpLabelAlphabet(project)
            "scrolloff" -> getEffectiveScrolloff(project)
            "cursorline" -> getEffectiveCursorline(project)
            "rulers" -> getEffectiveRulers(project)
            "soft-wrap.enable" -> getEffectiveSoftWrap(project)
            "whitespace.render" -> getEffectiveWhitespaceRender(project)
            "indent-guides.render" -> getEffectiveIndentGuides(project)
            else -> null
        }
    }

    // Effective settings accessors with optional project scope

    fun getEffectiveLineNumberMode(project: Project? = null): HelixLineNumberMode {
        sessionOverrides["line-number"]?.let { if (it is HelixLineNumberMode) return it }
        getOverride("line-number", project)?.value?.let { if (it is HelixLineNumberMode) return it }
        return HelixSettings.instance.lineNumberMode
    }

    fun getEffectiveColorModes(project: Project? = null): Boolean {
        sessionOverrides["color-modes"]?.let { if (it is Boolean) return it }
        getOverride("color-modes", project)?.value?.let { if (it is Boolean) return it }
        return HelixSettings.instance.colorModes
    }

    fun getEffectiveCursorTint(project: Project? = null): Boolean {
        sessionOverrides["cursor-tint"]?.let { if (it is Boolean) return it }
        getOverride("cursor-tint", project)?.value?.let { if (it is Boolean) return it }
        return HelixSettings.instance.cursorTint
    }

    fun getEffectiveCursorShapeNormal(project: Project? = null): HelixCursorShape {
        sessionOverrides["cursor-shape.normal"]?.let { if (it is HelixCursorShape) return it }
        getOverride("cursor-shape.normal", project)?.value?.let { if (it is HelixCursorShape) return it }
        return HelixSettings.instance.cursorShapeNormal
    }

    fun getEffectiveCursorShapeInsert(project: Project? = null): HelixCursorShape {
        sessionOverrides["cursor-shape.insert"]?.let { if (it is HelixCursorShape) return it }
        getOverride("cursor-shape.insert", project)?.value?.let { if (it is HelixCursorShape) return it }
        return HelixSettings.instance.cursorShapeInsert
    }

    fun getEffectiveCursorShapeSelect(project: Project? = null): HelixCursorShape {
        sessionOverrides["cursor-shape.select"]?.let { if (it is HelixCursorShape) return it }
        getOverride("cursor-shape.select", project)?.value?.let { if (it is HelixCursorShape) return it }
        return HelixSettings.instance.cursorShapeSelect
    }

    fun getEffectiveSearchSmartCase(project: Project? = null): Boolean {
        sessionOverrides["search.smart-case"]?.let { if (it is Boolean) return it }
        getOverride("search.smart-case", project)?.value?.let { if (it is Boolean) return it }
        return HelixSettings.instance.searchSmartCase
    }

    fun getEffectiveSearchWrapAround(project: Project? = null): Boolean {
        sessionOverrides["search.wrap-around"]?.let { if (it is Boolean) return it }
        getOverride("search.wrap-around", project)?.value?.let { if (it is Boolean) return it }
        return HelixSettings.instance.searchWrapAround
    }

    fun getEffectiveJumpLabelAlphabet(project: Project? = null): String {
        sessionOverrides["jump-label-alphabet"]?.let { if (it is String && it.isNotEmpty()) return it }
        getOverride("jump-label-alphabet", project)?.value?.let { if (it is String && it.isNotEmpty()) return it }
        return HelixSettings.instance.jumpLabelAlphabet
    }

    fun getEffectiveScrolloff(project: Project? = null): Int? {
        sessionOverrides["scrolloff"]?.let { if (it is Int) return it }
        getOverride("scrolloff", project)?.value?.let { if (it is Int) return it }
        return null
    }

    fun getEffectiveCursorline(project: Project? = null): Boolean? {
        sessionOverrides["cursorline"]?.let { if (it is Boolean) return it }
        getOverride("cursorline", project)?.value?.let { if (it is Boolean) return it }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun getEffectiveRulers(project: Project? = null): List<Int> {
        sessionOverrides["rulers"]?.let { if (it is List<*>) return it as List<Int> }
        getOverride("rulers", project)?.value?.let { if (it is List<*>) return it as List<Int> }
        return emptyList()
    }

    fun getEffectiveSoftWrap(project: Project? = null): Boolean? {
        sessionOverrides["soft-wrap.enable"]?.let { if (it is Boolean) return it }
        getOverride("soft-wrap.enable", project)?.value?.let { if (it is Boolean) return it }
        return null
    }

    fun getEffectiveWhitespaceRender(project: Project? = null): Boolean? {
        sessionOverrides["whitespace.render"]?.let { if (it is Boolean) return it }
        getOverride("whitespace.render", project)?.value?.let { if (it is Boolean) return it }
        return null
    }

    fun getEffectiveIndentGuides(project: Project? = null): Boolean? {
        sessionOverrides["indent-guides.render"]?.let { if (it is Boolean) return it }
        getOverride("indent-guides.render", project)?.value?.let { if (it is Boolean) return it }
        return null
    }

    // Properties for global/default queries

    val lineNumberMode: HelixLineNumberMode get() = getEffectiveLineNumberMode(null)
    val colorModes: Boolean get() = getEffectiveColorModes(null)
    val cursorTint: Boolean get() = getEffectiveCursorTint(null)
    val cursorShapeNormal: HelixCursorShape get() = getEffectiveCursorShapeNormal(null)
    val cursorShapeInsert: HelixCursorShape get() = getEffectiveCursorShapeInsert(null)
    val cursorShapeSelect: HelixCursorShape get() = getEffectiveCursorShapeSelect(null)
    val searchSmartCase: Boolean get() = getEffectiveSearchSmartCase(null)
    val searchWrapAround: Boolean get() = getEffectiveSearchWrapAround(null)
    val jumpLabelAlphabet: String get() = getEffectiveJumpLabelAlphabet(null)
    val scrolloff: Int? get() = getEffectiveScrolloff(null)
    val cursorline: Boolean? get() = getEffectiveCursorline(null)
    val rulers: List<Int> get() = getEffectiveRulers(null)
    val softWrap: Boolean? get() = getEffectiveSoftWrap(null)
    val whitespaceRender: Boolean? get() = getEffectiveWhitespaceRender(null)
    val indentGuides: Boolean? get() = getEffectiveIndentGuides(null)

    fun applyAllToEditor(editor: Editor) {
        if (editor.isDisposed) return
        val project = editor.project
        getEffectiveScrolloff(project)?.let { editor.settings.verticalScrollOffset = it }
        getEffectiveCursorline(project)?.let { editor.settings.isCaretRowShown = it }
        val activeRulers = getEffectiveRulers(project)
        if (activeRulers.isNotEmpty()) {
            editor.settings.setSoftMargins(activeRulers)
        }
        getEffectiveIndentGuides(project)?.let { editor.settings.isIndentGuidesShown = it }
        getEffectiveSoftWrap(project)?.let { editor.settings.isUseSoftWraps = it }
        getEffectiveWhitespaceRender(project)?.let { editor.settings.isWhitespacesShown = it }
        val state = HelixStateManager.get(editor)
        if (state != null) {
            HelixStateManager.updateCursor(editor, state.mode)
        }
    }

    fun applyAllToProjectEditors(project: Project) {
        if (project.isDisposed) return
        EditorFactory.getInstance().allEditors.forEach { editor ->
            if (editor.project == project && HelixEditorEligibility.isActive(editor)) {
                applyAllToEditor(editor)
            }
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
