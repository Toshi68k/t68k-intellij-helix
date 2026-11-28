// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

internal object HelixRefactorCommands {

    fun registerAll() {
        HelixCommandRegistry.register(
            "extract_variable",
            "Extract variable",
            "IntroduceVariable",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("IntroduceVariable", ed)
        }
        HelixCommandRegistry.register(
            "extract_method",
            "Extract method",
            "ExtractMethod",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("ExtractMethod", ed)
        }
        HelixCommandRegistry.register(
            "extract_constant",
            "Extract constant",
            "IntroduceConstant",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("IntroduceConstant", ed)
        }
        HelixCommandRegistry.register(
            "extract_field",
            "Extract field",
            "IntroduceField",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("IntroduceField", ed)
        }
        HelixCommandRegistry.register(
            "extract_parameter",
            "Extract parameter",
            "IntroduceParameter",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("IntroduceParameter", ed)
        }
        HelixCommandRegistry.register("inline", "Inline symbol", "Inline", requiresWritable = true) { ed, _ ->
            executeRefactor("Inline", ed)
        }
        HelixCommandRegistry.register(
            "change_signature",
            "Change signature",
            "ChangeSignature",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("ChangeSignature", ed)
        }
        HelixCommandRegistry.register("move", "Move symbol or file", "Move", requiresWritable = true) { ed, _ ->
            executeRefactor("Move", ed)
        }
        HelixCommandRegistry.register(
            "override_methods",
            "Override methods",
            "OverrideMethods",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("OverrideMethods", ed)
        }
        HelixCommandRegistry.register(
            "implement_methods",
            "Implement methods",
            "ImplementMethods",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("ImplementMethods", ed)
        }
        HelixCommandRegistry.register(
            "surround_with",
            "Surround with template",
            "SurroundWith",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("SurroundWith", ed)
        }
        HelixCommandRegistry.register("generate", "Generate code", "Generate", requiresWritable = true) { ed, _ ->
            executeRefactor("Generate", ed)
        }
        HelixCommandRegistry.register(
            "safe_delete",
            "Safe delete",
            "SafeDelete",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("SafeDelete", ed)
        }
        HelixCommandRegistry.register(
            "pull_up",
            "Pull members up",
            "MembersPullUp",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("MembersPullUp", ed)
        }
        HelixCommandRegistry.register(
            "push_down",
            "Push members down",
            "PushMembersDown",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("PushMembersDown", ed)
        }
        HelixCommandRegistry.register(
            "refactor_this",
            "Refactor this menu",
            "Refactorings.QuickListPopupAction",
            requiresWritable = true,
        ) { ed, _ ->
            executeRefactor("Refactorings.QuickListPopupAction", ed)
        }
    }

    fun executeRefactor(actionId: String, editor: Editor): Boolean {
        if (!HelixEditorEligibility.ensureWritable(editor)) return true
        val result = HelixActionDelegate.executeAction(actionId, editor)
        val state = HelixStateManager.getOrCreate(editor)
        if (state.mode == HelixMode.SELECT) {
            state.setMode(HelixMode.NORMAL)
        }
        return result
    }
}
