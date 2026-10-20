// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

sealed class HelixKeymapResult {
    data class Leaf(val command: String, val label: String? = null, val intelliJAction: String? = null) :
        HelixKeymapResult()

    data class Branch(val node: HelixKeymapNode) : HelixKeymapResult()

    object None : HelixKeymapResult()
}

class HelixKeymapNode(var action: String? = null, var label: String? = null, var intelliJAction: String? = null) {
    val children = LinkedHashMap<HelixKeyStroke, HelixKeymapNode>()

    val isLeaf: Boolean get() = action != null && children.isEmpty()
    val isBranch: Boolean get() = children.isNotEmpty()

    fun findChild(stroke: HelixKeyStroke): HelixKeymapNode? {
        val exact = children[stroke]
        if (exact != null) return exact
        return children.entries.firstOrNull { it.key.matches(stroke) }?.value
    }

    fun copyDeep(): HelixKeymapNode {
        val copy = HelixKeymapNode(action, label, intelliJAction)
        for ((key, child) in children) {
            copy.children[key] = child.copyDeep()
        }
        return copy
    }
}

/**
 * Prefix Trie representing key combinations for a mode.
 */
class HelixKeymapTrie {

    val root = HelixKeymapNode()

    fun bind(keys: List<HelixKeyStroke>, command: String, label: String? = null, intelliJAction: String? = null) {
        if (keys.isEmpty()) return
        var current = root
        for (i in 0 until keys.size - 1) {
            val key = keys[i]
            var next = current.findChild(key)
            if (next == null) {
                next = HelixKeymapNode()
                current.children[key] = next
            } else {
                next.action = null
            }
            current = next
        }

        val lastKey = keys.last()
        var leaf = current.findChild(lastKey)
        if (leaf == null) {
            leaf = HelixKeymapNode(command, label, intelliJAction)
            current.children[lastKey] = leaf
        } else {
            leaf.action = command
            if (label != null) leaf.label = label
            if (intelliJAction != null) leaf.intelliJAction = intelliJAction
        }
    }

    fun bind(key: HelixKeyStroke, command: String, label: String? = null, intelliJAction: String? = null) =
        bind(listOf(key), command, label, intelliJAction)

    fun unbind(keys: List<HelixKeyStroke>) {
        if (keys.isEmpty()) return
        unbindRecursive(root, keys, 0)
    }

    private fun unbindRecursive(node: HelixKeymapNode, keys: List<HelixKeyStroke>, index: Int): Boolean {
        if (index == keys.size) {
            node.action = null
            node.label = null
            node.intelliJAction = null
            return node.children.isEmpty()
        }
        val key = keys[index]
        val child = node.findChild(key) ?: return false
        val shouldDeleteChild = unbindRecursive(child, keys, index + 1)
        if (shouldDeleteChild) {
            node.children.entries.removeIf { it.key.matches(key) }
        }
        return node.action == null && node.children.isEmpty()
    }

    fun resolve(keys: List<HelixKeyStroke>): HelixKeymapResult {
        if (keys.isEmpty()) return HelixKeymapResult.None
        var current = root
        for (key in keys) {
            val next = current.findChild(key) ?: return HelixKeymapResult.None
            current = next
        }
        val act = current.action
        return if (current.isBranch) {
            HelixKeymapResult.Branch(current)
        } else if (act != null) {
            HelixKeymapResult.Leaf(act, current.label, current.intelliJAction)
        } else {
            HelixKeymapResult.None
        }
    }

    fun resolveStep(currentNode: HelixKeymapNode, nextKey: HelixKeyStroke): HelixKeymapResult {
        val child = currentNode.findChild(nextKey) ?: return HelixKeymapResult.None
        val act = child.action
        return if (child.isBranch) {
            HelixKeymapResult.Branch(child)
        } else if (act != null) {
            HelixKeymapResult.Leaf(act, child.label, child.intelliJAction)
        } else {
            HelixKeymapResult.None
        }
    }

    fun copy(): HelixKeymapTrie {
        val trie = HelixKeymapTrie()
        for ((key, child) in root.children) {
            trie.root.children[key] = child.copyDeep()
        }
        return trie
    }
}
