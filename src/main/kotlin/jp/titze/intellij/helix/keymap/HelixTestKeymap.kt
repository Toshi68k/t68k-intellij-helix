// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate

internal object HelixTestKeymap {

    fun handle(ch: Char, editor: Editor): Boolean = when (ch) {
        't', 'r' -> {
            HelixActionDelegate.executeAction("RunClass", editor) ||
                HelixActionDelegate.executeAction("Run", editor)
        }

        'd' -> {
            HelixActionDelegate.executeAction("DebugClass", editor) ||
                HelixActionDelegate.executeAction("Debug", editor)
        }

        'l' -> HelixActionDelegate.executeAction("Rerun", editor)

        'f' -> HelixActionDelegate.executeAction("RerunFailedTests", editor)

        'c' -> {
            HelixActionDelegate.executeAction("RunCoverage", editor) ||
                HelixActionDelegate.executeAction("Coverage", editor)
        }

        'a' -> {
            HelixKeyHandler.recordJump(editor)
            HelixActionDelegate.executeAction("GotoTest", editor)
        }

        else -> false
    }
}
