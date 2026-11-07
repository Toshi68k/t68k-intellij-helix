// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.macro

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.editor.HelixEscapeHandler
import jp.titze.intellij.helix.keymap.HelixCommandRegistry
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.keymap.HelixKeyStroke
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.keymap.HelixKeymapResult
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup
import java.awt.event.KeyEvent

sealed class HelixMacroToken {
    data class TypedChar(val char: Char) : HelixMacroToken()
    data class SpecialStroke(val stroke: HelixKeyStroke) : HelixMacroToken()
}

/**
 * Executes Helix inline macros defined with '@...' syntax (e.g., '@f"lmi"').
 */
object HelixMacroExecutor {

    private const val MAX_MACRO_DEPTH = 10
    private var executionDepth = 0

    var isExecutingMacro: Boolean = false
        private set

    fun parseMacro(macroText: String): List<HelixMacroToken> {
        val clean = macroText.removePrefix("@")
        if (clean.isEmpty()) return emptyList()

        val tokens = mutableListOf<HelixMacroToken>()
        var i = 0
        while (i < clean.length) {
            val c = clean[i]
            if (c == '<') {
                val closeIdx = clean.indexOf('>', i + 1)
                if (closeIdx != -1) {
                    val rawName = clean.substring(i + 1, closeIdx).trim()
                    tokens.add(parseSpecialToken(rawName))
                    i = closeIdx + 1
                    continue
                }
            }
            tokens.add(HelixMacroToken.TypedChar(c))
            i++
        }
        return tokens
    }

    private fun parseSpecialToken(name: String): HelixMacroToken {
        val lower = name.lowercase()
        return when (lower) {
            "lt" -> HelixMacroToken.TypedChar('<')
            "gt" -> HelixMacroToken.TypedChar('>')
            else -> HelixMacroToken.SpecialStroke(HelixKeyStroke.fromString(name))
        }
    }

    fun execute(macroString: String, editor: Editor, count: Int = 1): Boolean {
        if (executionDepth >= MAX_MACRO_DEPTH) return false
        val tokens = parseMacro(macroString)
        if (tokens.isEmpty()) return true

        executionDepth++
        isExecutingMacro = true
        try {
            repeat(count.coerceAtLeast(1)) {
                for (token in tokens) {
                    executeToken(token, editor)
                }
            }
            return true
        } finally {
            executionDepth--
            if (executionDepth == 0) {
                isExecutingMacro = false
                HelixWhichKeyPopup.hide()
            }
        }
    }

    private fun executeToken(token: HelixMacroToken, editor: Editor) {
        when (token) {
            is HelixMacroToken.TypedChar -> {
                HelixKeyHandler.handleKey(token.char, editor)
            }

            is HelixMacroToken.SpecialStroke -> {
                executeSpecialStroke(token.stroke, editor)
            }
        }
    }

    private fun isEscapeStroke(stroke: HelixKeyStroke): Boolean =
        stroke.keyChar == '\u001b' || stroke.keyCode == KeyEvent.VK_ESCAPE

    private fun isPrintableWithoutModifiers(stroke: HelixKeyStroke): Boolean =
        stroke.keyChar != null && !stroke.isCtrl && !stroke.isAlt && !stroke.isMeta

    private fun executeSpecialStroke(stroke: HelixKeyStroke, editor: Editor) {
        if (isEscapeStroke(stroke)) {
            HelixEscapeHandler.handleEscape(editor)
            return
        }

        if (isPrintableWithoutModifiers(stroke)) {
            HelixKeyHandler.handleKey(stroke.keyChar!!, editor)
            return
        }

        val state = HelixStateManager.getOrCreate(editor)
        val trieResult = HelixKeymapManager.resolve(state.mode, listOf(stroke))
        if (trieResult is HelixKeymapResult.Leaf) {
            HelixCommandRegistry.execute(trieResult.command, editor, 1)
            return
        }

        executeStandardSpecialKey(stroke, editor)
    }

    private fun executeStandardSpecialKey(stroke: HelixKeyStroke, editor: Editor) {
        when (stroke.keyCode) {
            KeyEvent.VK_ENTER -> HelixKeyHandler.handleKey('\n', editor)

            KeyEvent.VK_TAB -> HelixKeyHandler.handleKey('\t', editor)

            KeyEvent.VK_BACK_SPACE -> HelixKeyHandler.handleKey('\b', editor)

            KeyEvent.VK_LEFT -> HelixMotions.moveLeft(editor, 1)

            KeyEvent.VK_RIGHT -> HelixMotions.moveRight(editor, 1)

            KeyEvent.VK_UP -> HelixMotions.moveUp(editor, 1)

            KeyEvent.VK_DOWN -> HelixMotions.moveDown(editor, 1)

            else -> {
                val actionName = stroke.toDisplayString()
                HelixActionDelegate.executeAction(actionName, editor)
            }
        }
    }
}
