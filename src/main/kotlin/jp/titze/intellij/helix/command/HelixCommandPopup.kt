package jp.titze.intellij.helix.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBTextField
import jp.titze.intellij.helix.action.HelixActionDelegate
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel

object HelixCommandPopup {

    fun show(editor: Editor) {
        val textField = JBTextField()
        textField.preferredSize = Dimension(300, 28)

        val panel = JPanel(BorderLayout())
        panel.add(textField, BorderLayout.CENTER)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, textField)
            .setTitle("Helix Command")
            .setRequestFocus(true)
            .setFocusable(true)
            .setMovable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(true)
            .createPopup()

        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val command = textField.text.trim().removePrefix(":")
                        popup.cancel()
                        executeCommand(command, editor)
                    }
                    KeyEvent.VK_ESCAPE -> {
                        popup.cancel()
                    }
                }
            }
        })

        val project = editor.project
        if (project != null) {
            popup.showCenteredInCurrentWindow(project)
        } else {
            popup.showInBestPositionFor(editor)
        }
    }

    private fun executeCommand(cmd: String, editor: Editor) {
        when (cmd) {
            "w", "write" -> {
                HelixActionDelegate.executeAction("SaveAll", editor)
            }
            "q", "quit" -> {
                HelixActionDelegate.executeAction("CloseContent", editor)
            }
            "wq", "x" -> {
                HelixActionDelegate.executeAction("SaveAll", editor)
                ApplicationManager.getApplication().invokeLater {
                    HelixActionDelegate.executeAction("CloseContent", editor)
                }
            }
            "wa" -> {
                HelixActionDelegate.executeAction("SaveAll", editor)
            }
            "qa" -> {
                HelixActionDelegate.executeAction("CloseAllEditors", editor)
            }
            "vsp" -> {
                HelixActionDelegate.executeAction("SplitVertically", editor)
            }
            "sp" -> {
                HelixActionDelegate.executeAction("SplitHorizontally", editor)
            }
            "format" -> {
                HelixActionDelegate.executeAction("ReformatCode", editor)
            }
        }
    }
}
