package jp.titze.intellij.helix

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixSearchActions
import jp.titze.intellij.helix.editor.HelixEscapeHandler
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class HelixRegistersTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixRegisterManager.clear()
    }

    override fun tearDown() {
        HelixRegisterManager.clear()
        super.tearDown()
    }

    private fun getClipboardText(): String? {
        val transferable = CopyPasteManager.getInstance().contents ?: return null
        return if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            transferable.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }

    private fun setClipboardText(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    fun testBlackHoleRegisterDeleteDoesNotOverwriteClipboardOrRegisters() {
        myFixture.configureByText("test.txt", "delete me now")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        setClipboardText("important clipboard content")
        caret.setSelection(0, 10) // "delete me "

        // Type "_d
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('_', editor)
        HelixKeyHandler.handleKey('d', editor)

        editor.document.text shouldBe "now"
        getClipboardText() shouldBe "important clipboard content"
        HelixRegisterManager.defaultRegister.shouldBeNull()
    }

    fun testBlackHoleRegisterChangeDoesNotOverwriteClipboardAndEntersInsert() {
        myFixture.configureByText("test.txt", "change me now")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val state = HelixStateManager.getOrCreate(editor)

        setClipboardText("safe content")
        caret.setSelection(0, 10) // "change me "

        // Type "_c
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('_', editor)
        HelixKeyHandler.handleKey('c', editor)

        editor.document.text shouldBe "now"
        state.mode shouldBe HelixMode.INSERT
        getClipboardText() shouldBe "safe content"
        HelixRegisterManager.defaultRegister.shouldBeNull()
    }

    fun testBlackHoleRegisterPasteDoesNothing() {
        myFixture.configureByText("test.txt", "hello")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(5)

        // Type "_p
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('_', editor)
        HelixKeyHandler.handleKey('p', editor)

        editor.document.text shouldBe "hello"
    }

    fun testNamedRegistersYankAndPaste() {
        myFixture.configureByText("test.txt", "alpha beta gamma")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Yank "alpha" into register 'a'
        caret.setSelection(0, 5)
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('y', editor)

        // Yank "beta" into register 'b'
        caret.setSelection(6, 10)
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('b', editor)
        HelixKeyHandler.handleKey('y', editor)

        caret.removeSelection()
        caret.moveToOffset(editor.document.textLength)

        // Paste register 'a'
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('p', editor)

        // Paste register 'b'
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('b', editor)
        HelixKeyHandler.handleKey('p', editor)

        editor.document.text shouldBe "alpha beta gammaalphabeta"
    }

    fun testNamedRegisterUppercaseAppends() {
        myFixture.configureByText("test.txt", "first second")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Yank "first" into register 'a'
        caret.setSelection(0, 5)
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('y', editor)

        // Append "second" to register 'a' with 'A'
        caret.setSelection(6, 12)
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('A', editor)
        HelixKeyHandler.handleKey('y', editor)

        caret.removeSelection()
        caret.moveToOffset(editor.document.textLength)

        // Paste register 'a'
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('p', editor)

        editor.document.text shouldBe "first second\nfirst\nsecond"
    }

    fun testSystemClipboardRegistersPlusAndStar() {
        myFixture.configureByText("test.txt", "system text end")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.setSelection(0, 11) // "system text"
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('+', editor)
        HelixKeyHandler.handleKey('y', editor)

        getClipboardText() shouldBe "system text"

        setClipboardText("external injection")
        caret.removeSelection()
        caret.moveToOffset(editor.document.textLength)

        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('+', editor)
        HelixKeyHandler.handleKey('p', editor)

        editor.document.text shouldBe "system text endexternal injection"
    }

    fun testNumberedYankAndDeleteRegisters() {
        myFixture.configureByText("test.txt", "yanked_line\ndeleted_line\nlast_line")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Yank line 1
        caret.setSelection(0, 12) // "yanked_line\n"
        HelixKeyHandler.handleKey('y', editor)

        // Delete line 2
        caret.setSelection(12, 25) // "deleted_line\n"
        HelixKeyHandler.handleKey('d', editor)

        // Register '0' should be yanked_line
        HelixRegisterManager.get('0')?.text shouldBe "yanked_line\n"
        // Register '1' should be deleted_line
        HelixRegisterManager.get('1')?.text shouldBe "deleted_line\n"

        // Paste from register '0'
        caret.removeSelection()
        caret.moveToOffset(editor.document.textLength)
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('0', editor)
        HelixKeyHandler.handleKey('p', editor)

        editor.document.text.contains("yanked_line").shouldBeTrue()
    }

    fun testSearchAndBufferRegisters() {
        myFixture.configureByText("sample.txt", "base ")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(editor.document.textLength)

        HelixSearchActions.lastSearchPattern = "target_word"

        // Paste search register "/"
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('/', editor)
        HelixKeyHandler.handleKey('p', editor)

        editor.document.text shouldBe "base target_word"

        // Paste buffer register "%"
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('%', editor)
        HelixKeyHandler.handleKey('p', editor)

        editor.document.text shouldBe "base target_wordsample.txt"
    }

    fun testWhichKeyShowsRegistersOnQuote() {
        myFixture.configureByText("test.txt", "text")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('"', editor)
        state.pendingSequence shouldBe "\""
        val menu = jp.titze.intellij.helix.ui.HelixWhichKeyMenus.getMenu("\"")
        menu?.first shouldBe "REGISTERS"
        menu?.second?.any { it.key == "_" }?.shouldBeTrue()

        HelixKeyHandler.handleKey('_', editor)
        state.pendingSequence shouldBe ""
        state.selectedRegister shouldBe '_'
    }

    fun testEscapeClearsSelectedRegister() {
        myFixture.configureByText("test.txt", "text")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('a', editor)
        state.selectedRegister shouldBe 'a'

        HelixEscapeHandler.handleEscape(editor)
        state.selectedRegister.shouldBeNull()
    }
}
