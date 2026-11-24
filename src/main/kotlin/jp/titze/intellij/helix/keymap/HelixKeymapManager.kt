// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.project.Project
import jp.titze.intellij.helix.state.HelixMode
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active keymap tries for all editor modes (Normal, Select, Insert).
 */
object HelixKeymapManager {

    private val defaultKeymaps = ConcurrentHashMap<HelixMode, HelixKeymapTrie>()
    private val activeKeymaps = ConcurrentHashMap<HelixMode, HelixKeymapTrie>()
    private val projectKeymaps = ConcurrentHashMap<String, ConcurrentHashMap<HelixMode, HelixKeymapTrie>>()
    private val projectBindings = ConcurrentHashMap<String, MutableSet<Pair<HelixMode, String>>>()

    init {
        resetToDefaults()
    }

    fun resetToDefaults() {
        defaultKeymaps.clear()
        activeKeymaps.clear()
        projectKeymaps.clear()
        projectBindings.clear()
        for (mode in HelixMode.entries) {
            val trie = HelixDefaultKeymap.buildDefaultTrie(mode)
            defaultKeymaps[mode] = trie
            activeKeymaps[mode] = trie.copy()
        }
        jp.titze.intellij.helix.settings.HelixEffectiveSettings.clearSessionOverrides()
    }

    fun getProjectKey(project: Project): String = project.basePath ?: project.locationHash

    fun initProjectKeymap(project: Project) {
        val key = getProjectKey(project)
        val modeMap = ConcurrentHashMap<HelixMode, HelixKeymapTrie>()
        for (mode in HelixMode.entries) {
            modeMap[mode] = getActiveTrie(mode).copy()
        }
        projectKeymaps[key] = modeMap
        projectBindings[key] = ConcurrentHashMap.newKeySet()
    }

    fun clearProject(project: Project) {
        val key = getProjectKey(project)
        projectKeymaps.remove(key)
        projectBindings.remove(key)
    }

    fun isProjectBinding(mode: HelixMode, sequence: String, project: Project?): Boolean {
        if (project == null || project.isDisposed) return false
        val key = getProjectKey(project)
        return projectBindings[key]?.contains(mode to sequence) == true
    }

    fun getActiveTrie(mode: HelixMode, project: Project? = null): HelixKeymapTrie {
        if (project != null && !project.isDisposed) {
            val key = getProjectKey(project)
            projectKeymaps[key]?.get(mode)?.let { return it }
        }
        return activeKeymaps[mode] ?: defaultKeymaps.getOrPut(mode) {
            HelixDefaultKeymap.buildDefaultTrie(mode)
        }
    }

    fun bind(
        mode: HelixMode,
        sequence: String,
        command: String,
        label: String? = null,
        intelliJAction: String? = null,
        project: Project? = null,
    ) {
        val strokes = parseSequence(sequence)
        getActiveTrie(mode, project).bind(strokes, command, label, intelliJAction)
        if (project != null && !project.isDisposed) {
            val key = getProjectKey(project)
            projectBindings.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(mode to sequence)
        }
    }

    fun resolve(mode: HelixMode, strokes: List<HelixKeyStroke>, project: Project? = null): HelixKeymapResult =
        getActiveTrie(mode, project).resolve(strokes)

    fun resolveStep(
        mode: HelixMode,
        currentPendingSeq: String,
        nextStroke: HelixKeyStroke,
        project: Project? = null,
    ): HelixKeymapResult {
        val strokes = if (currentPendingSeq.isEmpty()) {
            listOf(nextStroke)
        } else {
            val prefixStrokes = parsePendingPrefix(currentPendingSeq)
            prefixStrokes + nextStroke
        }
        return resolve(mode, strokes, project)
    }

    fun getBranchChildren(
        mode: HelixMode,
        prefix: String,
        project: Project? = null,
    ): Map<HelixKeyStroke, HelixKeymapNode>? {
        val strokes = parsePendingPrefix(prefix)
        if (strokes.isEmpty()) return null
        val result = resolve(mode, strokes, project)
        return if (result is HelixKeymapResult.Branch) {
            result.node.children
        } else {
            null
        }
    }

    private fun parsePendingPrefix(pendingSeq: String): List<HelixKeyStroke> = when (pendingSeq) {
        " " -> listOf(HelixKeyStroke.fromString("space"))

        " G" -> listOf(HelixKeyStroke.fromString("space"), HelixKeyStroke('G'))

        " m" -> listOf(HelixKeyStroke.fromString("space"), HelixKeyStroke('m'))

        " t" -> listOf(HelixKeyStroke.fromString("space"), HelixKeyStroke('t'))

        " v" -> listOf(HelixKeyStroke.fromString("space"), HelixKeyStroke('v'))

        "C-w" -> listOf(HelixKeyStroke.fromString("C-w"))

        "C-w n" -> listOf(HelixKeyStroke.fromString("C-w"), HelixKeyStroke('n'))

        else -> {
            if (pendingSeq.startsWith(" ") && pendingSeq.length > 1) {
                listOf(HelixKeyStroke.fromString("space")) + parseSequence(pendingSeq.drop(1))
            } else if (pendingSeq.length == 1) {
                listOf(HelixKeyStroke.fromChar(pendingSeq[0]))
            } else {
                parseSequence(pendingSeq)
            }
        }
    }

    fun parseSequence(seq: String): List<HelixKeyStroke> {
        val trimmed = seq.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parts = trimmed.split("\\s+".toRegex())
        return parts.map { HelixKeyStroke.fromString(it) }
    }
}
