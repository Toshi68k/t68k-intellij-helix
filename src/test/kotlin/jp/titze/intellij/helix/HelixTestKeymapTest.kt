// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.keymap.HelixKeymapConflictDetector
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.keymap.HelixTestKeymap
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixWhichKeyMenus

class HelixTestKeymapTest : BasePlatformTestCase() {

    private val executedActions = mutableListOf<String>()

    override fun setUp() {
        super.setUp()
        executedActions.clear()
        HelixRegisterManager.clear()
        HelixKeymapManager.resetToDefaults()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }
    }

    override fun tearDown() {
        HelixActionDelegate.actionExecutor = null
        HelixRegisterManager.clear()
        HelixKeymapManager.resetToDefaults()
        super.tearDown()
    }

    fun testTestKeymapDirectActions() {
        myFixture.configureByText("TestFile.kt", "class TestFile { }")
        val editor = myFixture.editor

        executedActions.clear()
        HelixTestKeymap.handle('t', editor).shouldBeTrue()
        executedActions shouldBe listOf("RunClass")

        executedActions.clear()
        HelixTestKeymap.handle('r', editor).shouldBeTrue()
        executedActions shouldBe listOf("RunClass")

        executedActions.clear()
        HelixTestKeymap.handle('d', editor).shouldBeTrue()
        executedActions shouldBe listOf("DebugClass")

        executedActions.clear()
        HelixTestKeymap.handle('l', editor).shouldBeTrue()
        executedActions shouldBe listOf("Rerun")

        executedActions.clear()
        HelixTestKeymap.handle('f', editor).shouldBeTrue()
        executedActions shouldBe listOf("RerunFailedTests")

        executedActions.clear()
        HelixTestKeymap.handle('c', editor).shouldBeTrue()
        executedActions shouldBe listOf("RunCoverage")

        executedActions.clear()
        HelixTestKeymap.handle('a', editor).shouldBeTrue()
        executedActions shouldBe listOf("GotoTest")

        executedActions.clear()
        HelixTestKeymap.handle('x', editor).shouldBeFalse()
        executedActions.isEmpty().shouldBeTrue()
    }

    fun testTestKeymapFallbacks() {
        myFixture.configureByText("TestFile.kt", "class TestFile { }")
        val editor = myFixture.editor

        // Fallback for RunClass -> Run
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            actionId != "RunClass" && actionId != "DebugClass" && actionId != "RunCoverage"
        }

        executedActions.clear()
        HelixTestKeymap.handle('t', editor).shouldBeTrue()
        executedActions shouldBe listOf("RunClass", "Run")

        executedActions.clear()
        HelixTestKeymap.handle('d', editor).shouldBeTrue()
        executedActions shouldBe listOf("DebugClass", "Debug")

        executedActions.clear()
        HelixTestKeymap.handle('c', editor).shouldBeTrue()
        executedActions shouldBe listOf("RunCoverage", "Coverage")
    }

    fun testSpaceTestChordSequence() {
        myFixture.configureByText("TestFile.kt", "class TestFile { }")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Type ' ' then 't' then 't' to run test
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
        state.pendingSequence shouldBe " "
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        state.pendingSequence shouldBe " t"

        executedActions.clear()
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""
        executedActions shouldBe listOf("RunClass")

        // Type ' ' then 't' then 'd' to debug test
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
        state.pendingSequence shouldBe " "
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        state.pendingSequence shouldBe " t"

        executedActions.clear()
        HelixKeyHandler.handleKey('d', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""
        executedActions shouldBe listOf("DebugClass")

        // Type ' ' then 't' then 'f' to rerun failed tests
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
        state.pendingSequence shouldBe " "
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        state.pendingSequence shouldBe " t"

        executedActions.clear()
        HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""
        executedActions shouldBe listOf("RerunFailedTests")
    }

    fun testGotoTestChord() {
        myFixture.configureByText("TestFile.kt", "class TestFile { }")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Type 'g' then 'T'
        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        state.pendingSequence shouldBe "g"
        executedActions.clear()
        HelixKeyHandler.handleKey('T', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""
        executedActions shouldBe listOf("GotoTest")
    }

    fun testWhichKeyMenuIntegration() {
        // Space menu must contain 't' for Test menu
        val spaceMenu = HelixWhichKeyMenus.getMenu(" ")
        spaceMenu.shouldNotBeNull()
        val spaceItems = spaceMenu.second
        spaceItems.any { it.key == "t" && it.helixCommand == "test_menu" }.shouldBeTrue()

        // Goto menu must contain 'T' for Toggle test / subject
        val gotoMenu = HelixWhichKeyMenus.getMenu("g")
        gotoMenu.shouldNotBeNull()
        val gotoItems = gotoMenu.second
        gotoItems.any {
            it.key == "T" && it.helixCommand == "goto_test" && it.intelliJAction == "GotoTest"
        }.shouldBeTrue()

        // Test menu (" t")
        val testMenu = HelixWhichKeyMenus.getMenu(" t")
        testMenu.shouldNotBeNull()
        testMenu.first shouldBe "TEST MENU"
        val testItems = testMenu.second
        testItems.any { it.key == "t" && it.intelliJAction == "RunClass" }.shouldBeTrue()
        testItems.any { it.key == "r" && it.intelliJAction == "RunClass" }.shouldBeTrue()
        testItems.any { it.key == "d" && it.intelliJAction == "DebugClass" }.shouldBeTrue()
        testItems.any { it.key == "l" && it.intelliJAction == "Rerun" }.shouldBeTrue()
        testItems.any { it.key == "f" && it.intelliJAction == "RerunFailedTests" }.shouldBeTrue()
        testItems.any { it.key == "c" && it.intelliJAction == "RunCoverage" }.shouldBeTrue()
        testItems.any { it.key == "a" && it.intelliJAction == "GotoTest" }.shouldBeTrue()
    }

    fun testBuiltinCommandsExecution() {
        myFixture.configureByText("TestFile.kt", "class TestFile { }")
        val editor = myFixture.editor

        executedActions.clear()
        HelixCommands.execute("test-run", editor)
        executedActions shouldBe listOf("RunClass")

        executedActions.clear()
        HelixCommands.execute("test-debug", editor)
        executedActions shouldBe listOf("DebugClass")

        executedActions.clear()
        HelixCommands.execute("test-rerun-failed", editor)
        executedActions shouldBe listOf("RerunFailedTests")

        executedActions.clear()
        HelixCommands.execute("test-coverage", editor)
        executedActions shouldBe listOf("RunCoverage")
    }

    fun testNoShadowedConflictsInDefaultKeymaps() {
        for (mode in HelixMode.entries) {
            val trie = HelixKeymapManager.getActiveTrie(mode)
            val conflicts = HelixKeymapConflictDetector.detectConflicts(mode, trie)
            conflicts.isEmpty().shouldBeTrue()
        }
    }
}
