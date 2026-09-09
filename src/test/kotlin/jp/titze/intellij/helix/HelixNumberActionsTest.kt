package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.editor.HelixEventDispatcher
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

class HelixNumberActionsTest : BasePlatformTestCase() {

    fun testDecimalIncrementDecrementNoSelection() {
        myFixture.configureByText("test.txt", "val x = 42")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(8) // on '4'

        HelixActions.increment(editor)
        editor.document.text shouldBe "val x = 43"
        caret.offset shouldBe 8
        caret.hasSelection().shouldBeFalse()

        HelixActions.decrement(editor)
        editor.document.text shouldBe "val x = 42"
        caret.offset shouldBe 8
    }

    fun testDecimalIncrementWithCount() {
        myFixture.configureByText("test.txt", "val x = 10")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(8)

        HelixActions.increment(editor, 5)
        editor.document.text shouldBe "val x = 15"

        HelixActions.decrement(editor, 20)
        editor.document.text shouldBe "val x = -5"
    }

    fun testNegativeNumbers() {
        myFixture.configureByText("test.txt", "val x = -5")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(8) // on '-'

        HelixActions.increment(editor, 1)
        editor.document.text shouldBe "val x = -4"

        HelixActions.increment(editor, 4)
        editor.document.text shouldBe "val x = 0"

        HelixActions.decrement(editor, 1)
        editor.document.text shouldBe "val x = -1"
    }

    fun testLeadingZeroes() {
        myFixture.configureByText("test.txt", "val id = 007")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(9)

        HelixActions.increment(editor, 1)
        editor.document.text shouldBe "val id = 008"

        HelixActions.increment(editor, 2)
        editor.document.text shouldBe "val id = 010"
    }

    fun testUnderscoreSeparators() {
        myFixture.configureByText("test.txt", "val big = 1_000")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(10)

        HelixActions.increment(editor, 1)
        editor.document.text shouldBe "val big = 1_001"

        HelixActions.decrement(editor, 2)
        editor.document.text shouldBe "val big = 999"
    }

    fun testHexNumbers() {
        myFixture.configureByText("test.txt", "val mask = 0x0f")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(11) // on '0' in '0x0f'

        HelixActions.decrement(editor, 1)
        editor.document.text shouldBe "val mask = 0x0e"

        HelixActions.increment(editor, 2)
        editor.document.text shouldBe "val mask = 0x10"

        HelixActions.decrement(editor, 1)
        editor.document.text shouldBe "val mask = 0xf"

        // Uppercase hex
        myFixture.configureByText("test2.txt", "val color = 0X1A")
        val editor2 = myFixture.editor
        editor2.caretModel.primaryCaret.moveToOffset(12)
        HelixActions.increment(editor2, 1)
        editor2.document.text shouldBe "val color = 0X1B"

        // Hex saturation at 0
        myFixture.configureByText("test3.txt", "0x0")
        val editor3 = myFixture.editor
        editor3.caretModel.primaryCaret.moveToOffset(0)
        HelixActions.decrement(editor3, 5)
        editor3.document.text shouldBe "0x0"
    }

    fun testBinaryAndOctalNumbers() {
        myFixture.configureByText("test.txt", "0b101")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.moveToOffset(0)

        HelixActions.increment(editor, 1)
        editor.document.text shouldBe "0b110"

        myFixture.configureByText("test2.txt", "0o77")
        val editor2 = myFixture.editor
        editor2.caretModel.primaryCaret.moveToOffset(0)

        HelixActions.increment(editor2, 1)
        editor2.document.text shouldBe "0o100"
    }

    fun testSelectionExplicit() {
        myFixture.configureByText("test.txt", "count: 42;")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.setSelection(7, 9) // "42"

        HelixActions.increment(editor, 1)
        editor.document.text shouldBe "count: 43;"
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "43"
    }

    fun testSelectionContainingNumber() {
        myFixture.configureByText("test.txt", "int x = 42;")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.setSelection(0, 11) // entire line

        HelixActions.increment(editor, 1)
        editor.document.text shouldBe "int x = 43;"
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "int x = 43;"
    }

    fun testSelectMode() {
        myFixture.configureByText("test.txt", "val x = 99")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(8)

        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.SELECT)

        HelixActions.increment(editor, 1)
        editor.document.text shouldBe "val x = 100"
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "100"
    }

    fun testScanForwardOnLine() {
        myFixture.configureByText("test.txt", "val x = 100")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0) // at 'v', before '100'

        HelixActions.increment(editor, 1)
        editor.document.text shouldBe "val x = 101"
        caret.offset shouldBe 8 // moved to start of number
    }

    fun testMultiCaretIncrement() {
        myFixture.configureByText("test.txt", "a = 10\nb = 20\nc = 30")
        val editor = myFixture.editor
        val model = editor.caretModel
        model.primaryCaret.moveToOffset(4) // on '10'
        model.addCaret(editor.offsetToVisualPosition(11)) // on '20'
        model.addCaret(editor.offsetToVisualPosition(18)) // on '30'

        HelixActions.increment(editor, 2)
        editor.document.text shouldBe "a = 12\nb = 22\nc = 32"
    }

    fun testEventDispatcherCtrlAAndCtrlX() {
        myFixture.configureByText("test.txt", "val x = 5")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.moveToOffset(8)

        val dispatcher = HelixEventDispatcher()
        val ctrlA = KeyEvent(
            editor.contentComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            InputEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_A,
            KeyEvent.CHAR_UNDEFINED,
        )

        val handledA = dispatcher.dispatch(ctrlA)
        handledA.shouldBeTrue()
        ctrlA.isConsumed.shouldBeTrue()
        editor.document.text shouldBe "val x = 6"

        val ctrlX = KeyEvent(
            editor.contentComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            InputEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_X,
            KeyEvent.CHAR_UNDEFINED,
        )

        val handledX = dispatcher.dispatch(ctrlX)
        handledX.shouldBeTrue()
        ctrlX.isConsumed.shouldBeTrue()
        editor.document.text shouldBe "val x = 5"
    }

    fun testEventDispatcherWithCount() {
        myFixture.configureByText("test.txt", "val x = 5")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.moveToOffset(8)

        val state = HelixStateManager.getOrCreate(editor)
        state.appendCountDigit('3')

        val dispatcher = HelixEventDispatcher()
        val ctrlA = KeyEvent(
            editor.contentComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            InputEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_A,
            KeyEvent.CHAR_UNDEFINED,
        )

        val handled = dispatcher.dispatch(ctrlA)
        handled.shouldBeTrue()
        editor.document.text shouldBe "val x = 8"
    }

    fun testCommandsPalette() {
        myFixture.configureByText("test.txt", "val x = 10")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.moveToOffset(8)

        HelixCommands.execute("increment", editor)
        editor.document.text shouldBe "val x = 11"

        HelixCommands.execute("decrement", editor)
        editor.document.text shouldBe "val x = 10"

        HelixCommands.execute("inc", editor)
        editor.document.text shouldBe "val x = 11"

        HelixCommands.execute("dec", editor)
        editor.document.text shouldBe "val x = 10"
    }
}
