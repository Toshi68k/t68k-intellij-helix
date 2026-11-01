// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.editor.HelixEventDispatcher
import jp.titze.intellij.helix.editor.HelixTypedActionHandler
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.event.KeyEvent

class HelixMacroTest : BasePlatformTestCase() {

    private val executedActions = mutableListOf<String>()

    override fun setUp() {
        super.setUp()
        executedActions.clear()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }
    }

    override fun tearDown() {
        try {
            HelixActionDelegate.actionExecutor = null
            executedActions.clear()
        } finally {
            super.tearDown()
        }
    }

    fun testMacroActionIdsExistInPlatform() {
        val actionManager = ActionManager.getInstance()
        actionManager.getAction("StartStopMacroRecording").shouldNotBeNull()
        actionManager.getAction("PlaybackLastMacro").shouldNotBeNull()
    }

    fun testMacroDelegationInKeyHandler() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor

        // Q should trigger StartStopMacroRecording delegation
        HelixKeyHandler.handleKey('Q', editor).shouldBeTrue()
        executedActions shouldContainExactly listOf("StartStopMacroRecording")

        executedActions.clear()

        // q should trigger PlaybackLastMacro delegation
        HelixKeyHandler.handleKey('q', editor).shouldBeTrue()
        executedActions shouldContainExactly listOf("PlaybackLastMacro")
    }

    fun testMacroPlaybackWithCountPrefix() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor

        // 3q should execute PlaybackLastMacro 3 times
        HelixKeyHandler.handleKey('3', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('q', editor).shouldBeTrue()

        executedActions shouldContainExactly listOf(
            "PlaybackLastMacro",
            "PlaybackLastMacro",
            "PlaybackLastMacro",
        )

        // State count should be cleared after execution
        HelixStateManager.getOrCreate(editor).hasCount.shouldBe(false)
    }

    fun testMacroKeysInSelectMode() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.SELECT)

        HelixKeyHandler.handleKey('Q', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('q', editor).shouldBeTrue()

        executedActions shouldContainExactly listOf(
            "StartStopMacroRecording",
            "PlaybackLastMacro",
        )
    }

    fun testMacroCommandPaletteItems() {
        val recordCmd = HelixCommands.COMMANDS.firstOrNull { it.name == "record-macro" }
        recordCmd.shouldNotBeNull()
        recordCmd.aliases shouldBe listOf("macro-record")

        val replayCmd = HelixCommands.COMMANDS.firstOrNull { it.name == "replay-macro" }
        replayCmd.shouldNotBeNull()
        replayCmd.aliases shouldBe listOf("macro-play", "playback-macro")
    }

    fun testMacroCommandsExecution() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor

        HelixCommands.execute("record-macro", editor)
        HelixCommands.execute(":macro-record", editor)
        HelixCommands.execute("replay-macro", editor)
        HelixCommands.execute(":macro-play", editor)
        HelixCommands.execute(":playback-macro", editor)

        executedActions shouldContainExactly listOf(
            "StartStopMacroRecording",
            "StartStopMacroRecording",
            "PlaybackLastMacro",
            "PlaybackLastMacro",
            "PlaybackLastMacro",
        )
    }

    fun testMacroEventDispatcherInterceptionAtKeyPressed() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val dispatcher = HelixEventDispatcher()

        val shiftQEvent = KeyEvent(
            editor.contentComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.SHIFT_DOWN_MASK,
            KeyEvent.VK_Q,
            'Q',
        )

        val handled = dispatcher.dispatch(shiftQEvent)
        handled.shouldBeTrue()
        shiftQEvent.isConsumed.shouldBeTrue()
        executedActions shouldContainExactly listOf("StartStopMacroRecording")

        // Subsequent KEY_TYPED for 'Q' should be consumed and not trigger StartStopMacroRecording again
        val typedHandler = HelixTypedActionHandler(null)
        typedHandler.execute(editor, 'Q', DataContext.EMPTY_CONTEXT)
        executedActions shouldContainExactly listOf("StartStopMacroRecording")
    }

    fun testMacroEventDispatcherDoesNotInterceptWhenPendingSequence() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.setPendingSequence("r")

        val dispatcher = HelixEventDispatcher()
        val shiftQEvent = KeyEvent(
            editor.contentComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.SHIFT_DOWN_MASK,
            KeyEvent.VK_Q,
            'Q',
        )

        val handled = dispatcher.dispatch(shiftQEvent)
        handled.shouldBeFalse()
        shiftQEvent.isConsumed.shouldBeFalse()
        executedActions.isEmpty().shouldBeTrue()
    }
}
