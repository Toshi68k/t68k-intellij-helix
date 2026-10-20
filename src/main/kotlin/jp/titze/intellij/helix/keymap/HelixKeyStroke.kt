// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import java.awt.event.KeyEvent

/**
 * Represents a normalized key combination in a Helix key sequence.
 *
 * Supports single printable characters ('a', 'x', 'G'), special keys
 * ("space", "ret", "tab", "esc"), and modifiers ("C-w", "A-x", "C-S-o").
 */
data class HelixKeyStroke(
    val keyChar: Char? = null,
    val keyCode: Int = KeyEvent.VK_UNDEFINED,
    val isCtrl: Boolean = false,
    val isAlt: Boolean = false,
    val isShift: Boolean = false,
    val isMeta: Boolean = false,
) {

    fun matches(other: HelixKeyStroke): Boolean {
        if (!hasSameModifiers(other)) {
            return false
        }
        if (keyChar != null && other.keyChar != null) {
            return keyChar == other.keyChar
        }
        if (keyCode != KeyEvent.VK_UNDEFINED && other.keyCode != KeyEvent.VK_UNDEFINED) {
            return keyCode == other.keyCode
        }
        return false
    }

    private fun hasSameModifiers(other: HelixKeyStroke): Boolean {
        val ctrlAltMatch = isCtrl == other.isCtrl && isAlt == other.isAlt
        val shiftMetaMatch = isShift == other.isShift && isMeta == other.isMeta
        return ctrlAltMatch && shiftMetaMatch
    }

    fun toDisplayString(): String {
        val sb = StringBuilder()
        if (isCtrl) sb.append("C-")
        if (isAlt) sb.append("A-")
        if (isShift) sb.append("S-")
        if (isMeta) sb.append("M-")

        val name = specialKeyToString(keyCode, keyChar)
        sb.append(name)
        return sb.toString()
    }

    companion object {

        private const val ASCII_CTRL_MIN = 1
        private const val ASCII_CTRL_MAX = 26
        private const val ASCII_CTRL_OFFSET = 96

        fun fromChar(charTyped: Char): HelixKeyStroke {
            val code = charTyped.code
            if (code in ASCII_CTRL_MIN..ASCII_CTRL_MAX && charTyped != '\t' && charTyped != '\n') {
                val letter = (code + ASCII_CTRL_OFFSET).toChar()
                val vk = KeyEvent.VK_A + (letter - 'a')
                return HelixKeyStroke(keyChar = letter, keyCode = vk, isCtrl = true)
            }
            if (charTyped == ' ') {
                return HelixKeyStroke(keyChar = ' ', keyCode = KeyEvent.VK_SPACE)
            }
            if (charTyped == '\n' || charTyped == '\r') {
                return HelixKeyStroke(keyChar = '\n', keyCode = KeyEvent.VK_ENTER)
            }
            if (charTyped == '\t') {
                return HelixKeyStroke(keyChar = '\t', keyCode = KeyEvent.VK_TAB)
            }
            if (charTyped == '\b') {
                return HelixKeyStroke(keyChar = '\b', keyCode = KeyEvent.VK_BACK_SPACE)
            }
            if (charTyped == '\u001b') {
                return HelixKeyStroke(keyChar = '\u001b', keyCode = KeyEvent.VK_ESCAPE)
            }

            val vk = if (charTyped.isLetterOrDigit()) {
                KeyEvent.getExtendedKeyCodeForChar(charTyped.code)
            } else {
                KeyEvent.VK_UNDEFINED
            }
            return HelixKeyStroke(keyChar = charTyped, keyCode = vk)
        }

        fun fromKeyEvent(e: KeyEvent): HelixKeyStroke {
            val isCtrl = e.isControlDown
            val isAlt = e.isAltDown
            val isShift = e.isShiftDown
            val isMeta = e.isMetaDown
            val keyCode = e.keyCode
            val rawChar = e.keyChar

            val keyChar = if (rawChar != KeyEvent.CHAR_UNDEFINED) {
                normalizeKeyChar(rawChar, isCtrl, keyCode)
            } else {
                null
            }

            return HelixKeyStroke(
                keyChar = keyChar,
                keyCode = keyCode,
                isCtrl = isCtrl,
                isAlt = isAlt,
                isShift = isShift,
                isMeta = isMeta,
            )
        }

        fun fromString(str: String): HelixKeyStroke {
            val trimmed = str.trim()
            if (trimmed.isEmpty()) {
                return HelixKeyStroke(keyChar = ' ', keyCode = KeyEvent.VK_SPACE)
            }

            var isCtrl = false
            var isAlt = false
            var isShift = false
            var isMeta = false

            var remainder = trimmed
            var matchedModifier = true
            while (matchedModifier && remainder.length > 1) {
                val lower = remainder.lowercase()
                when {
                    lower.startsWith("ctrl-") -> {
                        isCtrl = true
                        remainder = remainder.substring(5)
                    }

                    lower.startsWith("control-") -> {
                        isCtrl = true
                        remainder = remainder.substring(8)
                    }

                    lower.startsWith("alt-") -> {
                        isAlt = true
                        remainder = remainder.substring(4)
                    }

                    lower.startsWith("shift-") -> {
                        isShift = true
                        remainder = remainder.substring(6)
                    }

                    lower.startsWith("meta-") -> {
                        isMeta = true
                        remainder = remainder.substring(5)
                    }

                    remainder.length > 2 && remainder[1] == '-' -> {
                        when (remainder[0].uppercaseChar()) {
                            'C' -> isCtrl = true
                            'A' -> isAlt = true
                            'M' -> isMeta = true
                            'S' -> isShift = true
                            else -> matchedModifier = false
                        }
                        if (matchedModifier) {
                            remainder = remainder.substring(2)
                        }
                    }

                    else -> matchedModifier = false
                }
            }

            return parseKeyComponent(remainder, isCtrl, isAlt, isShift, isMeta)
        }

        private fun normalizeKeyChar(rawChar: Char, isCtrl: Boolean, keyCode: Int): Char? {
            val code = rawChar.code
            if (isCtrl && code in ASCII_CTRL_MIN..ASCII_CTRL_MAX) {
                return (code + ASCII_CTRL_OFFSET).toChar()
            }
            if (rawChar == KeyEvent.CHAR_UNDEFINED || (code < 32 && keyCode != KeyEvent.VK_UNDEFINED)) {
                return null
            }
            return rawChar
        }

        private fun parseKeyComponent(
            keyPart: String,
            isCtrl: Boolean,
            isAlt: Boolean,
            isShift: Boolean,
            isMeta: Boolean,
        ): HelixKeyStroke {
            val lower = keyPart.lowercase()
            return when (lower) {
                "space" -> HelixKeyStroke(' ', KeyEvent.VK_SPACE, isCtrl, isAlt, isShift, isMeta)

                "ret", "return", "enter" -> HelixKeyStroke('\n', KeyEvent.VK_ENTER, isCtrl, isAlt, isShift, isMeta)

                "tab" -> HelixKeyStroke('\t', KeyEvent.VK_TAB, isCtrl, isAlt, isShift, isMeta)

                "backspace" -> HelixKeyStroke('\b', KeyEvent.VK_BACK_SPACE, isCtrl, isAlt, isShift, isMeta)

                "esc", "escape" -> HelixKeyStroke('\u001b', KeyEvent.VK_ESCAPE, isCtrl, isAlt, isShift, isMeta)

                "del", "delete" -> HelixKeyStroke(null, KeyEvent.VK_DELETE, isCtrl, isAlt, isShift, isMeta)

                "ins", "insert" -> HelixKeyStroke(null, KeyEvent.VK_INSERT, isCtrl, isAlt, isShift, isMeta)

                "up" -> HelixKeyStroke(null, KeyEvent.VK_UP, isCtrl, isAlt, isShift, isMeta)

                "down" -> HelixKeyStroke(null, KeyEvent.VK_DOWN, isCtrl, isAlt, isShift, isMeta)

                "left" -> HelixKeyStroke(null, KeyEvent.VK_LEFT, isCtrl, isAlt, isShift, isMeta)

                "right" -> HelixKeyStroke(null, KeyEvent.VK_RIGHT, isCtrl, isAlt, isShift, isMeta)

                "pageup" -> HelixKeyStroke(null, KeyEvent.VK_PAGE_UP, isCtrl, isAlt, isShift, isMeta)

                "pagedown" -> HelixKeyStroke(null, KeyEvent.VK_PAGE_DOWN, isCtrl, isAlt, isShift, isMeta)

                "home" -> HelixKeyStroke(null, KeyEvent.VK_HOME, isCtrl, isAlt, isShift, isMeta)

                "end" -> HelixKeyStroke(null, KeyEvent.VK_END, isCtrl, isAlt, isShift, isMeta)

                else -> {
                    val ch = if (keyPart.length == 1) keyPart[0] else keyPart.firstOrNull() ?: ' '
                    val vk = KeyEvent.getExtendedKeyCodeForChar(ch.code)
                    HelixKeyStroke(ch, vk, isCtrl, isAlt, isShift, isMeta)
                }
            }
        }

        private fun specialKeyToString(keyCode: Int, keyChar: Char?): String = when (keyCode) {
            KeyEvent.VK_SPACE -> "space"

            KeyEvent.VK_ENTER -> "ret"

            KeyEvent.VK_TAB -> "tab"

            KeyEvent.VK_BACK_SPACE -> "backspace"

            KeyEvent.VK_ESCAPE -> "esc"

            KeyEvent.VK_DELETE -> "del"

            KeyEvent.VK_UP -> "up"

            KeyEvent.VK_DOWN -> "down"

            KeyEvent.VK_LEFT -> "left"

            KeyEvent.VK_RIGHT -> "right"

            KeyEvent.VK_PAGE_UP -> "pageup"

            KeyEvent.VK_PAGE_DOWN -> "pagedown"

            KeyEvent.VK_HOME -> "home"

            KeyEvent.VK_END -> "end"

            else -> {
                if (keyChar != null && keyChar != ' ') {
                    keyChar.toString()
                } else if (keyChar == ' ') {
                    "space"
                } else {
                    KeyEvent.getKeyText(keyCode).lowercase()
                }
            }
        }
    }
}
