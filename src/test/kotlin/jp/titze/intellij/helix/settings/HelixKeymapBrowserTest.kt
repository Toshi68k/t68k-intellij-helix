// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTabbedPane
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.editor.HelixInsertTracker
import jp.titze.intellij.helix.keymap.BindingSource
import jp.titze.intellij.helix.keymap.HelixActionExtractor
import jp.titze.intellij.helix.keymap.HelixIntelliJAction
import jp.titze.intellij.helix.keymap.HelixKeymapExtractor
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.keymap.KeymapPrefixGroup
import jp.titze.intellij.helix.motion.HelixMotionHistory
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.state.HelixMode

class HelixKeymapBrowserTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixRegisterManager.clear()
        HelixMotionHistory.lastMotion = null
        HelixInsertTracker.reset()
        HelixKeymapManager.resetToDefaults()
    }

    override fun tearDown() {
        HelixRegisterManager.clear()
        HelixMotionHistory.lastMotion = null
        HelixInsertTracker.reset()
        HelixKeymapManager.resetToDefaults()
        super.tearDown()
    }

    fun testKeymapExtractorExtractsDefaultRows() {
        val normalRows = HelixKeymapExtractor.extractRows(HelixMode.NORMAL)
        normalRows.isNotEmpty().shouldBeTrue()

        val spaceF = normalRows.firstOrNull { it.rawSequence == "space f" }
        spaceF.shouldNotBeNull()
        spaceF.displayShortcut shouldBe "space + f"
        spaceF.command shouldBe "file_picker"
        spaceF.source shouldBe BindingSource.DEFAULT
        spaceF.prefixGroup shouldBe KeymapPrefixGroup.SPACE

        val gd = normalRows.firstOrNull { it.rawSequence == "g d" }
        gd.shouldNotBeNull()
        gd.displayShortcut shouldBe "g + d"
        gd.command shouldBe "goto_definition"
        gd.prefixGroup shouldBe KeymapPrefixGroup.GOTO

        val moveH = normalRows.firstOrNull { it.rawSequence == "h" }
        moveH.shouldNotBeNull()
        moveH.command shouldBe "move_char_left"
        moveH.prefixGroup shouldBe KeymapPrefixGroup.STANDALONE

        val matchMm = normalRows.firstOrNull { it.rawSequence == "m m" }
        matchMm.shouldNotBeNull()
        matchMm.command shouldBe "match_brackets"
        matchMm.prefixGroup shouldBe KeymapPrefixGroup.MATCH

        val matchMs = normalRows.firstOrNull { it.rawSequence == "m s" }
        matchMs.shouldNotBeNull()
        matchMs.command shouldBe "surround_add"
        matchMs.prefixGroup shouldBe KeymapPrefixGroup.MATCH

        val bracketPrevDiag = normalRows.firstOrNull { it.rawSequence == "[ d" }
        bracketPrevDiag.shouldNotBeNull()
        bracketPrevDiag.command shouldBe "goto_prev_diag"
        bracketPrevDiag.prefixGroup shouldBe KeymapPrefixGroup.BRACKET

        val bracketNextDiag = normalRows.firstOrNull { it.rawSequence == "] d" }
        bracketNextDiag.shouldNotBeNull()
        bracketNextDiag.command shouldBe "goto_next_diag"
        bracketNextDiag.prefixGroup shouldBe KeymapPrefixGroup.BRACKET
    }

    fun testAllPrefixGroupsHaveDefaultBindings() {
        val normalRows = HelixKeymapExtractor.extractRows(HelixMode.NORMAL)
        val groups = KeymapPrefixGroup.entries.filter { it != KeymapPrefixGroup.ALL }
        for (group in groups) {
            val count = normalRows.count { it.prefixGroup == group }
            (count > 0).shouldBeTrue()
        }
    }

    fun testCustomAndNoOpBindingDetection() {
        HelixKeymapManager.bind(HelixMode.NORMAL, "space t", ":action ActivateTerminalToolWindow")
        HelixKeymapManager.bind(HelixMode.NORMAL, "backspace", "no_op")

        val normalRows = HelixKeymapExtractor.extractRows(HelixMode.NORMAL)

        val customRow = normalRows.firstOrNull { it.rawSequence == "space t" }
        customRow.shouldNotBeNull()
        customRow.source.shouldBeInstanceOf<BindingSource>()
        (customRow.source == BindingSource.CUSTOM_INTELLIJ || customRow.source == BindingSource.CUSTOM_CONFIG)
            .shouldBeTrue()
        customRow.description.shouldContain("ActivateTerminalToolWindow")

        val disabledRow = normalRows.firstOrNull { it.rawSequence == "backspace" }
        disabledRow.shouldNotBeNull()
        disabledRow.source shouldBe BindingSource.NO_OP
        disabledRow.description shouldBe "(Disabled)"
    }

    fun testKeymapBrowserPanelFiltering() {
        val panel = HelixKeymapBrowserPanel()
        val table = panel.table
        val model = table.model as HelixKeymapBrowserPanel.HelixKeymapTableModel

        model.rowCount.shouldBeGreaterThan(0)

        val initialCount = model.rowCount
        val spaceRows = (0 until initialCount).mapNotNull { model.getRow(it) }
            .filter { it.prefixGroup == KeymapPrefixGroup.SPACE }
        spaceRows.isNotEmpty().shouldBeTrue()

        panel.reloadModeData()
        model.rowCount.shouldBeGreaterThan(0)
    }

    fun testConfigurableTabbedPaneStructure() {
        val configurable = HelixConfigurable()
        val component = configurable.createComponent()
        component.shouldNotBeNull()
        component.shouldBeInstanceOf<JBTabbedPane>()

        val tabs = component
        tabs.tabCount shouldBe 3
        tabs.getTitleAt(0) shouldBe "General"
        tabs.getTitleAt(1) shouldBe "Appearance"
        tabs.getTitleAt(2) shouldBe "Behavior"

        configurable.isModified.shouldBeFalse()
        configurable.disposeUIResources()

        val keybindingsConfigurable = HelixKeybindingsConfigurable()
        val kbComponent = keybindingsConfigurable.createComponent()
        kbComponent.shouldNotBeNull()
        kbComponent.shouldBeInstanceOf<JBTabbedPane>()

        val kbTabs = kbComponent
        kbTabs.tabCount shouldBe 2
        kbTabs.getTitleAt(0) shouldBe "Keybindings"
        kbTabs.getTitleAt(1) shouldBe "Ctrl Keys"

        keybindingsConfigurable.isModified.shouldBeFalse()
        keybindingsConfigurable.disposeUIResources()

        val configFileConfigurable = HelixConfigFileConfigurable()
        configFileConfigurable.createComponent().shouldNotBeNull()
        configFileConfigurable.isModified.shouldBeFalse()
        configFileConfigurable.disposeUIResources()
    }

    fun testActionExtractorFiltersQueries() {
        val actions = listOf(
            HelixIntelliJAction("ReformatCode", "Reformat Code", "Formats active file", "⌥⌘L"),
            HelixIntelliJAction("ActivateTerminalToolWindow", "Terminal", "Opens terminal", "⌥F12"),
            HelixIntelliJAction("Git.Commit", "Commit...", "Commit changes to git", "⌘K"),
        )

        val emptyFilter = HelixActionExtractor.filterActions(actions, "")
        emptyFilter.size shouldBe 3

        val termFilter = HelixActionExtractor.filterActions(actions, "terminal")
        termFilter.size shouldBe 1
        termFilter.first().actionId shouldBe "ActivateTerminalToolWindow"

        val multiTermFilter = HelixActionExtractor.filterActions(actions, "format file")
        multiTermFilter.size shouldBe 1
        multiTermFilter.first().actionId shouldBe "ReformatCode"

        val noMatch = HelixActionExtractor.filterActions(actions, "nonexistentaction")
        noMatch.isEmpty().shouldBeTrue()
    }

    fun testKeymapExtractorFlagsShadowedRows() {
        HelixKeymapManager.bind(HelixMode.NORMAL, "g", "move_char_left")

        val normalRows = HelixKeymapExtractor.extractRows(HelixMode.NORMAL)
        val gRow = normalRows.firstOrNull { it.rawSequence == "g" }
        gRow.shouldNotBeNull()
        gRow.command shouldBe "move_char_left"
        gRow.isShadowed.shouldBeTrue()
        gRow.conflictReason.shouldNotBeNull()
        gRow.conflictReason.contains("child chords take precedence").shouldBeTrue()
    }

    fun testKeymapBrowserActionButtonsStateOnSelection() {
        val browser = HelixKeymapBrowserPanel()
        browser.addBindingBtn.isEnabled.shouldBeTrue()
        browser.editBindingBtn.isEnabled.shouldBeFalse()
        browser.resetBindingBtn.isEnabled.shouldBeFalse()
        browser.disableBindingBtn.isEnabled.shouldBeFalse()

        browser.table.setRowSelectionInterval(0, 0)
        browser.updateActionButtonsState()

        browser.editBindingBtn.isEnabled.shouldBeTrue()
        browser.disableBindingBtn.isEnabled.shouldBeTrue()
        // Default binding should not be resettable
        browser.resetBindingBtn.isEnabled.shouldBeFalse()

        // Bind custom shortcut and select it
        HelixKeymapManager.bind(HelixMode.NORMAL, "C-s", ":w")
        browser.reloadModeData()
        val customIdx = (0 until browser.table.rowCount).first {
            browser.table.getValueAt(it, 0).toString().contains("C-s")
        }
        browser.table.setRowSelectionInterval(customIdx, customIdx)
        browser.updateActionButtonsState()
        browser.resetBindingBtn.isEnabled.shouldBeTrue()
        browser.disableBindingBtn.isEnabled.shouldBeTrue()

        // Bind no_op shortcut
        HelixKeymapManager.bind(HelixMode.NORMAL, "C-x", "no_op")
        browser.reloadModeData()
        val noOpIdx = (0 until browser.table.rowCount).first {
            browser.table.getValueAt(it, 0).toString().contains("C-x")
        }
        browser.table.setRowSelectionInterval(noOpIdx, noOpIdx)
        browser.updateActionButtonsState()
        browser.disableBindingBtn.isEnabled.shouldBeFalse()
        browser.resetBindingBtn.isEnabled.shouldBeTrue()
    }

    fun testKeybindingEditDialogValidationAndResults() {
        val browser = HelixKeymapBrowserPanel()
        val dialog = HelixKeybindingEditDialog(
            browser.panel,
            initialMode = HelixMode.NORMAL,
            initialShortcut = "space + f",
            initialAction = ":action GotoFile",
        )
        dialog.resultMode shouldBe HelixMode.NORMAL
        // Normalizes ' + ' to space chord
        dialog.resultShortcut shouldBe "space f"
        dialog.resultAction shouldBe ":action GotoFile"
        dialog.doValidate().shouldBeNull()

        // ActionType toString() matches localization bundle
        HelixKeybindingEditDialog.ActionType.INTELLIJ.toString() shouldBe
            HelixBundle.message("dialog.editKeybinding.type.intellij")
        HelixKeybindingEditDialog.ActionType.NO_OP.toString() shouldBe
            HelixBundle.message("dialog.editKeybinding.type.noop")
    }

    fun testKeymapBrowserSelectRowBySequence() {
        val browser = HelixKeymapBrowserPanel()
        browser.selectRowBySequence("space f")
        browser.table.selectedRow.shouldBeGreaterThan(-1)
        val selected = browser.getSelectedRowData()
        selected.shouldNotBeNull()
        selected.rawSequence shouldBe "space f"
    }

    private fun Int.shouldBeGreaterThan(other: Int) {
        (this > other).shouldBeTrue()
    }
}
