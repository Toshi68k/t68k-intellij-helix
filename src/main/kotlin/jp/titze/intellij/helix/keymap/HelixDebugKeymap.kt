package jp.titze.intellij.helix.keymap

import com.intellij.openapi.editor.Editor
import jp.titze.intellij.helix.action.HelixActionDelegate

internal object HelixDebugKeymap {

    fun handle(ch: Char, editor: Editor): Boolean = when (ch) {
        'b' -> HelixActionDelegate.executeAction("ToggleLineBreakpoint", editor)
        'c' -> HelixActionDelegate.executeAction("Resume", editor)
        's' -> HelixActionDelegate.executeAction("StepInto", editor)
        'n' -> HelixActionDelegate.executeAction("StepOver", editor)
        'o' -> HelixActionDelegate.executeAction("StepOut", editor)
        'p' -> HelixActionDelegate.executeAction("Pause", editor)
        'l' -> HelixActionDelegate.executeAction("Debug", editor)
        'r' -> HelixActionDelegate.executeAction("Rerun", editor)
        't' -> HelixActionDelegate.executeAction("Stop", editor)
        'v' -> HelixActionDelegate.executeAction("ActivateDebugToolWindow", editor)
        'k' -> HelixActionDelegate.executeAction("EvaluateExpression", editor)
        'e' -> HelixActionDelegate.executeAction("EditBreakpoint", editor)
        'B' -> HelixActionDelegate.executeAction("ViewBreakpoints", editor)
        else -> false
    }
}
