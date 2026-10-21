// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTabbedPane
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import jp.titze.intellij.helix.editor.HelixInsertTracker
import jp.titze.intellij.helix.keymap.BindingSource
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

        val tabs = component as JBTabbedPane
        tabs.tabCount shouldBe 4
        tabs.getTitleAt(0) shouldBe "General"
        tabs.getTitleAt(1) shouldBe "Keybindings"
        tabs.getTitleAt(2) shouldBe "Ctrl Keys"
        tabs.getTitleAt(3) shouldBe "Config File (TOML)"

        configurable.isModified.shouldBeFalse()
        configurable.disposeUIResources()
    }

    private fun Int.shouldBeGreaterThan(other: Int) {
        (this > other).shouldBeTrue()
    }
}
