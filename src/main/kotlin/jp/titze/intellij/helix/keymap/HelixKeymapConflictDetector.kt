// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import jp.titze.intellij.helix.config.HelixParsedBinding
import jp.titze.intellij.helix.state.HelixMode

/**
 * Represents a keymap conflict where a key sequence has an action assigned but is shadowed by child chords.
 */
data class KeymapConflict(
    val mode: HelixMode,
    val rawSequence: String,
    val displayShortcut: String,
    val action: String,
    val childChords: List<String>,
    val description: String,
)

/**
 * Detects prefix collisions and shadowed bindings in Helix keymap tries.
 */
object HelixKeymapConflictDetector {

    private const val MAX_CHILD_SUMMARY_CHORDS = 5

    /**
     * Traverses a trie and finds all nodes that have an action but are shadowed by child chords.
     */
    fun detectConflicts(mode: HelixMode, trie: HelixKeymapTrie): List<KeymapConflict> {
        val conflicts = mutableListOf<KeymapConflict>()
        traverse(trie.root, mutableListOf(), mode, conflicts)
        return conflicts
    }

    /**
     * Checks all active runtime tries across Normal, Select, and Insert modes.
     */
    fun detectAllActiveConflicts(): List<KeymapConflict> {
        val result = mutableListOf<KeymapConflict>()
        for (mode in arrayOf(HelixMode.NORMAL, HelixMode.SELECT, HelixMode.INSERT)) {
            val trie = HelixKeymapManager.getActiveTrie(mode)
            result.addAll(detectConflicts(mode, trie))
        }
        return result
    }

    /**
     * Detects conflicts that would arise if a set of parsed TOML bindings were applied over base tries.
     */
    fun detectConflictsInBindings(
        bindings: List<HelixParsedBinding>,
        baseTrieProvider: (HelixMode) -> HelixKeymapTrie = { HelixDefaultKeymap.buildDefaultTrie(it) },
    ): List<KeymapConflict> {
        val conflicts = mutableListOf<KeymapConflict>()
        val modes = arrayOf(HelixMode.NORMAL, HelixMode.SELECT, HelixMode.INSERT)

        for (mode in modes) {
            val modeBindings = bindings.filter { it.mode == mode }
            if (modeBindings.isEmpty()) continue

            val trie = baseTrieProvider(mode).copy()
            for (binding in modeBindings) {
                applyParsedBindingToTrie(trie, binding)
            }
            conflicts.addAll(detectConflicts(mode, trie))
        }

        return conflicts
    }

    private fun applyParsedBindingToTrie(trie: HelixKeymapTrie, binding: HelixParsedBinding) {
        val strokes = HelixKeymapManager.parseSequence(binding.sequence)
        if (strokes.isEmpty()) return
        val action = when (val act = binding.action) {
            is String -> act
            is List<*> -> act.filterIsInstance<String>().joinToString(";")
            else -> return
        }
        trie.bind(strokes, action)
    }

    private fun traverse(
        node: HelixKeymapNode,
        currentPath: MutableList<HelixKeyStroke>,
        mode: HelixMode,
        outConflicts: MutableList<KeymapConflict>,
    ) {
        val action = node.action
        if (action != null && node.children.isNotEmpty() && currentPath.isNotEmpty()) {
            val raw = HelixKeymapExtractor.formatRawSequence(currentPath)
            val display = HelixKeymapExtractor.formatDisplayShortcut(currentPath)
            val childChords = collectChildChords(node, currentPath)
            val summary = formatChildSummary(childChords)
            val desc = "Shortcut '$display' binds '$action', but is shadowed by child chords: $summary"

            outConflicts.add(
                KeymapConflict(
                    mode = mode,
                    rawSequence = raw,
                    displayShortcut = display,
                    action = action,
                    childChords = childChords,
                    description = desc,
                ),
            )
        }

        for ((stroke, child) in node.children) {
            currentPath.add(stroke)
            traverse(child, currentPath, mode, outConflicts)
            currentPath.removeAt(currentPath.size - 1)
        }
    }

    private fun collectChildChords(node: HelixKeymapNode, currentPath: List<HelixKeyStroke>): List<String> {
        val result = mutableListOf<String>()
        for ((stroke, child) in node.children) {
            collectLeafShortcuts(child, currentPath + stroke, result)
        }
        return result
    }

    private fun collectLeafShortcuts(
        node: HelixKeymapNode,
        currentPath: List<HelixKeyStroke>,
        out: MutableList<String>,
    ) {
        if (out.size >= MAX_CHILD_SUMMARY_CHORDS * 2) return
        if (node.action != null) {
            out.add(HelixKeymapExtractor.formatDisplayShortcut(currentPath))
        }
        for ((stroke, child) in node.children) {
            collectLeafShortcuts(child, currentPath + stroke, out)
        }
    }

    private fun formatChildSummary(childChords: List<String>): String =
        if (childChords.size > MAX_CHILD_SUMMARY_CHORDS) {
            childChords.take(MAX_CHILD_SUMMARY_CHORDS).joinToString(", ") +
                " and ${childChords.size - MAX_CHILD_SUMMARY_CHORDS} more"
        } else {
            childChords.joinToString(", ")
        }
}
