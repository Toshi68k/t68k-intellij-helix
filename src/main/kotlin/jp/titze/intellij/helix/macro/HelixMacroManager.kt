// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.macro

import com.intellij.ide.actionMacro.ActionMacro
import com.intellij.ide.actionMacro.ActionMacroManager

object HelixMacroManager {

    fun getAllMacros(): List<HelixMacroItem> {
        val manager = ActionMacroManager.getInstance()
        return manager.allMacros.map { macro ->
            HelixMacroItem.fromActionMacro(macro)
        }
    }

    fun playMacro(macro: ActionMacro, count: Int = 1) {
        val manager = ActionMacroManager.getInstance()
        repeat(count) {
            manager.playMacro(macro)
        }
    }
}
