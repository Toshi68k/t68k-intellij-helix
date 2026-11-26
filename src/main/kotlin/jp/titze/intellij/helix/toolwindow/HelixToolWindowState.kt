// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import java.awt.event.KeyEvent
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent

/**
 * Tracks transient state for modal tool window navigation.
 */
object HelixToolWindowState {

    private const val MAX_PENDING_TYPED_CHARS = 8

    data class BackwardSearchSession(
        val toolWindowId: String,
        val anchorRow: Int,
        var lastAdjustedPrefix: String? = null,
    )

    private var pendingCount: Int? = null
    private var pendingSequence: String = ""
    private val pendingInterceptedChars = ArrayDeque<Char>()

    @Volatile
    var isDispatchingSynthetic: Boolean = false

    // Remembers the last search pattern and direction per tool window id
    private val lastSearchQueries = ConcurrentHashMap<String, String>()
    private val lastSearchDirections = ConcurrentHashMap<String, Boolean>()
    private val activeBackwardSessions: MutableMap<JComponent, BackwardSearchSession> =
        Collections.synchronizedMap(WeakHashMap())

    fun getPendingCount(): Int? = pendingCount

    fun appendCountDigit(digit: Int) {
        val current = pendingCount ?: 0
        pendingCount = (current * 10) + digit
    }

    fun takeCount(): Int? {
        val count = pendingCount
        pendingCount = null
        return count
    }

    fun getPendingSequence(): String = pendingSequence

    fun setPendingSequence(sequence: String) {
        pendingSequence = sequence
    }

    fun clearPendingSequence() {
        pendingSequence = ""
    }

    fun reset() {
        pendingCount = null
        pendingSequence = ""
        pendingInterceptedChars.clear()
        isDispatchingSynthetic = false
    }

    fun clearSearchState() {
        lastSearchQueries.clear()
        lastSearchDirections.clear()
        activeBackwardSessions.clear()
    }

    fun markInterceptedKey(keyCode: Int, keyChar: Char, isShift: Boolean = false) {
        val resolvedChar = if (keyChar != KeyEvent.CHAR_UNDEFINED) {
            keyChar
        } else {
            keyCharFromCode(keyCode, isShift)
        }
        if (resolvedChar != null) {
            markInterceptedKey(resolvedChar)
        }
    }

    fun markInterceptedKey(char: Char) {
        if (pendingInterceptedChars.size >= MAX_PENDING_TYPED_CHARS) {
            pendingInterceptedChars.removeFirst()
        }
        pendingInterceptedChars.addLast(char)
    }

    fun consumeInterceptedKey(char: Char): Boolean {
        if (pendingInterceptedChars.isEmpty()) return false
        val exactIdx = pendingInterceptedChars.indexOf(char)
        if (exactIdx >= 0) {
            pendingInterceptedChars.removeAt(exactIdx)
            return true
        }
        val ciIdx = pendingInterceptedChars.indexOfFirst { it.equals(char, ignoreCase = true) }
        if (ciIdx >= 0) {
            pendingInterceptedChars.removeAt(ciIdx)
            return true
        }
        return false
    }

    fun clearInterceptedKey() {
        pendingInterceptedChars.clear()
    }

    fun keyCharFromCode(keyCode: Int, isShift: Boolean = false): Char? = when (keyCode) {
        in KeyEvent.VK_A..KeyEvent.VK_Z -> {
            val base = if (isShift) 'A' else 'a'
            base + (keyCode - KeyEvent.VK_A)
        }

        in KeyEvent.VK_0..KeyEvent.VK_9 -> if (!isShift) ('0' + (keyCode - KeyEvent.VK_0)) else null

        KeyEvent.VK_SLASH -> if (isShift) '?' else '/'

        KeyEvent.VK_SPACE -> if (!isShift) ' ' else null

        else -> null
    }

    fun getLastSearchQuery(toolWindowId: String): String? = lastSearchQueries[toolWindowId]

    fun setLastSearchQuery(toolWindowId: String, query: String) {
        if (query.isNotEmpty()) {
            lastSearchQueries[toolWindowId] = query
        }
    }

    fun isLastSearchBackward(toolWindowId: String): Boolean = lastSearchDirections[toolWindowId] == true

    fun setLastSearchDirection(toolWindowId: String, backward: Boolean) {
        lastSearchDirections[toolWindowId] = backward
    }

    fun startBackwardSearch(component: JComponent, toolWindowId: String, anchorRow: Int) {
        activeBackwardSessions[component] = BackwardSearchSession(toolWindowId, anchorRow)
    }

    fun getBackwardSearch(component: JComponent): BackwardSearchSession? = activeBackwardSessions[component]

    fun clearBackwardSearch(component: JComponent) {
        activeBackwardSessions.remove(component)
    }
}
