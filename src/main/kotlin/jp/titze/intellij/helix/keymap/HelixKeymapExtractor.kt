// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import jp.titze.intellij.helix.config.HelixConfigLoader
import jp.titze.intellij.helix.state.HelixMode

enum class BindingSource(val displayName: String) {
    DEFAULT("Default"),
    CUSTOM_INTELLIJ("custom: intellij"),
    CUSTOM_CONFIG("custom: config"),
    NO_OP("no_op"),
}

enum class KeymapPrefixGroup(val displayName: String) {
    ALL("All Chords"),
    SPACE("Space Chords (space)"),
    GOTO("Goto Chords (g)"),
    VIEW("View Chords (z)"),
    MATCH("Match Chords (m)"),
    WINDOW("Window Chords (C-w)"),
    BRACKET("Bracket Chords ([ / ])"),
    STANDALONE("Single Keys & Other"),
}

data class HelixKeybindingRow(
    val mode: HelixMode,
    val rawSequence: String,
    val displayShortcut: String,
    val command: String,
    val description: String,
    val source: BindingSource,
    val prefixGroup: KeymapPrefixGroup,
    val isShadowed: Boolean = false,
    val conflictReason: String? = null,
)

/**
 * Extracts and flattens active HelixKeymapTrie trees into tabular rows for UI inspection.
 */
object HelixKeymapExtractor {

    fun extractRows(
        mode: HelixMode,
        activeTrie: HelixKeymapTrie = HelixKeymapManager.getActiveTrie(mode),
    ): List<HelixKeybindingRow> {
        val rows = mutableListOf<HelixKeybindingRow>()
        val defaultTrie = HelixDefaultKeymap.buildDefaultTrie(mode)
        traverse(activeTrie.root, mutableListOf(), mode, defaultTrie, rows)
        return rows.sortedWith(compareBy({ it.prefixGroup.ordinal }, { it.displayShortcut }))
    }

    private fun traverse(
        node: HelixKeymapNode,
        currentPath: MutableList<HelixKeyStroke>,
        mode: HelixMode,
        defaultTrie: HelixKeymapTrie,
        outRows: MutableList<HelixKeybindingRow>,
    ) {
        val action = node.action
        if (action != null && currentPath.isNotEmpty()) {
            val rawSequence = formatRawSequence(currentPath)
            val displayShortcut = formatDisplayShortcut(currentPath)
            val source = determineSource(currentPath, action, mode, defaultTrie)
            val prefixGroup = determinePrefixGroup(rawSequence)
            val description = resolveDescription(node, action)
            val isShadowed = node.children.isNotEmpty()
            val conflictReason = if (isShadowed) {
                "Shadowed: shortcut '$displayShortcut' binds '$action', but child chords take precedence"
            } else {
                null
            }

            outRows.add(
                HelixKeybindingRow(
                    mode = mode,
                    rawSequence = rawSequence,
                    displayShortcut = displayShortcut,
                    command = action,
                    description = description,
                    source = source,
                    prefixGroup = prefixGroup,
                    isShadowed = isShadowed,
                    conflictReason = conflictReason,
                ),
            )
        }

        for ((stroke, child) in node.children) {
            currentPath.add(stroke)
            traverse(child, currentPath, mode, defaultTrie, outRows)
            currentPath.removeAt(currentPath.size - 1)
        }
    }

    fun formatRawSequence(strokes: List<HelixKeyStroke>): String = strokes.joinToString(" ") { it.toDisplayString() }

    fun formatDisplayShortcut(strokes: List<HelixKeyStroke>): String =
        strokes.joinToString(" + ") { it.toDisplayString() }

    fun determineSource(
        strokes: List<HelixKeyStroke>,
        command: String,
        mode: HelixMode,
        defaultTrie: HelixKeymapTrie? = null,
    ): BindingSource {
        if (command == "no_op") return BindingSource.NO_OP

        val trie = defaultTrie ?: HelixDefaultKeymap.buildDefaultTrie(mode)
        val defaultResult = trie.resolve(strokes)
        if (defaultResult is HelixKeymapResult.Leaf && defaultResult.command == command) {
            return BindingSource.DEFAULT
        }

        val configFile = HelixConfigLoader.findConfigFile()
        return if (configFile != null && HelixConfigLoader.isCliConfigFile(configFile)) {
            BindingSource.CUSTOM_CONFIG
        } else {
            BindingSource.CUSTOM_INTELLIJ
        }
    }

    fun determinePrefixGroup(rawSequence: String): KeymapPrefixGroup {
        val trimmed = rawSequence.trim()
        return when {
            trimmed.startsWith("space") -> KeymapPrefixGroup.SPACE

            trimmed == "g" || trimmed.startsWith("g ") -> KeymapPrefixGroup.GOTO

            trimmed == "z" || trimmed.startsWith("z ") || trimmed == "Z" || trimmed.startsWith("Z ") ->
                KeymapPrefixGroup.VIEW

            trimmed == "m" || trimmed.startsWith("m ") -> KeymapPrefixGroup.MATCH

            trimmed == "C-w" || trimmed.startsWith("C-w ") -> KeymapPrefixGroup.WINDOW

            trimmed.startsWith("[") || trimmed.startsWith("]") -> KeymapPrefixGroup.BRACKET

            else -> KeymapPrefixGroup.STANDALONE
        }
    }

    fun resolveDescription(node: HelixKeymapNode, command: String): String {
        val explicitLabel = node.label?.trim()
        if (!explicitLabel.isNullOrEmpty()) return explicitLabel

        val regCmd = HelixCommandRegistry.get(command)
        if (regCmd != null && regCmd.description.isNotEmpty()) {
            return regCmd.description
        }

        if (command.startsWith(":action ")) {
            val actionId = command.removePrefix(":action ").trim()
            return "Action: $actionId"
        }

        if (command == "no_op") {
            return "(Disabled)"
        }

        return command.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
