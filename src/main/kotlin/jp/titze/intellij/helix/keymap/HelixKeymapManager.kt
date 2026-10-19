// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import jp.titze.intellij.helix.state.HelixMode
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active keymap tries for all editor modes (Normal, Select, Insert).
 */
object HelixKeymapManager {

    private val defaultKeymaps = ConcurrentHashMap<HelixMode, HelixKeymapTrie>()
    private val activeKeymaps = ConcurrentHashMap<HelixMode, HelixKeymapTrie>()

    init {
        resetToDefaults()
    }

    fun resetToDefaults() {
        defaultKeymaps.clear()
        activeKeymaps.clear()
        for (mode in HelixMode.values()) {
            val trie = HelixDefaultKeymap.buildDefaultTrie(mode)
            defaultKeymaps[mode] = trie
            activeKeymaps[mode] = trie.copy()
        }
    }

    fun getActiveTrie(mode: HelixMode): HelixKeymapTrie = activeKeymaps[mode] ?: defaultKeymaps.getOrPut(mode) {
        HelixDefaultKeymap.buildDefaultTrie(mode)
    }

    fun bind(
        mode: HelixMode,
        sequence: String,
        command: String,
        label: String? = null,
        intelliJAction: String? = null,
    ) {
        val strokes = parseSequence(sequence)
        getActiveTrie(mode).bind(strokes, command, label, intelliJAction)
    }

    fun unbind(mode: HelixMode, sequence: String) {
        val strokes = parseSequence(sequence)
        getActiveTrie(mode).unbind(strokes)
    }

    fun resolve(mode: HelixMode, strokes: List<HelixKeyStroke>): HelixKeymapResult =
        getActiveTrie(mode).resolve(strokes)

    fun resolveStep(mode: HelixMode, currentPendingSeq: String, nextStroke: HelixKeyStroke): HelixKeymapResult {
        val strokes = if (currentPendingSeq.isEmpty()) {
            listOf(nextStroke)
        } else {
            val prefixStrokes = parsePendingPrefix(currentPendingSeq)
            prefixStrokes + nextStroke
        }
        return resolve(mode, strokes)
    }

    fun getBranchChildren(mode: HelixMode, prefix: String): Map<HelixKeyStroke, HelixKeymapNode>? {
        val strokes = parsePendingPrefix(prefix)
        if (strokes.isEmpty()) return null
        val result = resolve(mode, strokes)
        return if (result is HelixKeymapResult.Branch) {
            result.node.children
        } else {
            null
        }
    }

    private fun parsePendingPrefix(pendingSeq: String): List<HelixKeyStroke> = when (pendingSeq) {
        " " -> listOf(HelixKeyStroke.fromString("space"))

        " G" -> listOf(HelixKeyStroke.fromString("space"), HelixKeyStroke('G'))

        "C-w" -> listOf(HelixKeyStroke.fromString("C-w"))

        "C-w n" -> listOf(HelixKeyStroke.fromString("C-w"), HelixKeyStroke('n'))

        else -> {
            if (pendingSeq.length == 1) {
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
