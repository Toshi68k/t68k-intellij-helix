package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.editor.HelixEscapeHandler
import jp.titze.intellij.helix.editor.HelixEventDispatcher
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.keymap.HelixWindowKeymap
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixWhichKeyMenus
import java.awt.event.KeyEvent

class HelixWindowKeymapTest : BasePlatformTestCase() {

    fun testWindowMenuData() {
        val menu = HelixWhichKeyMenus.getMenu("C-w")
        menu.shouldNotBeNull()
        menu.first shouldBe "WINDOW MENU"

        val keys = menu.second.map { it.key }
        keys shouldBe listOf("v", "s", "h", "j", "k", "l", "w", "W", "H", "J", "K", "L", "q", "c", "o")

        // Also verify aliases
        HelixWhichKeyMenus.getMenu("Ctrl+w").shouldNotBeNull()
        HelixWhichKeyMenus.getMenu("\u0017").shouldNotBeNull()
    }

    fun testWindowKeymapHandlerValidAndInvalidKeys() {
        myFixture.configureByText("test.txt", "hello window split")
        val editor = myFixture.editor

        val validKeys = listOf('v', 's', 'h', 'j', 'k', 'l', 'w', 'W', 'H', 'J', 'K', 'L', 'q', 'c', 'o')
        for (k in validKeys) {
            HelixWindowKeymap.handle(k, editor).shouldBeTrue()
        }

        HelixWindowKeymap.handle('x', editor).shouldBeFalse()
        HelixWindowKeymap.handle('z', editor).shouldBeFalse()
    }

    fun testWindowKeymapActionRouting() {
        myFixture.configureByText("test.txt", "split test")
        val editor = myFixture.editor
        val executed = mutableListOf<String>()
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
            executed.add(actionId)
            true
        }
        try {
            HelixWindowKeymap.handle('w', editor).shouldBeTrue()
            executed.last() shouldBe "NextSplitter"

            HelixWindowKeymap.handle('W', editor).shouldBeTrue()
            executed.last() shouldBe "PrevSplitter"

            HelixWindowKeymap.handle('h', editor).shouldBeTrue()
            executed.last() shouldBe "PrevSplitter"

            HelixWindowKeymap.handle('j', editor).shouldBeTrue()
            executed.last() shouldBe "NextSplitter"

            HelixWindowKeymap.handle('k', editor).shouldBeTrue()
            executed.last() shouldBe "PrevSplitter"

            HelixWindowKeymap.handle('l', editor).shouldBeTrue()
            executed.last() shouldBe "NextSplitter"

            for (ch in listOf('H', 'J', 'K', 'L')) {
                HelixWindowKeymap.handle(ch, editor).shouldBeTrue()
                executed.last() shouldBe "ChangeSplitOrientation"
            }

            HelixWindowKeymap.handle('v', editor).shouldBeTrue()
            executed.last() shouldBe "SplitVertically"

            HelixWindowKeymap.handle('s', editor).shouldBeTrue()
            executed.last() shouldBe "SplitHorizontally"

            HelixWindowKeymap.handle('o', editor).shouldBeTrue()
            executed.last() shouldBe "UnsplitAll"
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = null
        }
    }

    fun testCtrlWChordInitiationAndKeyExecution() {
        myFixture.configureByText("test.txt", "hello chord")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.startWindowChord(editor)
        state.pendingSequence shouldBe "C-w"

        HelixKeyHandler.handleKey('v', editor).shouldBeTrue()
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testCtrlWCharacterTypedDirectly() {
        myFixture.configureByText("test.txt", "hello typed")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('\u0017', editor).shouldBeTrue()
        state.pendingSequence shouldBe "C-w"

        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testCtrlWChordEscapeCancellation() {
        myFixture.configureByText("test.txt", "hello escape")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.startWindowChord(editor)
        state.pendingSequence shouldBe "C-w"

        HelixEscapeHandler.handleEscape(editor).shouldBeTrue()
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testWindowCommandPaletteItems() {
        myFixture.configureByText("test.txt", "line 1\nline 2")
        val editor = myFixture.editor

        val unsplitCmd = HelixCommandPopup.COMMANDS.firstOrNull { it.name == "unsplit" }
        unsplitCmd.shouldNotBeNull()
        unsplitCmd.matches("only").shouldBeTrue()

        val closeSplitCmd = HelixCommandPopup.COMMANDS.firstOrNull { it.name == "close-split" }
        closeSplitCmd.shouldNotBeNull()
        closeSplitCmd.matches("close").shouldBeTrue()
        closeSplitCmd.matches("clo").shouldBeTrue()

        val swapSplitCmd = HelixCommandPopup.COMMANDS.firstOrNull { it.name == "swap-split" }
        swapSplitCmd.shouldNotBeNull()
        swapSplitCmd.matches("change-split-orientation").shouldBeTrue()
        swapSplitCmd.matches("swap").shouldBeTrue()

        HelixCommandPopup.executeCommand("unsplit", editor)
        HelixCommandPopup.executeCommand("only", editor)
        HelixCommandPopup.executeCommand("close-split", editor)
        HelixCommandPopup.executeCommand("close", editor)
        HelixCommandPopup.executeCommand("swap-split", editor)
        HelixCommandPopup.executeCommand("change-split-orientation", editor)
    }

    fun testEventDispatcherWindowChord() {
        myFixture.configureByText("test.txt", "hello dispatcher")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        val dispatcher = HelixEventDispatcher()

        // 1. Dispatch Ctrl+w
        val ctrlWEvent = KeyEvent(
            editor.contentComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_W,
            'w',
        )
        dispatcher.dispatch(ctrlWEvent).shouldBeTrue()
        state.pendingSequence shouldBe "C-w"

        // 2. Dispatch Ctrl+v while pending sequence is C-w
        val ctrlVEvent = KeyEvent(
            editor.contentComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_V,
            'v',
        )
        dispatcher.dispatch(ctrlVEvent).shouldBeTrue()
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testEventDispatcherShiftWindowChord() {
        myFixture.configureByText("test.txt", "hello dispatcher shift")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        val dispatcher = HelixEventDispatcher()

        val executed = mutableListOf<String>()
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
            executed.add(actionId)
            true
        }
        try {
            // Dispatch Ctrl+w
            val ctrlWEvent = KeyEvent(
                editor.contentComponent,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                KeyEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_W,
                'w',
            )
            dispatcher.dispatch(ctrlWEvent).shouldBeTrue()
            state.pendingSequence shouldBe "C-w"

            // Dispatch Ctrl+Shift+W -> PrevSplitter
            val ctrlShiftWEvent = KeyEvent(
                editor.contentComponent,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_W,
                'W',
            )
            dispatcher.dispatch(ctrlShiftWEvent).shouldBeTrue()
            state.pendingSequence.isEmpty().shouldBeTrue()
            executed.last() shouldBe "PrevSplitter"

            // Dispatch Ctrl+w again
            dispatcher.dispatch(ctrlWEvent).shouldBeTrue()
            state.pendingSequence shouldBe "C-w"

            // Dispatch Ctrl+Shift+H -> ChangeSplitOrientation
            val ctrlShiftHEvent = KeyEvent(
                editor.contentComponent,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_H,
                'H',
            )
            dispatcher.dispatch(ctrlShiftHEvent).shouldBeTrue()
            state.pendingSequence.isEmpty().shouldBeTrue()
            executed.last() shouldBe "ChangeSplitOrientation"
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = null
        }
    }
}
