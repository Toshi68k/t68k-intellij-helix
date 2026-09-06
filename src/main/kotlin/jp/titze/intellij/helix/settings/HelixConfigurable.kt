package jp.titze.intellij.helix.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class HelixConfigurable : SearchableConfigurable {

    private var stockHelixRadio: JBRadioButton? = null
    private var popupRadio: JBRadioButton? = null

    override fun getId(): String = "jp.titze.intellij.helix.settings"

    override fun getDisplayName(): String = "Helix Keymap"

    override fun createComponent(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, 16))
        mainPanel.border = JBUI.Borders.empty(16)

        val searchSection = JPanel(BorderLayout(0, 8))
        val titleLabel = JBLabel("Search and Selection Prompt UI")
        titleLabel.font = JBUI.Fonts.label().asBold()
        searchSection.add(titleLabel, BorderLayout.NORTH)

        val radioGroup = ButtonGroup()
        stockHelixRadio = JBRadioButton("Stock Helix: Inline bottom bar with live search/select-as-you-type")
        popupRadio = JBRadioButton("Popup Dialog: Floating centered dialog window")

        radioGroup.add(stockHelixRadio)
        radioGroup.add(popupRadio)

        val optionsPanel = JPanel(GridLayout(2, 1, 0, 6))
        optionsPanel.border = JBUI.Borders.emptyLeft(12)
        optionsPanel.add(stockHelixRadio)
        optionsPanel.add(popupRadio)

        val helpLabel = JBLabel(
            "<html>Applies to <code>/</code> (search), <code>?</code> (reverse search), <code>s</code> (regex select), and <code>S</code> (regex split).<br/>In Stock Helix mode, matches and selections update live in the editor buffer as you type. Pressing <b>Esc</b> cancels and restores original selections.</html>",
        )
        helpLabel.font = JBUI.Fonts.smallFont()
        helpLabel.foreground = UIUtil.getContextHelpForeground()
        helpLabel.border = JBUI.Borders.emptyLeft(12)

        val contentBox = JPanel(BorderLayout(0, 8))
        contentBox.add(optionsPanel, BorderLayout.NORTH)
        contentBox.add(helpLabel, BorderLayout.CENTER)

        searchSection.add(contentBox, BorderLayout.CENTER)
        mainPanel.add(searchSection, BorderLayout.NORTH)

        reset()
        return mainPanel
    }

    override fun isModified(): Boolean {
        val current = HelixSettings.instance.searchUiMode
        val selected = if (stockHelixRadio?.isSelected == true) {
            HelixSearchUiMode.STOCK_HELIX
        } else {
            HelixSearchUiMode.POPUP
        }
        return current != selected
    }

    override fun apply() {
        val newMode = if (stockHelixRadio?.isSelected == true) {
            HelixSearchUiMode.STOCK_HELIX
        } else {
            HelixSearchUiMode.POPUP
        }
        HelixSettings.instance.searchUiMode = newMode
    }

    override fun reset() {
        val current = HelixSettings.instance.searchUiMode
        stockHelixRadio?.isSelected = (current == HelixSearchUiMode.STOCK_HELIX)
        popupRadio?.isSelected = (current == HelixSearchUiMode.POPUP)
    }

    override fun disposeUIResources() {
        stockHelixRadio = null
        popupRadio = null
    }
}
