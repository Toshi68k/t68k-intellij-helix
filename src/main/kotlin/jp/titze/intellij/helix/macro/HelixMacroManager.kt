// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.macro

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate

object HelixMacroManager {

    private const val ACTION_MACRO_MANAGER_CLASS = "com.intellij.ide.actionMacro.ActionMacroManager"
    private const val ACTION_MACRO_CLASS = "com.intellij.ide.actionMacro.ActionMacro"

    fun getAllMacros(): List<HelixMacroItem> = try {
        val clazz = Class.forName(ACTION_MACRO_MANAGER_CLASS)
        val manager = clazz.getMethod("getInstance").invoke(null)
        val getAllMacrosMethod = clazz.getMethod("getAllMacros")
        val macrosArray = getAllMacrosMethod.invoke(manager) as? Array<*> ?: emptyArray<Any>()
        macrosArray.filterNotNull().map { macro ->
            HelixMacroItem.fromActionMacro(macro)
        }
    } catch (_: Throwable) {
        emptyList()
    }

    fun playMacro(item: HelixMacroItem, count: Int = 1, editor: Editor? = null) {
        repeat(count) {
            playMacroOnce(item, editor)
        }
    }

    fun playMacro(rawMacro: Any, count: Int = 1, editor: Editor? = null) {
        val item = HelixMacroItem.fromActionMacro(rawMacro)
        playMacro(item, count, editor)
    }

    private fun playMacroOnce(item: HelixMacroItem, editor: Editor?) {
        if (editor != null) {
            if (!item.isRecent && item.name != HelixMacroItem.NO_NAME_MACRO) {
                val actionId = "Macro." + item.name
                val actionManager = ActionManager.getInstance()
                if (actionManager.getAction(actionId) != null) {
                    HelixActionDelegate.executeAction(actionId, editor)
                    return
                }
            } else if (item.isRecent) {
                val actionManager = ActionManager.getInstance()
                if (actionManager.getAction("PlaybackLastMacro") != null) {
                    HelixActionDelegate.executeAction("PlaybackLastMacro", editor)
                    return
                }
            }
        }

        val rawMacro = item.rawMacro ?: return
        try {
            val managerClass = Class.forName(ACTION_MACRO_MANAGER_CLASS)
            val macroClass = Class.forName(ACTION_MACRO_CLASS)
            val manager = managerClass.getMethod("getInstance").invoke(null)
            val playMethod = managerClass.getMethod("playMacro", macroClass)
            playMethod.invoke(manager, rawMacro)
        } catch (_: Throwable) {
            // Ignored
        }
    }
}
