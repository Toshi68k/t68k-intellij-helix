// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import jp.titze.intellij.helix.settings.HelixSettings
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree

/**
 * Coordinates SpeedSearch activation (`/` and `?`), two-step Enter confirmation,
 * and directional match jumping (`n` / `N`) inside tool window components.
 */
object HelixToolWindowSpeedSearch {

    private const val BACKWARD_LISTENER_KEY = "helix.speedSearch.backwardListener"

    fun handleActiveEnter(e: KeyEvent, settings: HelixSettings = HelixSettings.instance): Boolean {
        if (!settings.toolWindowSearchConfirmOnEnter || HelixToolWindowState.isDispatchingSynthetic) return false
        if (e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_ENTER || !isPlainKey(e)) return false

        val (navComponent, toolWindowId, speedSearch) =
            HelixToolWindowGuard.findActiveSpeedSearchTarget(e.component, settings) ?: return false

        val prefix = speedSearch.enteredPrefix
        if (!prefix.isNullOrEmpty()) {
            HelixToolWindowState.setLastSearchQuery(toolWindowId, prefix)
        }
        HelixToolWindowState.clearBackwardSearch(navComponent)
        (speedSearch as? SpeedSearchBase<*>)?.hidePopup()
        HelixToolWindowState.reset()
        HelixToolWindowState.markInterceptedKey('\n')
        e.consume()
        return true
    }

    fun open(component: JComponent, toolWindowId: String, backward: Boolean) {
        val supply = SpeedSearchSupply.getSupply(component, true)
        HelixToolWindowState.setLastSearchDirection(toolWindowId, backward)
        if (backward) {
            HelixToolWindowState.startBackwardSearch(component, toolWindowId, getSelectedRowIndex(component))
            if (supply is SpeedSearchBase<*>) {
                ensureBackwardListener(component, supply)
            }
        } else {
            HelixToolWindowState.clearBackwardSearch(component)
        }
        if (supply is SpeedSearchBase<*>) {
            supply.showPopup()
        }
    }

    fun jumpMatch(component: JComponent, toolWindowId: String, opposite: Boolean) {
        val supply = SpeedSearchSupply.getSupply(component, true) ?: return
        val currentPrefix = supply.enteredPrefix
        if (!currentPrefix.isNullOrEmpty()) {
            HelixToolWindowState.setLastSearchQuery(toolWindowId, currentPrefix)
        }
        val query = HelixToolWindowState.getLastSearchQuery(toolWindowId) ?: return
        val forward = HelixToolWindowState.isLastSearchBackward(toolWindowId) == opposite
        if (supply is SpeedSearchBase<*>) {
            val primaryKey = if (forward) KeyEvent.VK_DOWN else KeyEvent.VK_UP
            val wrapKey = if (forward) KeyEvent.VK_HOME else KeyEvent.VK_END
            if (!supply.adjustSelection(primaryKey, query) && !supply.adjustSelection(wrapKey, query)) {
                supply.findAndSelectElement(query)
            }
        } else {
            supply.findAndSelectElement(query)
        }
    }

    private fun ensureBackwardListener(component: JComponent, supply: SpeedSearchBase<*>) {
        if (component.getClientProperty(BACKWARD_LISTENER_KEY) != null) return
        val listener = PropertyChangeListener {
            syncBackwardPopupMatch(component, supply)
        }
        component.putClientProperty(BACKWARD_LISTENER_KEY, listener)
        supply.addChangeListener(listener)
    }

    private fun syncBackwardPopupMatch(component: JComponent, supply: SpeedSearchBase<*>) {
        val session = HelixToolWindowState.getBackwardSearch(component) ?: return
        if (!supply.isPopupActive) {
            HelixToolWindowState.clearBackwardSearch(component)
            return
        }
        val prefix = supply.enteredPrefix
        if (prefix.isNullOrEmpty()) {
            session.lastAdjustedPrefix = null
            return
        }
        if (prefix == session.lastAdjustedPrefix) return
        session.lastAdjustedPrefix = prefix

        val currentRow = getSelectedRowIndex(component)
        setSelectedRowIndex(component, session.anchorRow)
        val matched = supply.adjustSelection(KeyEvent.VK_UP, prefix) ||
            supply.adjustSelection(KeyEvent.VK_END, prefix)
        if (!matched && currentRow >= 0) {
            setSelectedRowIndex(component, currentRow)
        }
    }

    private fun getSelectedRowIndex(component: JComponent): Int = when (component) {
        is JTree -> component.leadSelectionRow
        is JTable -> component.selectedRow
        is JList<*> -> component.selectedIndex
        else -> -1
    }

    private fun setSelectedRowIndex(component: JComponent, row: Int) {
        when (component) {
            is JTree -> {
                if (row < 0) {
                    component.clearSelection()
                } else if (component.rowCount > 0) {
                    component.setSelectionRow(row.coerceIn(0, component.rowCount - 1))
                }
            }

            is JTable -> {
                if (row < 0) {
                    component.clearSelection()
                } else if (component.rowCount > 0) {
                    val target = row.coerceIn(0, component.rowCount - 1)
                    component.setRowSelectionInterval(target, target)
                }
            }

            is JList<*> -> {
                if (row < 0) {
                    component.clearSelection()
                } else if (component.model.size > 0) {
                    component.selectedIndex = row.coerceIn(0, component.model.size - 1)
                }
            }
        }
    }

    private fun isPlainKey(e: KeyEvent): Boolean = !(e.isControlDown || e.isMetaDown) && !e.isAltDown && !e.isShiftDown
}
