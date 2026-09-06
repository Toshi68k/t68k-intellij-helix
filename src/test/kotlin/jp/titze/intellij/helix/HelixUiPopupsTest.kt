package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixPromptType
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixUiPopupsTest : BasePlatformTestCase() {

    fun testSpaceMenuChords() {
        myFixture.configureByText("test.txt", "sample text for space testing")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Press ' ' begins chord
        HelixKeyHandler.handleKey(' ', editor)
        state.pendingSequence shouldBe " "

        // Second key clears chord and executes handler
        HelixKeyHandler.handleKey('y', editor)
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testWhichKeyChordsSequenceReset() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('g', editor)
        state.pendingSequence shouldBe "g"

        HelixKeyHandler.handleKey('h', editor) // move line start
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testHelixCommandPopupMatchingAndExecution() {
        myFixture.configureByText("test.txt", "line 1\nline 2")
        val editor = myFixture.editor

        // Verify command item matching
        val writeCmd = HelixCommandPopup.COMMANDS.first { it.name == "write" }
        writeCmd.matches("w").shouldBeTrue()
        writeCmd.matches("write").shouldBeTrue()
        writeCmd.matches("unknown_xyz").shouldBeFalse()

        // Verify colon triggering HelixCommandPopup.show in tests without throwing
        HelixKeyHandler.handleKey(':', editor)
        // Execute command directly
        HelixCommandPopup.executeCommand("format", editor)
    }

    fun testHelixSettingsSearchUiMode() {
        val settings = HelixSettings.instance
        val original = settings.searchUiMode

        try {
            settings.searchUiMode = HelixSearchUiMode.STOCK_HELIX
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX

            settings.searchUiMode = HelixSearchUiMode.POPUP
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP
        } finally {
            settings.searchUiMode = original
        }
    }

    fun testHelixCommandToggleSearchUi() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor
        val settings = HelixSettings.instance
        val original = settings.searchUiMode

        try {
            settings.searchUiMode = HelixSearchUiMode.STOCK_HELIX
            HelixCommandPopup.executeCommand("toggle-search-ui", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP

            HelixCommandPopup.executeCommand("toggle-search-ui", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX

            HelixCommandPopup.executeCommand("set search-ui=popup", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP

            HelixCommandPopup.executeCommand("set search-ui=inline", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX
        } finally {
            settings.searchUiMode = original
        }
    }

    fun testPromptBarComponentCreation() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor

        val bar = HelixPromptBar.getOrCreate(editor)
        bar.shouldNotBeNull()
        HelixPromptBar.getOrCreate(editor) shouldBeSameInstanceAs bar

        bar.show(HelixPromptType.SEARCH)
        bar.isVisible.shouldBeTrue()

        bar.cancelAndClose()
        bar.isVisible.shouldBeFalse()
    }

    fun testHelixWhichKeyPopupPositionCalculation() {
        // Normal dimensions: 1000x800 viewport, 300x200 popup, margin 20
        val pos = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 1000,
            viewHeight = 800,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 0,
            visibleY = 0,
            marginX = 20,
            marginY = 20,
        )
        pos.x shouldBe 680 // 1000 - 300 - 20
        pos.y shouldBe 580 // 800 - 200 - 20

        // With viewport offset: visibleX = 50, visibleY = 100
        val posWithOffset = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 1000,
            viewHeight = 800,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 50,
            visibleY = 100,
            marginX = 20,
            marginY = 20,
        )
        posWithOffset.x shouldBe 730 // 50 + 680
        posWithOffset.y shouldBe 680 // 100 + 580

        // Very small viewport (smaller than popup) clamps to 0 offset
        val posClamped = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 200,
            viewHeight = 150,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 0,
            visibleY = 0,
            marginX = 20,
            marginY = 20,
        )
        posClamped.x shouldBe 0
        posClamped.y shouldBe 0
    }

    fun testHelixWhichKeyPopupTriggerAndHide() {
        myFixture.configureByText("test.txt", "line 1\nline 2\n")
        val editor = myFixture.editor

        // Typing space or g should trigger which-key show safely without throwing
        HelixWhichKeyPopup.show(editor, " ")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "g")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "m")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "[")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "]")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "z")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "Z")
        HelixWhichKeyPopup.hide()
        HelixWhichKeyPopup.isShowing() shouldBe false
    }
}
