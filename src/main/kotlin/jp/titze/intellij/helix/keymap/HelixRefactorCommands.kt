// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

internal object HelixRefactorCommands {

    fun registerAll() {
        HelixCommandRegistry.register("extract_variable", "Extract variable", "IntroduceVariable") { ed, _ ->
            executeRefactor("IntroduceVariable", ed)
        }
        HelixCommandRegistry.register("extract_method", "Extract method", "ExtractMethod") { ed, _ ->
            executeRefactor("ExtractMethod", ed)
        }
        HelixCommandRegistry.register("extract_constant", "Extract constant", "IntroduceConstant") { ed, _ ->
            executeRefactor("IntroduceConstant", ed)
        }
        HelixCommandRegistry.register("extract_field", "Extract field", "IntroduceField") { ed, _ ->
            executeRefactor("IntroduceField", ed)
        }
        HelixCommandRegistry.register("extract_parameter", "Extract parameter", "IntroduceParameter") { ed, _ ->
            executeRefactor("IntroduceParameter", ed)
        }
        HelixCommandRegistry.register("inline", "Inline symbol", "Inline") { ed, _ ->
            executeRefactor("Inline", ed)
        }
        HelixCommandRegistry.register("change_signature", "Change signature", "ChangeSignature") { ed, _ ->
            executeRefactor("ChangeSignature", ed)
        }
        HelixCommandRegistry.register("move", "Move symbol or file", "Move") { ed, _ ->
            executeRefactor("Move", ed)
        }
        HelixCommandRegistry.register("override_methods", "Override methods", "OverrideMethods") { ed, _ ->
            executeRefactor("OverrideMethods", ed)
        }
        HelixCommandRegistry.register("implement_methods", "Implement methods", "ImplementMethods") { ed, _ ->
            executeRefactor("ImplementMethods", ed)
        }
        HelixCommandRegistry.register("surround_with", "Surround with template", "SurroundWith") { ed, _ ->
            executeRefactor("SurroundWith", ed)
        }
        HelixCommandRegistry.register(
            "refactor_this",
            "Refactor this menu",
            "Refactorings.QuickListPopupAction",
        ) { ed, _ ->
            executeRefactor("Refactorings.QuickListPopupAction", ed)
        }
    }

    fun executeRefactor(actionId: String, editor: Editor): Boolean {
        val result = HelixActionDelegate.executeAction(actionId, editor)
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode == HelixMode.SELECT) {
            state.setMode(HelixMode.NORMAL)
        }
        return result
    }
}
