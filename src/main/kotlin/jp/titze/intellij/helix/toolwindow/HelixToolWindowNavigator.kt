// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.ui.ScrollingUtil
import jp.titze.intellij.helix.keymap.HelixKeyContext
import jp.titze.intellij.helix.keymap.HelixShortcutPolicy
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup
import java.awt.KeyboardFocusManager
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

    fun handleActiveSpeedSearchEnter(e: KeyEvent, settings: HelixSettings = HelixSettings.instance): Boolean =
        HelixToolWindowSpeedSearch.handleActiveEnter(e, settings)

    private fun isPlainKey(e: KeyEvent): Boolean {
        if (e.isControlDown || e.isMetaDown) return false
        return !e.isAltDown && !e.isShiftDown
    }

    fun dispatch(e: KeyEvent, eligibleTarget: Pair<JComponent, String>? = null): Boolean {
        if (HelixToolWindowState.isDispatchingSynthetic) return false

        val eligible = eligibleTarget ?: HelixToolWindowGuard.findEligibleTarget(e.component) ?: run {
            HelixToolWindowState.reset()
            return false
        }
        val (component, toolWindowId) = eligible

        val isCtrl = e.isControlDown && !e.isMetaDown && !e.isAltDown
        val isNoModifiers = isPlainKey(e)
        val isShiftOnly = e.isShiftDown && !e.isControlDown && !e.isMetaDown && !e.isAltDown

        // Escape: reset state and pass through to IntelliJ natively
        if (e.keyCode == KeyEvent.VK_ESCAPE && isNoModifiers) {
            HelixToolWindowState.reset()
            if (isToolWindowWhichKeyShowing()) {
                HelixWhichKeyPopup.hide()
            }
            return false
        }

        // Enter: reset state and pass through to IntelliJ natively
        if (e.keyCode == KeyEvent.VK_ENTER && isNoModifiers) {
            HelixToolWindowState.reset()
            return false
        }

        val handled = when {
            isCtrl -> handleCtrlKey(e, component, toolWindowId)
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

    private fun isToolWindowWhichKeyShowing(): Boolean = HelixWhichKeyPopup.isShowing(HelixToolWindowSwitcher.PREFIX) ||
        HelixWhichKeyPopup.isShowing(HelixToolWindowSwitcher.WINDOW_PREFIX)

    private fun handleCtrlKey(e: KeyEvent, component: JComponent, toolWindowId: String): Boolean {
        if (HelixToolWindowState.getPendingSequence() == "C-w") {
            HelixToolWindowState.clearPendingSequence()
            return handleWindowChordKey(e.keyCode, e.isShiftDown, component, toolWindowId)
        }

        if (e.keyCode == KeyEvent.VK_W && canStartToolWindowChord()) {
            HelixToolWindowState.takeCount()
            HelixToolWindowState.setPendingSequence("C-w")
            HelixToolWindowSwitcher.showWindowMenu(component, toolWindowId)
            return true
        }

        return when (e.keyCode) {
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
    }

    private fun canStartToolWindowChord(): Boolean = HelixSettings.instance.toolWindowSeamlessFocusJump &&
        HelixShortcutPolicy.isHelixOwned(KeyEvent.VK_W, HelixKeyContext.NORMAL)

    private fun handleWindowChordKey(
        keyCode: Int,
        isShift: Boolean,
        component: JComponent,
        toolWindowId: String,
    ): Boolean {
        if (HelixWhichKeyPopup.isShowing(HelixToolWindowSwitcher.WINDOW_PREFIX)) {
            HelixWhichKeyPopup.hide()
        }
        val direction = when {
            !isShift && keyCode == KeyEvent.VK_H -> FocusDirection.LEFT
            !isShift && keyCode == KeyEvent.VK_J -> FocusDirection.DOWN
            !isShift && keyCode == KeyEvent.VK_K -> FocusDirection.UP
            !isShift && keyCode == KeyEvent.VK_L -> FocusDirection.RIGHT
            else -> null
        }
        if (direction != null) {
            return HelixWindowFocusNavigator.jumpFromToolWindow(direction, component, toolWindowId)
        }
        if (keyCode == KeyEvent.VK_W) {
            return HelixWindowFocusNavigator.returnToEditorFromToolWindow(component)
        }
        return true // Swallow invalid C-w chord inside tool window
    }

    private fun handleNormalKey(e: KeyEvent, component: JComponent, toolWindowId: String, isShift: Boolean): Boolean {
        val keyCode = e.keyCode
        val effectiveChar = resolveEffectiveChar(keyCode, e.keyChar, isShift)
        val pendingSeq = HelixToolWindowState.getPendingSequence()

        if (pendingSeq == "C-w") {
            HelixToolWindowState.clearPendingSequence()
            return handleWindowChordKey(keyCode, isShift, component, toolWindowId)
        }
        if (pendingSeq == HelixToolWindowSwitcher.PREFIX) {
            return HelixToolWindowSwitcher.handlePendingKey(keyCode, effectiveChar, isShift, component, toolWindowId)
        }
        if (pendingSeq == "g") {
            HelixToolWindowState.clearPendingSequence()
            return handleGChord(keyCode, effectiveChar, isShift, component)
        }
        if (!isShift && effectiveChar in '0'..'9' && appendCountDigitIfValid(effectiveChar)) {
            return true
        }

        val count = HelixToolWindowState.takeCount() ?: 1
        val dispatched = handleToolWindowActionKey(keyCode, effectiveChar, isShift, component, toolWindowId) ||
            handleNavigationKey(keyCode, effectiveChar, isShift, component, count) ||
            handleSearchKey(keyCode, effectiveChar, isShift, component, toolWindowId)
        if (dispatched) return true

        // Swallow unmapped letters to prevent accidental speed search popup
        return effectiveChar in 'a'..'z' || effectiveChar in 'A'..'Z'
    }

    private fun resolveEffectiveChar(keyCode: Int, keyChar: Char, isShift: Boolean): Char =
        if (keyChar != KeyEvent.CHAR_UNDEFINED) {
            keyChar
        } else {
            HelixToolWindowState.keyCharFromCode(keyCode, isShift) ?: KeyEvent.CHAR_UNDEFINED
        }

    private fun appendCountDigitIfValid(effectiveChar: Char): Boolean {
        val digit = effectiveChar - '0'
        if (digit == 0 && HelixToolWindowState.getPendingCount() == null) return false
        HelixToolWindowState.appendCountDigit(digit)
        return true
    }

    private fun handleGChord(keyCode: Int, effectiveChar: Char, isShift: Boolean, component: JComponent): Boolean =
        when {
            !isShift && (keyCode == KeyEvent.VK_G || effectiveChar == 'g') -> {
                moveToHome(component)
                true
            }

            !isShift && (keyCode == KeyEvent.VK_E || effectiveChar == 'e') -> {
                moveToEnd(component)
                true
            }

            else -> true
        }

    private fun handleToolWindowActionKey(
        keyCode: Int,
        effectiveChar: Char,
        isShift: Boolean,
        component: JComponent,
        toolWindowId: String,
    ): Boolean = when {
        !isShift && (keyCode == KeyEvent.VK_SPACE || effectiveChar == ' ') -> {
            handleSpaceKey(component, toolWindowId)
        }

        !isShift && (keyCode == KeyEvent.VK_S || effectiveChar == 's') &&
            HelixCommitToolWindowActions.isCommitToolWindow(toolWindowId) -> {
            HelixCommitToolWindowActions.toggleStageOrCheckbox(component, toolWindowId, allowNativePassthrough = false)
        }

        !isShift && (keyCode == KeyEvent.VK_D || effectiveChar == 'd') -> {
            HelixCommitToolWindowActions.openDiffPreview(component, toolWindowId) || true
        }

        !isShift && (keyCode == KeyEvent.VK_C || effectiveChar == 'c') -> {
            HelixCommitToolWindowActions.focusCommitMessage(component, toolWindowId) || true
        }

        !isShift && (keyCode == KeyEvent.VK_O || effectiveChar == 'o') -> {
            dispatchSyntheticKey(component, KeyEvent.VK_ENTER, '\n')
            true
        }

        !isShift && (keyCode == KeyEvent.VK_G || effectiveChar == 'g') -> {
            HelixToolWindowState.setPendingSequence("g")
            true
        }

        else -> false
    }

    private fun handleSpaceKey(component: JComponent, toolWindowId: String): Boolean = when {
        HelixToolWindowSwitcher.isEligibleForSpaceSwitcher(component, toolWindowId) -> {
            HelixToolWindowSwitcher.startSpaceMenu(component, toolWindowId)
        }

        HelixCommitToolWindowActions.isCommitToolWindow(toolWindowId) ||
            HelixCommitToolWindowActions.isChangesTreeWithCheckboxes(component) -> {
            HelixCommitToolWindowActions.toggleStageOrCheckbox(component, toolWindowId)
        }

        else -> false
    }

    private fun handleNavigationKey(
        keyCode: Int,
        effectiveChar: Char,
        isShift: Boolean,
        component: JComponent,
        count: Int,
    ): Boolean = when {
        !isShift && (keyCode == KeyEvent.VK_J || effectiveChar == 'j') -> {
            moveDown(component, count)
            true
        }

        !isShift && (keyCode == KeyEvent.VK_K || effectiveChar == 'k') -> {
            moveUp(component, count)
            true
        }

        !isShift && (keyCode == KeyEvent.VK_H || effectiveChar == 'h') -> {
            collapseOrSelectParent(component)
            true
        }

        !isShift && (keyCode == KeyEvent.VK_L || effectiveChar == 'l') -> {
            expandOrSelectChild(component)
            true
        }

        else -> false
    }

    private fun handleSearchKey(
        keyCode: Int,
        effectiveChar: Char,
        isShift: Boolean,
        component: JComponent,
        toolWindowId: String,
    ): Boolean = when {
        !isShift && (keyCode == KeyEvent.VK_SLASH || effectiveChar == '/') -> {
            HelixToolWindowSpeedSearch.open(component, toolWindowId, backward = false)
            true
        }

        effectiveChar == '?' || (isShift && keyCode == KeyEvent.VK_SLASH) -> {
            HelixToolWindowSpeedSearch.open(component, toolWindowId, backward = true)
            true
        }

        !isShift && (keyCode == KeyEvent.VK_N || effectiveChar == 'n') -> {
            HelixToolWindowSpeedSearch.jumpMatch(component, toolWindowId, opposite = false)
            true
        }

        isShift && (keyCode == KeyEvent.VK_N || effectiveChar == 'N') -> {
            HelixToolWindowSpeedSearch.jumpMatch(component, toolWindowId, opposite = true)
            true
        }

        else -> false
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

    fun dispatchSyntheticKey(component: JComponent, keyCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED) {
        if (HelixToolWindowState.isDispatchingSynthetic) return
        HelixToolWindowState.isDispatchingSynthetic = true
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        try {
            val pressed = KeyEvent(
                component,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                keyCode,
                keyChar,
            )
            focusManager.redispatchEvent(component, pressed)

            if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                val typed = KeyEvent(
                    component,
                    KeyEvent.KEY_TYPED,
                    System.currentTimeMillis(),
                    0,
                    KeyEvent.VK_UNDEFINED,
                    keyChar,
                )
                focusManager.redispatchEvent(component, typed)
            }

            val released = KeyEvent(
                component,
                KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),
                0,
                keyCode,
                keyChar,
            )
            focusManager.redispatchEvent(component, released)
        } finally {
            HelixToolWindowState.isDispatchingSynthetic = false
        }
    }
}
