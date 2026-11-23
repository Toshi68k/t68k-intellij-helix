// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.harpoon.HelixHarpoonService
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.ui.HelixWhichKeyMenus

class HelixHarpoonTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixKeymapManager.resetToDefaults()
        val service = HelixHarpoonService.getInstance(myFixture.project)
        service.clearAll()
    }

    override fun tearDown() {
        val service = HelixHarpoonService.getInstance(myFixture.project)
        service.clearAll()
        HelixKeymapManager.resetToDefaults()
        super.tearDown()
    }

    fun testHarpoonServiceSlotOperations() {
        myFixture.configureByText("ServiceA.kt", "class ServiceA {\n    val a = 1\n}")
        val editorA = myFixture.editor
        val service = HelixHarpoonService.getInstance(myFixture.project)

        // Pin slot 0 (Slot 1)
        service.setSlot(0, editorA).shouldBeTrue()
        val slot0 = service.getSlot(0)
        slot0.shouldNotBeNull()
        slot0.virtualFile.name shouldBe "ServiceA.kt"
        slot0.line shouldBe 1

        // Pin current to next slot
        service.pinCurrent(editorA) shouldBe 1 // Already in slot 0 (1-based index)

        // Clear slot
        service.clearSlot(0).shouldBeTrue()
        service.getSlot(0) shouldBe null
    }

    fun testHarpoonRangeMarkerShifting() {
        myFixture.configureByText("RangeTest.kt", "line 1\nline 2\ntarget line\nline 4")
        val editor = myFixture.editor
        val service = HelixHarpoonService.getInstance(myFixture.project)

        // Move to line 3 ("target line", index 2)
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(2))
        service.setSlot(0, editor).shouldBeTrue()

        val slotBefore = service.getSlot(0).shouldNotBeNull()
        slotBefore.offset shouldBe editor.document.getLineStartOffset(2)

        // Insert text at start of document
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.insertString(0, "prepended line 1\nprepended line 2\n")
        }

        // RangeMarker should have shifted automatically
        val slotAfter = service.getSlot(0).shouldNotBeNull()
        slotAfter.offset shouldBe editor.document.getLineStartOffset(4)
    }

    fun testHarpoonSlotSwapping() {
        val file1 = myFixture.configureByText("One.kt", "one")
        val editor1 = myFixture.editor
        val service = HelixHarpoonService.getInstance(myFixture.project)
        service.setSlot(0, editor1)

        val file2 = myFixture.configureByText("Two.kt", "two")
        val editor2 = myFixture.editor
        service.setSlot(1, editor2)

        service.swapSlots(0, 1).shouldBeTrue()
        service.getSlot(0)?.virtualFile?.name shouldBe file2.name
        service.getSlot(1)?.virtualFile?.name shouldBe file1.name
    }

    fun testSpaceFastPathDirectJump() {
        myFixture.configureByText("Target.kt", "first\nsecond\nthird")
        val editor = myFixture.editor
        val service = HelixHarpoonService.getInstance(myFixture.project)

        // Move to line 2 and pin slot 1
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(1))
        service.setSlot(0, editor).shouldBeTrue()

        // Move caret elsewhere
        editor.caretModel.moveToOffset(0)

        // Trigger space + '1'
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('1', editor).shouldBeTrue()

        editor.caretModel.offset shouldBe editor.document.getLineStartOffset(1)
    }

    fun testHarpoonKeymapChords() {
        myFixture.configureByText("ChordTest.kt", "alpha\nbeta\ngamma")
        val editor = myFixture.editor
        val service = HelixHarpoonService.getInstance(myFixture.project)

        // space + H + a -> pins current file
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('H', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('a', editor).shouldBeTrue()

        service.getSlot(0).shouldNotBeNull()
        service.getSlot(0)?.virtualFile?.name shouldBe "ChordTest.kt"

        // space + H + s + 3 -> set slot 3 (index 2)
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('H', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('s', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('3', editor).shouldBeTrue()

        service.getSlot(2).shouldNotBeNull()
        service.getSlot(2)?.virtualFile?.name shouldBe "ChordTest.kt"

        // space + H + d + 3 -> delete slot 3 (index 2)
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('H', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('d', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('3', editor).shouldBeTrue()

        service.getSlot(2) shouldBe null
    }

    fun testWhichKeyMenusContainHarpoon() {
        myFixture.configureByText("MenuTest.kt", "content")
        val editor = myFixture.editor

        // Root space items contain Harpoon entries
        HelixWhichKeyMenus.spaceItems.any { it.key == "1-5" }.shouldBeTrue()
        HelixWhichKeyMenus.spaceItems.any { it.key == "H" }.shouldBeTrue()

        // Harpoon submenus resolve
        val harpoonMenu = HelixWhichKeyMenus.getMenu(" H", editor)
        harpoonMenu.shouldNotBeNull()
        harpoonMenu.second.any { it.key == "1" }.shouldBeTrue()
        harpoonMenu.second.any { it.key == "a" }.shouldBeTrue()

        val setMenu = HelixWhichKeyMenus.getMenu(" H s", editor)
        setMenu.shouldNotBeNull()
        setMenu.second.size shouldBe 5

        val deleteMenu = HelixWhichKeyMenus.getMenu(" H d", editor)
        deleteMenu.shouldNotBeNull()
        deleteMenu.second.size shouldBe 5
    }

    fun testPaletteCommands() {
        myFixture.configureByText("CommandTest.kt", "line 1\nline 2")
        val editor = myFixture.editor
        val service = HelixHarpoonService.getInstance(myFixture.project)

        // :pin 2
        HelixCommands.execute("pin 2", editor)
        service.getSlot(1).shouldNotBeNull()
        service.getSlot(1)?.virtualFile?.name shouldBe "CommandTest.kt"

        // :unpin 2
        HelixCommands.execute("unpin 2", editor)
        service.getSlot(1) shouldBe null
    }

    fun testHarpoonSnippetTruncationInWhichKey() {
        val longLine = "val veryLongStatementThatShouldBeTruncatedWithAnEllipsisInsteadOfStretchingThePopup = true"
        myFixture.configureByText("LongLine.kt", longLine)
        val editor = myFixture.editor
        val service = HelixHarpoonService.getInstance(myFixture.project)
        service.setSlot(0, editor)

        val harpoonMenu = HelixWhichKeyMenus.getMenu(" H", editor).shouldNotBeNull()
        val slot1Item = harpoonMenu.second.first { it.key == "1" }
        slot1Item.snippet.shouldNotBeNull()
        (slot1Item.snippet.length <= 32).shouldBeTrue()
        slot1Item.snippet.endsWith("…").shouldBeTrue()
    }
}
