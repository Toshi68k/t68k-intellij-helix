package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

class HelixCaseActionsTest : BasePlatformTestCase() {

    fun testSwitchCaseSingleCharNoSelection() {
        myFixture.configureByText("test.txt", "hello")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('~', editor)
        editor.document.text shouldBe "Hello"
        caret.offset shouldBe 0
        caret.hasSelection().shouldBeFalse()

        HelixKeyHandler.handleKey('~', editor)
        editor.document.text shouldBe "hello"
    }

    fun testSwitchCaseWithCountNoSelection() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('3', editor)
        HelixKeyHandler.handleKey('~', editor)
        editor.document.text shouldBe "HELlo world"
        caret.offset shouldBe 0
        caret.hasSelection().shouldBeFalse()
    }

    fun testSwitchCaseWithSelection() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)
        caret.setSelection(0, 5)

        HelixKeyHandler.handleKey('~', editor)
        editor.document.text shouldBe "HELLO world"
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "HELLO"

        HelixKeyHandler.handleKey('~', editor)
        editor.document.text shouldBe "hello world"
        caret.selectedText shouldBe "hello"
    }

    fun testSwitchToLowercaseKeyHandler() {
        myFixture.configureByText("test.txt", "HELLO WORLD")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // Single character toggle with `
        HelixKeyHandler.handleKey('`', editor)
        editor.document.text shouldBe "hELLO WORLD"

        // Selection toggle with `
        caret.setSelection(6, 11)
        HelixKeyHandler.handleKey('`', editor)
        editor.document.text shouldBe "hELLO world"
        caret.selectedText shouldBe "world"
    }

    fun testSwitchToUppercase() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixActions.toUpperCase(editor, count = 1)
        editor.document.text shouldBe "Hello world"

        caret.setSelection(6, 11)
        HelixActions.toUpperCase(editor)
        editor.document.text shouldBe "Hello WORLD"
        caret.selectedText shouldBe "WORLD"
    }

    fun testMultiCaretCaseTransform() {
        myFixture.configureByText("test.txt", "foo\nbar\nbaz")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.moveToOffset(0)
        editor.caretModel.addCaret(editor.offsetToVisualPosition(4))
        editor.caretModel.addCaret(editor.offsetToVisualPosition(8))

        // All carets have 1 char under cursor
        HelixActions.toggleCase(editor)
        editor.document.text shouldBe "Foo\nBar\nBaz"

        HelixActions.toLowerCase(editor)
        editor.document.text shouldBe "foo\nbar\nbaz"

        HelixActions.toUpperCase(editor)
        editor.document.text shouldBe "Foo\nBar\nBaz"
    }

    fun testCaseChangingInSelectMode() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.SELECT)

        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // In select mode, unselected character gets selected when transformed
        HelixActions.toggleCase(editor, count = 2)
        editor.document.text shouldBe "HEllo world"
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "HE"
    }

    fun testCommandsPaletteExecution() {
        myFixture.configureByText("test.txt", "MiXeD cAsE")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.setSelection(0, 10)

        HelixCommands.execute("switch-to-lowercase", editor)
        editor.document.text shouldBe "mixed case"

        HelixCommands.execute("switch_to_uppercase", editor)
        editor.document.text shouldBe "MIXED CASE"

        HelixCommands.execute("switch_case", editor)
        editor.document.text shouldBe "mixed case"
    }
}
