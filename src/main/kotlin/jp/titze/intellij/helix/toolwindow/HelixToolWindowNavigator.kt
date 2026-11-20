// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree

/**
 * Handles key navigation commands inside eligible tool window components.
 */
object HelixToolWindowNavigator {

    fun dispatch(e: KeyEvent, eligibleTarget: Pair<JComponent, String>? = null): Boolean {
        if (HelixToolWindowState.isDispatchingSynthetic) return false

        val eligible = eligibleTarget ?: HelixToolWindowGuard.findEligibleTarget(e.component) ?: run {
            HelixToolWindowState.reset()
            return false
        }
        val (component, toolWindowId) = eligible

        val isCtrl = e.isControlDown && !e.isMetaDown && !e.isAltDown
        val isNoModifiers = !e.isControlDown && !e.isMetaDown && !e.isAltDown && !e.isShiftDown
        val isShiftOnly = e.isShiftDown && !e.isControlDown && !e.isMetaDown && !e.isAltDown

        // Escape: reset state and pass through to IntelliJ natively
        if (e.keyCode == KeyEvent.VK_ESCAPE && isNoModifiers) {
            HelixToolWindowState.reset()
            return false
        }

        // Enter: reset state and pass through to IntelliJ natively
        if (e.keyCode == KeyEvent.VK_ENTER && isNoModifiers) {
            HelixToolWindowState.reset()
            return false
        }

        val handled = when {
            isCtrl -> handleCtrlKey(e, component)
            isNoModifiers || isShiftOnly -> handleNormalKey(e, component, toolWindowId, isShiftOnly)
            else -> false
        }

        if (handled) {
            if (!isCtrl) {
                HelixToolWindowState.markInterceptedKey(e.keyCode, e.keyChar, isShiftOnly)
            }
            e.consume()
            return true
        }

        HelixToolWindowState.clearInterceptedKey()
        return false
    }

    private fun handleCtrlKey(e: KeyEvent, component: JComponent): Boolean = when (e.keyCode) {
        KeyEvent.VK_D -> {
            moveHalfPage(component, down = true)
            true
        }

        KeyEvent.VK_U -> {
            moveHalfPage(component, down = false)
            true
        }

        KeyEvent.VK_F -> {
            movePage(component, down = true)
            true
        }

        KeyEvent.VK_B -> {
            movePage(component, down = false)
            true
        }

        else -> false
    }

    private fun handleNormalKey(e: KeyEvent, component: JComponent, toolWindowId: String, isShift: Boolean): Boolean {
        val keyCode = e.keyCode
        val effectiveChar = if (e.keyChar != KeyEvent.CHAR_UNDEFINED) {
            e.keyChar
        } else {
            HelixToolWindowState.keyCharFromCode(keyCode, isShift) ?: KeyEvent.CHAR_UNDEFINED
        }
        val pendingSeq = HelixToolWindowState.getPendingSequence()

        // Handle 'g' chords: gg / ge
        if (pendingSeq == "g") {
            HelixToolWindowState.clearPendingSequence()
            return when {
                !isShift && (keyCode == KeyEvent.VK_G || effectiveChar == 'g') -> {
                    moveToHome(component)
                    true
                }

                !isShift && (keyCode == KeyEvent.VK_E || effectiveChar == 'e') -> {
                    moveToEnd(component)
                    true
                }

                else -> true // Swallow invalid g-chords
            }
        }

        // Count prefix accumulation: 1-9 starts a count; 0 appends if count > 0
        if (!isShift && effectiveChar in '0'..'9') {
            val digit = effectiveChar - '0'
            if (digit > 0 || HelixToolWindowState.getPendingCount() != null) {
                HelixToolWindowState.appendCountDigit(digit)
                return true
            }
        }

        val count = HelixToolWindowState.takeCount() ?: 1

        return when {
            // j: down
            !isShift && (keyCode == KeyEvent.VK_J || effectiveChar == 'j') -> {
                moveDown(component, count)
                true
            }

            // k: up
            !isShift && (keyCode == KeyEvent.VK_K || effectiveChar == 'k') -> {
                moveUp(component, count)
                true
            }

            // h: collapse / parent in tree; swallow in table/list
            !isShift && (keyCode == KeyEvent.VK_H || effectiveChar == 'h') -> {
                collapseOrSelectParent(component)
                true
            }

            // l: expand / child in tree; swallow in table/list
            !isShift && (keyCode == KeyEvent.VK_L || effectiveChar == 'l') -> {
                expandOrSelectChild(component)
                true
            }

            // 'o': default action / open
            !isShift && (keyCode == KeyEvent.VK_O || effectiveChar == 'o') -> {
                dispatchSyntheticKey(component, KeyEvent.VK_ENTER, '\n')
                true
            }

            // g: start chord
            !isShift && (keyCode == KeyEvent.VK_G || effectiveChar == 'g') -> {
                HelixToolWindowState.setPendingSequence("g")
                true
            }

            // /: open speed search
            !isShift && (keyCode == KeyEvent.VK_SLASH || effectiveChar == '/') -> {
                openSpeedSearch(component)
                true
            }

            // n: jump to next speed search match
            !isShift && (keyCode == KeyEvent.VK_N || effectiveChar == 'n') -> {
                jumpSpeedSearchMatch(component, toolWindowId)
                true
            }

            // N: jump to previous speed search match
            isShift && (keyCode == KeyEvent.VK_N || effectiveChar == 'N') -> {
                jumpSpeedSearchMatch(component, toolWindowId)
                true
            }

            // Swallow unmapped letters to prevent accidental speed search popup
            effectiveChar in 'a'..'z' || effectiveChar in 'A'..'Z' -> {
                true
            }

            else -> false
        }
    }

    private fun moveDown(component: JComponent, count: Int) {
        when (component) {
            is JTable -> ScrollingUtil.moveDown(component, count, false)

            is JList<*> -> repeat(count) { ScrollingUtil.moveDown(component, 0) }

            is JTree -> {
                val action = component.actionMap?.get("selectNext")
                if (action != null) {
                    val event = ActionEvent(component, ActionEvent.ACTION_PERFORMED, "selectNext")
                    repeat(count) { action.actionPerformed(event) }
                } else {
                    repeat(count) { dispatchSyntheticKey(component, KeyEvent.VK_DOWN) }
                }
            }
        }
    }

    private fun moveUp(component: JComponent, count: Int) {
        when (component) {
            is JTable -> ScrollingUtil.moveUp(component, count, false)

            is JList<*> -> repeat(count) { ScrollingUtil.moveUp(component, 0) }

            is JTree -> {
                val action = component.actionMap?.get("selectPrevious")
                if (action != null) {
                    val event = ActionEvent(component, ActionEvent.ACTION_PERFORMED, "selectPrevious")
                    repeat(count) { action.actionPerformed(event) }
                } else {
                    repeat(count) { dispatchSyntheticKey(component, KeyEvent.VK_UP) }
                }
            }
        }
    }

    private fun collapseOrSelectParent(component: JComponent) {
        if (component is JTree) {
            val action = component.actionMap?.get("selectParent")
            if (action != null) {
                action.actionPerformed(ActionEvent(component, ActionEvent.ACTION_PERFORMED, "selectParent"))
            } else {
                dispatchSyntheticKey(component, KeyEvent.VK_LEFT)
            }
        }
    }

    private fun expandOrSelectChild(component: JComponent) {
        if (component is JTree) {
            val action = component.actionMap?.get("selectChild")
            if (action != null) {
                action.actionPerformed(ActionEvent(component, ActionEvent.ACTION_PERFORMED, "selectChild"))
            } else {
                dispatchSyntheticKey(component, KeyEvent.VK_RIGHT)
            }
        }
    }

    private fun moveToHome(component: JComponent) {
        when (component) {
            is JTable -> ScrollingUtil.moveHome(component)

            is JList<*> -> ScrollingUtil.moveHome(component)

            is JTree -> {
                val action = component.actionMap?.get("selectFirst")
                if (action != null) {
                    action.actionPerformed(ActionEvent(component, ActionEvent.ACTION_PERFORMED, "selectFirst"))
                } else {
                    dispatchSyntheticKey(component, KeyEvent.VK_HOME)
                }
            }
        }
    }

    private fun moveToEnd(component: JComponent) {
        when (component) {
            is JTable -> ScrollingUtil.moveEnd(component)

            is JList<*> -> ScrollingUtil.moveEnd(component)

            is JTree -> {
                val action = component.actionMap?.get("selectLast")
                if (action != null) {
                    action.actionPerformed(ActionEvent(component, ActionEvent.ACTION_PERFORMED, "selectLast"))
                } else {
                    dispatchSyntheticKey(component, KeyEvent.VK_END)
                }
            }
        }
    }

    private fun moveHalfPage(component: JComponent, down: Boolean) {
        val visibleRowCount = getApproximateVisibleRowCount(component)
        val half = (visibleRowCount / 2).coerceAtLeast(1)
        if (down) {
            moveDown(component, half)
        } else {
            moveUp(component, half)
        }
    }

    private fun movePage(component: JComponent, down: Boolean) {
        when (component) {
            is JTable -> if (down) ScrollingUtil.movePageDown(component) else ScrollingUtil.movePageUp(component)

            is JList<*> -> if (down) ScrollingUtil.movePageDown(component) else ScrollingUtil.movePageUp(component)

            is JTree -> {
                val actionName = if (down) "scrollDownChangeSelection" else "scrollUpChangeSelection"
                val action = component.actionMap?.get(actionName)
                if (action != null) {
                    action.actionPerformed(ActionEvent(component, ActionEvent.ACTION_PERFORMED, actionName))
                } else {
                    val key = if (down) KeyEvent.VK_PAGE_DOWN else KeyEvent.VK_PAGE_UP
                    dispatchSyntheticKey(component, key)
                }
            }
        }
    }

    private fun getApproximateVisibleRowCount(component: JComponent): Int = when (component) {
        is JTree -> {
            val visible = component.visibleRowCount
            if (visible > 0) visible else 10
        }

        is JTable -> {
            val rowHeight = component.rowHeight
            if (rowHeight > 0 && component.visibleRect.height > 0) {
                component.visibleRect.height / rowHeight
            } else {
                10
            }
        }

        is JList<*> -> {
            val visible = component.visibleRowCount
            if (visible > 0) visible else 10
        }

        else -> 10
    }

    private fun openSpeedSearch(component: JComponent) {
        val supply = SpeedSearchSupply.getSupply(component, true)
        if (supply is SpeedSearchBase<*>) {
            supply.showPopup()
        }
    }

    private fun jumpSpeedSearchMatch(component: JComponent, toolWindowId: String) {
        val supply = SpeedSearchSupply.getSupply(component) ?: return
        val currentPrefix = supply.enteredPrefix
        if (!currentPrefix.isNullOrEmpty()) {
            HelixToolWindowState.setLastSearchQuery(toolWindowId, currentPrefix)
        }
        val query = HelixToolWindowState.getLastSearchQuery(toolWindowId) ?: return
        supply.findAndSelectElement(query)
    }

    fun dispatchSyntheticKey(component: JComponent, keyCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED) {
        if (HelixToolWindowState.isDispatchingSynthetic) return
        HelixToolWindowState.isDispatchingSynthetic = true
        try {
            val pressed = KeyEvent(
                component,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                keyCode,
                keyChar,
            )
            component.dispatchEvent(pressed)

            if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                val typed = KeyEvent(
                    component,
                    KeyEvent.KEY_TYPED,
                    System.currentTimeMillis(),
                    0,
                    KeyEvent.VK_UNDEFINED,
                    keyChar,
                )
                component.dispatchEvent(typed)
            }

            val released = KeyEvent(
                component,
                KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),
                0,
                keyCode,
                keyChar,
            )
            component.dispatchEvent(released)
        } finally {
            HelixToolWindowState.isDispatchingSynthetic = false
        }
    }
}
