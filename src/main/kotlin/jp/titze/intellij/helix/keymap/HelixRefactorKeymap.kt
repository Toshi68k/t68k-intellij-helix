// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor

internal object HelixRefactorKeymap {

    fun handle(ch: Char, editor: Editor): Boolean {
        val actionId = when (ch) {
            'v' -> "IntroduceVariable"
            'm' -> "ExtractMethod"
            'c' -> "IntroduceConstant"
            'f' -> "IntroduceField"
            'p' -> "IntroduceParameter"
            'i' -> "Inline"
            's' -> "ChangeSignature"
            'r' -> "RenameElement"
            'M' -> "Move"
            'w' -> "SurroundWith"
            'o' -> "OverrideMethods"
            'I' -> "ImplementMethods"
            'g' -> "Generate"
            'd' -> "SafeDelete"
            'u' -> "MembersPullUp"
            'D' -> "PushMembersDown"
            '?', ' ' -> "Refactorings.QuickListPopupAction"
            else -> return false
        }

        return HelixRefactorCommands.executeRefactor(actionId, editor)
    }
}
