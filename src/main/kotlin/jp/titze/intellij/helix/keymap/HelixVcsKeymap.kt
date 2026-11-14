// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate

internal object HelixVcsKeymap {

    fun handle(ch: Char, editor: Editor): Boolean = when (ch) {
        'r' -> HelixActionDelegate.executeAction("Vcs.RollbackChangedLines", editor)

        'd' -> {
            HelixActionDelegate.executeAction("Compare.SameVersion", editor) ||
                HelixActionDelegate.executeAction("Diff.ShowDiff", editor)
        }

        'h' -> {
            HelixActionDelegate.executeAction("Vcs.ShowTabbedFileHistory", editor) ||
                HelixActionDelegate.executeAction("Vcs.ShowHistory", editor)
        }

        's' -> HelixActionDelegate.executeAction("Git.Stash", editor)

        'u' -> HelixActionDelegate.executeAction("Git.Unstash", editor)

        'b' -> HelixActionDelegate.executeAction("Annotate", editor)

        'c' -> HelixActionDelegate.executeAction("CheckinProject", editor)

        'p' -> HelixActionDelegate.executeAction("Vcs.Push", editor)

        'P' -> HelixActionDelegate.executeAction("Vcs.UpdateProject", editor)

        'l' -> HelixActionDelegate.executeAction("Vcs.Show.Log", editor)

        'B' -> {
            HelixActionDelegate.executeAction("Git.Branches", editor) ||
                HelixActionDelegate.executeAction("Vcs.Branches", editor)
        }

        '?', ' ' -> HelixActionDelegate.executeAction("Vcs.QuickListPopupAction", editor)

        else -> false
    }
}
