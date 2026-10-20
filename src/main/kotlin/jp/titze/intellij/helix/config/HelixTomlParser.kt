// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.config

import jp.titze.intellij.helix.state.HelixMode

data class HelixParsedBinding(
    val mode: HelixMode,
    val sequence: String,
    val action: Any, // String or List<String>
)

data class HelixTomlParseResult(val bindings: List<HelixParsedBinding>, val errors: List<String>)

/**
 * Lightweight, zero-dependency TOML parser specifically designed for Helix configuration files.
 * Extracts [keys.normal], [keys.select], and [keys.insert] tables and chords.
 */
object HelixTomlParser {

    fun parse(content: String): HelixTomlParseResult {
        val bindings = mutableListOf<HelixParsedBinding>()
        val errors = mutableListOf<String>()

        var currentMode: HelixMode? = null
        var currentPrefix = ""

        val lines = content.lines()
        for ((lineIdx, rawLine) in lines.withIndex()) {
            val lineNum = lineIdx + 1
            val line = stripComment(rawLine).trim()
            if (line.isNotEmpty()) {
                if (line.startsWith("[") && line.endsWith("]")) {
                    val header = line.substring(1, line.length - 1).trim()
                    val (mode, prefix) = parseSectionHeader(header)
                    currentMode = mode
                    currentPrefix = prefix
                } else if (currentMode != null) {
                    try {
                        parseKeyValue(line, currentMode, currentPrefix, bindings)
                    } catch (ex: IllegalArgumentException) {
                        errors.add("Line $lineNum: ${ex.message}")
                    }
                }
            }
        }

        return HelixTomlParseResult(bindings, errors)
    }

    private fun isUnescapedQuote(c: Char, prevChar: Char?): Boolean {
        val isQuote = c == '"' || c == '\''
        val notEscaped = prevChar != '\\'
        return isQuote && notEscaped
    }

    private fun stripComment(line: String): String {
        var inQuote = false
        var quoteChar = ' '
        for (i in line.indices) {
            val c = line[i]
            val prev = if (i > 0) line[i - 1] else null
            if (isUnescapedQuote(c, prev)) {
                if (!inQuote) {
                    inQuote = true
                    quoteChar = c
                } else if (c == quoteChar) {
                    inQuote = false
                }
            } else if (c == '#' && !inQuote) {
                return line.substring(0, i)
            }
        }
        return line
    }

    private fun normalizePrefixPart(part: String): String = when (part.lowercase()) {
        "goto" -> "g"
        "view" -> "z"
        "match" -> "m"
        "window" -> "C-w"
        else -> part
    }

    private fun parseSectionHeader(header: String): Pair<HelixMode?, String> {
        val parts = splitDottedKeys(header)
        if (parts.isEmpty() || parts[0].lowercase() != "keys" || parts.size < 2) {
            return null to ""
        }

        val firstSub = parts[1].lowercase()
        val rest = parts.drop(2).map { normalizePrefixPart(it) }

        return when (firstSub) {
            "normal" -> HelixMode.NORMAL to rest.joinToString(" ")
            "select" -> HelixMode.SELECT to rest.joinToString(" ")
            "insert" -> HelixMode.INSERT to rest.joinToString(" ")
            "space" -> HelixMode.NORMAL to (listOf("space") + rest).joinToString(" ")
            "goto", "g" -> HelixMode.NORMAL to (listOf("g") + rest).joinToString(" ")
            "view", "z" -> HelixMode.NORMAL to (listOf("z") + rest).joinToString(" ")
            "match", "m" -> HelixMode.NORMAL to (listOf("m") + rest).joinToString(" ")
            "window" -> HelixMode.NORMAL to (listOf("C-w") + rest).joinToString(" ")
            else -> null to ""
        }
    }

    private fun parseKeyValue(
        line: String,
        mode: HelixMode,
        sectionPrefix: String,
        bindings: MutableList<HelixParsedBinding>,
    ) {
        val eqIdx = findTopLevelEquals(line)
        require(eqIdx != -1) { "Missing '=' assignment operator in line: '$line'" }

        val rawKey = line.substring(0, eqIdx).trim()
        val rawValue = line.substring(eqIdx + 1).trim()

        val keyTokens = splitDottedKeys(rawKey)
        require(keyTokens.isNotEmpty()) { "Empty key expression in line: '$line'" }

        val fullKeyParts = if (sectionPrefix.isNotEmpty()) {
            sectionPrefix.split(" ") + keyTokens
        } else {
            keyTokens
        }

        if (rawValue.startsWith("{") && rawValue.endsWith("}")) {
            parseInlineTable(rawValue, mode, fullKeyParts.joinToString(" "), bindings)
        } else if (rawValue.startsWith("[") && rawValue.endsWith("]")) {
            val list = parseStringArray(rawValue)
            bindings.add(HelixParsedBinding(mode, fullKeyParts.joinToString(" "), list))
        } else {
            val actionStr = parseStringLiteral(rawValue)
            bindings.add(HelixParsedBinding(mode, fullKeyParts.joinToString(" "), actionStr))
        }
    }

    private fun parseInlineTable(
        inlineTable: String,
        mode: HelixMode,
        prefix: String,
        bindings: MutableList<HelixParsedBinding>,
    ) {
        val inner = inlineTable.substring(1, inlineTable.length - 1).trim()
        val pairs = splitTopLevelCommas(inner)
        for (pair in pairs) {
            if (pair.isBlank()) continue
            val eqIdx = findTopLevelEquals(pair)
            require(eqIdx != -1) { "Invalid key-value in inline table: '$pair'" }
            val k = splitDottedKeys(pair.substring(0, eqIdx).trim()).joinToString(" ")
            val vStr = pair.substring(eqIdx + 1).trim()
            val fullKey = if (prefix.isNotEmpty()) "$prefix $k" else k
            if (vStr.startsWith("[") && vStr.endsWith("]")) {
                bindings.add(HelixParsedBinding(mode, fullKey, parseStringArray(vStr)))
            } else {
                bindings.add(HelixParsedBinding(mode, fullKey, parseStringLiteral(vStr)))
            }
        }
    }

    private fun parseStringArray(raw: String): List<String> {
        val inner = raw.substring(1, raw.length - 1).trim()
        val items = splitTopLevelCommas(inner)
        return items.filter { it.isNotBlank() }.map { parseStringLiteral(it.trim()) }
    }

    private fun parseStringLiteral(raw: String): String {
        val trimmed = raw.trim()
        val isDoubleQuoted = trimmed.startsWith("\"") && trimmed.endsWith("\"")
        val isSingleQuoted = trimmed.startsWith("'") && trimmed.endsWith("'")
        if (isDoubleQuoted || isSingleQuoted) {
            val content = trimmed.substring(1, trimmed.length - 1)
            return unescapeString(content)
        }
        return trimmed
    }

    private fun unescapeString(str: String): String = str.replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\t", "\t")

    private fun findTopLevelEquals(line: String): Int {
        var inQuote = false
        var quoteChar = ' '
        var depth = 0
        for (i in line.indices) {
            val c = line[i]
            val prev = if (i > 0) line[i - 1] else null
            if (isUnescapedQuote(c, prev)) {
                if (!inQuote) {
                    inQuote = true
                    quoteChar = c
                } else if (c == quoteChar) {
                    inQuote = false
                }
            } else if (!inQuote) {
                if (c == '{' || c == '[') {
                    depth++
                } else if (c == '}' || c == ']') {
                    depth--
                } else if (c == '=' && depth == 0) {
                    return i
                }
            }
        }
        return -1
    }

    private fun splitTopLevelCommas(str: String): List<String> {
        val result = mutableListOf<String>()
        var inQuote = false
        var quoteChar = ' '
        var depth = 0
        var start = 0
        for (i in str.indices) {
            val c = str[i]
            val prev = if (i > 0) str[i - 1] else null
            if (isUnescapedQuote(c, prev)) {
                if (!inQuote) {
                    inQuote = true
                    quoteChar = c
                } else if (c == quoteChar) {
                    inQuote = false
                }
            } else if (!inQuote) {
                if (c == '{' || c == '[') {
                    depth++
                } else if (c == '}' || c == ']') {
                    depth--
                } else if (c == ',' && depth == 0) {
                    result.add(str.substring(start, i))
                    start = i + 1
                }
            }
        }
        if (start < str.length) {
            result.add(str.substring(start))
        }
        return result
    }

    private fun splitDottedKeys(raw: String): List<String> {
        val result = mutableListOf<String>()
        var inQuote = false
        var quoteChar = ' '
        var start = 0
        for (i in raw.indices) {
            val c = raw[i]
            val prev = if (i > 0) raw[i - 1] else null
            if (isUnescapedQuote(c, prev)) {
                if (!inQuote) {
                    inQuote = true
                    quoteChar = c
                } else if (c == quoteChar) {
                    inQuote = false
                }
            } else if (c == '.' && !inQuote) {
                val token = parseStringLiteral(raw.substring(start, i).trim())
                if (token.isNotEmpty()) result.add(token)
                start = i + 1
            }
        }
        val lastToken = parseStringLiteral(raw.substring(start).trim())
        if (lastToken.isNotEmpty()) result.add(lastToken)
        return result
    }
}
