// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.keymap.HelixKeyStroke
import jp.titze.intellij.helix.keymap.HelixKeymapExtractor
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Modal dialog for capturing keystrokes and chords to filter the keymap explorer.
 */
class HelixShortcutCaptureDialog(parent: JComponent) : DialogWrapper(parent, true) {

    private val recordedStrokes = mutableListOf<HelixKeyStroke>()
    private val displayLabel = JBLabel(" ", SwingConstants.CENTER)

    val recordedShortcut: String
        get() = HelixKeymapExtractor.formatDisplayShortcut(recordedStrokes)

    val rawSequence: String
        get() = HelixKeymapExtractor.formatRawSequence(recordedStrokes)

    init {
        title = HelixBundle.message("dialog.recordShortcut.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 12))
        root.preferredSize = Dimension(JBUI.scale(380), JBUI.scale(160))
        root.border = JBUI.Borders.empty(12)

        val headerLabel = JBLabel(HelixBundle.message("dialog.recordShortcut.prompt"))
        headerLabel.font = JBUI.Fonts.label().asBold()

        val displayCard = JPanel(BorderLayout())
        displayCard.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIUtil.getBoundsColor(), 1, true),
            JBUI.Borders.empty(12),
        )
        displayCard.background = UIUtil.getTextFieldBackground()

        displayLabel.font = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scale(14).toFloat())
        displayLabel.foreground = UIUtil.getLabelForeground()
        displayCard.add(displayLabel, BorderLayout.CENTER)

        val helpLabel = JBLabel(HelixBundle.message("dialog.recordShortcut.help"))
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()

        val clearButton = JButton(HelixBundle.message("dialog.recordShortcut.clear"))
        clearButton.isFocusable = false
        clearButton.addActionListener {
            recordedStrokes.clear()
            updateDisplay()
        }

        val centerBox = JPanel()
        centerBox.layout = BoxLayout(centerBox, BoxLayout.Y_AXIS)
        centerBox.add(headerLabel)
        centerBox.add(Box.createVerticalStrut(JBUI.scale(8)))
        centerBox.add(displayCard)
        centerBox.add(Box.createVerticalStrut(JBUI.scale(6)))
        centerBox.add(helpLabel)

        root.add(centerBox, BorderLayout.CENTER)
        root.add(clearButton, BorderLayout.SOUTH)

        root.isFocusable = true
        root.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                handleKeyEvent(e)
            }
        })

        updateDisplay()
        return root
    }

    private fun handleKeyEvent(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ESCAPE) {
            doCancelAction()
            e.consume()
            return
        }
        if (e.keyCode == KeyEvent.VK_ENTER && recordedStrokes.isNotEmpty()) {
            doOKAction()
            e.consume()
            return
        }

        if (isModifierKey(e.keyCode)) return

        val stroke = HelixKeyStroke.fromKeyEvent(e)
        recordedStrokes.add(stroke)
        updateDisplay()
        e.consume()
    }

    private fun isModifierKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.VK_SHIFT,
        KeyEvent.VK_CONTROL,
        KeyEvent.VK_ALT,
        KeyEvent.VK_META,
        KeyEvent.VK_ALT_GRAPH,
        -> true

        else -> false
    }

    private fun updateDisplay() {
        if (recordedStrokes.isEmpty()) {
            displayLabel.text = "<press keys>"
            displayLabel.foreground = UIUtil.getInactiveTextColor()
            isOKActionEnabled = false
        } else {
            displayLabel.text = recordedShortcut
            displayLabel.foreground = UIUtil.getLabelForeground()
            isOKActionEnabled = true
        }
    }

    override fun createActions(): Array<Action> {
        okAction.putValue(Action.NAME, HelixBundle.message("dialog.recordShortcut.apply"))
        return arrayOf(okAction, cancelAction)
    }
}
