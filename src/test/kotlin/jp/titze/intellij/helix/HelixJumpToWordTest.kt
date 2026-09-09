package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.editor.HelixEscapeHandler
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixJumpToWord
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

class HelixJumpToWordTest : BasePlatformTestCase() {

    fun testJumpToWordSingleLetterMode() {
        val text = "apple banana cherry date elderberry fig grape\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)

        // Trigger 'gw'
        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        // 'cherry' is the 3rd word -> label 'k' (alphabet: j, f, k, d, l, s, a...)
        HelixKeyHandler.handleKey('k', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeFalse()

        val cherryOffset = text.indexOf("cherry")
        caret.selectionStart shouldBe cherryOffset
        caret.selectionEnd shouldBe cherryOffset + "cherry".length
        caret.selectedText shouldBe "cherry"
    }

    fun testJumpToWordTwoLetterMode() {
        // Create 25 words to exceed alphabet length (22)
        val words = (1..25).map { "word$it" }
        val text = words.joinToString(" ")
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)

        // Trigger 'gw'
        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        // 3rd word ("word3", index 2) has label "jk" (j=0, f=1, k=2)
        HelixKeyHandler.handleKey('j', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue() // 1st character typed, waiting for 2nd

        HelixKeyHandler.handleKey('k', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeFalse()

        val word3Offset = text.indexOf("word3")
        caret.selectionStart shouldBe word3Offset
        caret.selectionEnd shouldBe word3Offset + "word3".length
        caret.selectedText shouldBe "word3"
    }

    fun testJumpToWordInSelectMode() {
        val text = "one two three four five\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val state = HelixStateManager.getOrCreate(editor)

        caret.moveToOffset(0)
        state.setMode(HelixMode.SELECT)

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        // 2nd word ("two") -> label 'f'
        HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeFalse()

        val twoOffset = text.indexOf("two")
        caret.selectionStart shouldBe 0
        caret.selectionEnd shouldBe twoOffset + "two".length
        caret.selectedText shouldBe "one two"
    }

    fun testJumpToWordInSelectModeBackward() {
        val text = "one two three four five\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val state = HelixStateManager.getOrCreate(editor)

        val threeEnd = text.indexOf("three") + "three".length
        caret.moveToOffset(threeEnd)
        state.setMode(HelixMode.SELECT)

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        // 1st word ("one") -> label 'j'
        HelixKeyHandler.handleKey('j', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeFalse()

        caret.selectionStart shouldBe 0
        caret.selectionEnd shouldBe threeEnd
        caret.selectedText shouldBe "one two three"
    }

    fun testJumpToWordRecordsJumpList() {
        val text = "first second third fourth\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val project = myFixture.project

        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()

        // Jump to 'third' -> label 'k'
        HelixKeyHandler.handleKey('k', editor).shouldBeTrue()
        val thirdOffset = text.indexOf("third")
        caret.selectionStart shouldBe thirdOffset
        caret.selectedText shouldBe "third"

        // Jump back via Ctrl-O
        HelixJumpListService.getInstance(project).jumpBackward(editor, 1)
        caret.offset shouldBe 0
    }

    fun testJumpToWordCancelledByEscape() {
        val text = "alpha beta gamma\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        HelixEscapeHandler.handleEscape(editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeFalse()
        caret.offset shouldBe 0
    }

    fun testJumpToWordBackspaceRestoresPrefix() {
        val words = (1..25).map { "word$it" }
        val text = words.joinToString(" ")
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        // Type 'j'
        HelixKeyHandler.handleKey('j', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        // Backspace should clear pending 'j' but remain in jump mode
        HelixJumpToWord.handleBackspace(editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        // Backspace again cancels jump mode
        HelixJumpToWord.handleBackspace(editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeFalse()
    }

    fun testJumpToWordInvalidKeyCancels() {
        val text = "alpha beta gamma\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('w', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeTrue()

        // '9' is not a valid jump label
        HelixKeyHandler.handleKey('9', editor).shouldBeTrue()
        HelixJumpToWord.isActive(editor).shouldBeFalse()
        caret.offset shouldBe 0
    }
}
