// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.config

import com.intellij.openapi.vfs.LocalFileSystem
import jp.titze.intellij.helix.state.HelixMode
import java.io.File
import java.io.IOException

/**
 * Safely adds, updates, or removes keybinding assignments in Helix TOML configuration files.
 */
object HelixConfigModifier {

    fun setBinding(file: File, mode: HelixMode, sequence: String, action: Any): Boolean = try {
        ensureConfigFileExists(file)
        val content = file.readText()
        val newContent = updateBindingInContent(content, mode, sequence.trim(), action)
        file.writeText(newContent)
        refreshAndReload(file)
        true
    } catch (_: IOException) {
        false
    }

    fun removeBinding(file: File, mode: HelixMode, sequence: String): Boolean {
        if (!file.exists()) return false
        return try {
            val content = file.readText()
            val newContent = removeBindingFromContent(content, mode, sequence.trim())
            file.writeText(newContent)
            refreshAndReload(file)
            true
        } catch (_: IOException) {
            false
        }
    }

    fun setNoOp(file: File, mode: HelixMode, sequence: String): Boolean = setBinding(file, mode, sequence, "no_op")

    private fun ensureConfigFileExists(file: File) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(HelixConfigLoader.getStarterConfigTemplate())
        }
    }

    private fun refreshAndReload(file: File) {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        HelixConfigLoader.loadConfig(file)
    }

    fun updateBindingInContent(content: String, mode: HelixMode, sequence: String, action: Any): String {
        val lines = content.lines().toMutableList()
        val lineInfos = parseLineInfos(lines)
        val formattedVal = formatActionValue(action)

        val existingIdx = lineInfos.indexOfFirst { it.mode == mode && it.sequence == sequence }
        if (existingIdx >= 0) {
            val info = lineInfos[existingIdx]
            val keyPart = if (info.sectionPrefix.isNotEmpty()) {
                sequence.removePrefix(info.sectionPrefix).trim()
            } else {
                sequence
            }
            lines[existingIdx] = "${formatKeyTokens(keyPart)} = $formattedVal"
            return lines.joinToString("\n")
        }

        val targetHeader = "keys.${mode.name.lowercase()}"
        val sectionStart = lineInfos.indexOfFirst { it.isSectionHeader && it.rawHeader == targetHeader }

        if (sectionStart >= 0) {
            val insertIdx = findSectionInsertionPoint(lines, lineInfos, sectionStart)
            val keyStr = formatKeyTokens(sequence)
            lines.add(insertIdx, "$keyStr = $formattedVal")
        } else {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines.add("")
            }
            lines.add("[$targetHeader]")
            lines.add("${formatKeyTokens(sequence)} = $formattedVal")
        }

        return lines.joinToString("\n")
    }

    fun removeBindingFromContent(content: String, mode: HelixMode, sequence: String): String {
        val lines = content.lines().toMutableList()
        val lineInfos = parseLineInfos(lines)

        val indicesToRemove = lineInfos
            .filter { it.mode == mode && it.sequence == sequence }
            .map { it.lineIndex }
            .sortedDescending()

        if (indicesToRemove.isEmpty()) return content

        for (idx in indicesToRemove) {
            lines.removeAt(idx)
        }

        return lines.joinToString("\n")
    }

    private fun findSectionInsertionPoint(lines: List<String>, lineInfos: List<LineInfo>, sectionStart: Int): Int {
        var nextSectionIdx = lineInfos.size
        for (i in (sectionStart + 1) until lineInfos.size) {
            if (lineInfos[i].isSectionHeader) {
                nextSectionIdx = i
                break
            }
        }
        var insertIdx = nextSectionIdx
        while (insertIdx > sectionStart + 1 && lines[insertIdx - 1].isBlank()) {
            insertIdx--
        }
        return insertIdx
    }

    private fun formatKeyTokens(sequence: String): String {
        val tokens = sequence.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return tokens.joinToString(".") { "\"${it.removeSurrounding("\"").removeSurrounding("'")}\"" }
    }

    private fun formatActionValue(action: Any): String = when (action) {
        is String -> {
            val trimmed = action.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed
            } else {
                "\"${escapeToml(trimmed)}\""
            }
        }

        is List<*> -> {
            val escapedItems = action.map { "\"${escapeToml(it.toString())}\"" }
            "[${escapedItems.joinToString(", ")}]"
        }

        else -> "\"${escapeToml(action.toString())}\""
    }

    private fun escapeToml(str: String): String = str
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t")

    private fun parseLineInfos(lines: List<String>): List<LineInfo> {
        val infos = mutableListOf<LineInfo>()
        var currentMode: HelixMode? = null
        var currentPrefix = ""
        var rawHeader = ""

        for ((idx, rawLine) in lines.withIndex()) {
            val stripped = HelixTomlParser.stripComment(rawLine).trim()
            if (isSectionHeader(stripped)) {
                rawHeader = stripped.substring(1, stripped.length - 1).trim()
                val (parsedMode, parsedPrefix) = HelixTomlParser.parseSectionHeader(rawHeader)
                currentMode = parsedMode
                currentPrefix = parsedPrefix
                infos.add(LineInfo(idx, currentMode, currentPrefix, null, true, rawHeader))
            } else if (currentMode != null && hasAssignment(stripped)) {
                val seq = extractSequence(stripped, currentPrefix)
                infos.add(LineInfo(idx, currentMode, currentPrefix, seq, false, rawHeader))
            } else {
                infos.add(LineInfo(idx, currentMode, currentPrefix, null, false, rawHeader))
            }
        }
        return infos
    }

    private fun isSectionHeader(line: String): Boolean = line.startsWith("[") && line.endsWith("]")

    private fun hasAssignment(line: String): Boolean = line.contains("=")

    private fun extractSequence(line: String, sectionPrefix: String): String? {
        val eqIdx = line.indexOf('=')
        if (eqIdx == -1) return null
        val rawKey = line.substring(0, eqIdx).trim()
        val tokens = HelixTomlParser.splitDottedKeys(rawKey)
        if (tokens.isEmpty()) return null

        val fullParts = if (sectionPrefix.isNotEmpty()) {
            sectionPrefix.split(" ") + tokens
        } else {
            tokens
        }
        return fullParts.joinToString(" ")
    }

    private data class LineInfo(
        val lineIndex: Int,
        val mode: HelixMode?,
        val sectionPrefix: String,
        val sequence: String?,
        val isSectionHeader: Boolean,
        val rawHeader: String,
    )
}
