package jp.titze.intellij.helix

import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixSearchActions
import jp.titze.intellij.helix.editor.HelixEscapeHandler
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixVisualFeedback
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class HelixRegistersTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixRegisterManager.clear()
        HelixVisualFeedback.clearAll()
    }

    override fun tearDown() {
        HelixRegisterManager.clear()
        HelixVisualFeedback.clearAll()
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

    fun testMultiCaretPieceWisePasteCharacterwise() {
        myFixture.configureByText("test.txt", "apple\nbanana\ncherry")
        val editor = myFixture.editor
        val sourceStates = listOf(
            CaretState(
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(5),
            ),
            CaretState(
                editor.offsetToLogicalPosition(6),
                editor.offsetToLogicalPosition(6),
                editor.offsetToLogicalPosition(12),
            ),
            CaretState(
                editor.offsetToLogicalPosition(13),
                editor.offsetToLogicalPosition(13),
                editor.offsetToLogicalPosition(19),
            ),
        )
        editor.caretModel.setCaretsAndSelections(sourceStates)
        editor.caretModel.caretCount shouldBe 3

        // Yank selections into register 'a'
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('y', editor)

        // Set up target buffer with 3 lines
        myFixture.configureByText("test.txt", "1: \n2: \n3: ")
        val targetEditor = myFixture.editor
        val targetStates = listOf(
            CaretState(targetEditor.offsetToLogicalPosition(3), null, null),
            CaretState(targetEditor.offsetToLogicalPosition(7), null, null),
            CaretState(targetEditor.offsetToLogicalPosition(11), null, null),
        )
        targetEditor.caretModel.setCaretsAndSelections(targetStates)
        targetEditor.caretModel.caretCount shouldBe 3

        // Paste register 'a' after caret
        HelixKeyHandler.handleKey('"', targetEditor)
        HelixKeyHandler.handleKey('a', targetEditor)
        HelixKeyHandler.handleKey('p', targetEditor)

        targetEditor.document.text shouldBe "1: apple\n2: banana\n3: cherry"
    }

    fun testMultiCaretPieceWiseReplace() {
        myFixture.configureByText("test.txt", "foo bar")
        val editor = myFixture.editor
        val sourceStates = listOf(
            CaretState(
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(3),
            ),
            CaretState(
                editor.offsetToLogicalPosition(4),
                editor.offsetToLogicalPosition(4),
                editor.offsetToLogicalPosition(7),
            ),
        )
        editor.caretModel.setCaretsAndSelections(sourceStates)
        editor.caretModel.caretCount shouldBe 2

        // Yank into default register
        HelixKeyHandler.handleKey('y', editor)

        // Select target tokens "AAA" and "BBB"
        myFixture.configureByText("test.txt", "AAA BBB")
        val targetEditor = myFixture.editor
        val targetStates = listOf(
            CaretState(
                targetEditor.offsetToLogicalPosition(0),
                targetEditor.offsetToLogicalPosition(0),
                targetEditor.offsetToLogicalPosition(3),
            ),
            CaretState(
                targetEditor.offsetToLogicalPosition(4),
                targetEditor.offsetToLogicalPosition(4),
                targetEditor.offsetToLogicalPosition(7),
            ),
        )
        targetEditor.caretModel.setCaretsAndSelections(targetStates)
        targetEditor.caretModel.caretCount shouldBe 2

        // Replace with register text (R)
        HelixKeyHandler.handleKey('R', targetEditor)

        targetEditor.document.text shouldBe "foo bar"
    }

    fun testPieceWiseFallbackWhenCaretCountDiffers() {
        myFixture.configureByText("test.txt", "first\nsecond")
        val editor = myFixture.editor
        val states = listOf(
            CaretState(
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(5),
            ),
            CaretState(
                editor.offsetToLogicalPosition(6),
                editor.offsetToLogicalPosition(6),
                editor.offsetToLogicalPosition(12),
            ),
        )
        editor.caretModel.setCaretsAndSelections(states)
        editor.caretModel.caretCount shouldBe 2

        // Yank 2 pieces ("first", "second")
        HelixKeyHandler.handleKey('y', editor)

        // Target has single caret -> pastes joined text
        myFixture.configureByText("test.txt", "result: ")
        val targetEditor = myFixture.editor
        targetEditor.caretModel.primaryCaret.moveToOffset(8)

        HelixKeyHandler.handleKey('p', targetEditor)
        targetEditor.document.text shouldBe "result: \nfirst\nsecond"
    }

    fun testSelectionIndexRegisterHashNormalMode() {
        myFixture.configureByText("test.txt", "item \nitem \nitem ")
        val editor = myFixture.editor
        val states = listOf(
            CaretState(editor.offsetToLogicalPosition(5), null, null),
            CaretState(editor.offsetToLogicalPosition(11), null, null),
            CaretState(editor.offsetToLogicalPosition(17), null, null),
        )
        editor.caretModel.setCaretsAndSelections(states)
        editor.caretModel.caretCount shouldBe 3

        // Paste register '#' after caret ("#p)
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('#', editor)
        HelixKeyHandler.handleKey('p', editor)

        editor.document.text shouldBe "item 0\nitem 1\nitem 2"
    }

    fun testSelectionIndexRegisterHashInsertMode() {
        myFixture.configureByText("test.txt", "id: \nid: \nid: ")
        val editor = myFixture.editor
        val states = listOf(
            CaretState(editor.offsetToLogicalPosition(4), null, null),
            CaretState(editor.offsetToLogicalPosition(9), null, null),
            CaretState(editor.offsetToLogicalPosition(14), null, null),
        )
        editor.caretModel.setCaretsAndSelections(states)
        editor.caretModel.caretCount shouldBe 3

        // Enter insert mode
        HelixStateManager.getOrCreate(editor).setMode(HelixMode.INSERT)

        // Trigger C-r, then '#'
        HelixKeyHandler.handleKey('\u0012', editor) // Ctrl+r
        HelixKeyHandler.handleKey('#', editor)

        editor.document.text shouldBe "id: 0\nid: 1\nid: 2"
    }

    fun testSelectionIndexRegisterHashIsReadOnly() {
        myFixture.configureByText("test.txt", "custom")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.setSelection(0, 6)

        // Attempt to yank into '#'
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('#', editor)
        HelixKeyHandler.handleKey('y', editor)

        editor.caretModel.primaryCaret.removeSelection()
        editor.caretModel.primaryCaret.moveToOffset(6)

        // Paste register '#'
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('#', editor)
        HelixKeyHandler.handleKey('p', editor)

        // Should still produce index "0", not "custom"
        editor.document.text shouldBe "custom0"
    }

    fun testWhichKeyShowsHashRegister() {
        val menu = jp.titze.intellij.helix.ui.HelixWhichKeyMenus.getMenu("\"")
        menu?.first shouldBe "REGISTERS"
        menu?.second?.any { it.key == "#" && it.label == "Selection index" }?.shouldBeTrue()
    }

    fun testGetAllRegistersIncludesStandardRegisters() {
        myFixture.configureByText("sample.txt", "hello world")
        val editor = myFixture.editor

        // Populate a named register and a delete register
        HelixRegisterManager.set('a', jp.titze.intellij.helix.register.HelixRegisterEntry("named_content"))
        HelixRegisterManager.recordDelete("deleted_line\n", isLinewise = true, pieces = emptyList())

        val registers = HelixRegisterManager.getAllRegisters(editor)
        val symbols = registers.map { it.register }

        symbols.contains('"').shouldBeTrue()
        symbols.contains('0').shouldBeTrue()
        symbols.contains('1').shouldBeTrue()
        symbols.contains('a').shouldBeTrue()
        symbols.contains('+').shouldBeTrue()
        symbols.contains('%').shouldBeTrue()
        symbols.contains('#').shouldBeTrue()
        symbols.contains('_').shouldBeTrue()

        val aItem = registers.first { it.register == 'a' }
        aItem.description shouldBe "Named 'a'"
        aItem.entry?.text shouldBe "named_content"
        aItem.previewText shouldBe "named_content"

        val bufferItem = registers.first { it.register == '%' }
        bufferItem.entry?.text shouldBe "sample.txt"
    }

    fun testRegisterItemFilteringAndTagText() {
        val item1 = jp.titze.intellij.helix.register.HelixRegisterItem(
            register = 'x',
            description = "Named 'x'",
            entry = jp.titze.intellij.helix.register.HelixRegisterEntry(
                text = "line 1\nline 2",
                isLinewise = true,
            ),
        )
        item1.matches("named").shouldBeTrue()
        item1.matches("x").shouldBeTrue()
        item1.matches("line 2").shouldBeTrue()
        item1.matches("nonexistent").shouldBeFalse()
        item1.previewText shouldBe "line 1⏎ line 2"
        item1.tagText shouldBe "[linewise]"

        val multiItem = jp.titze.intellij.helix.register.HelixRegisterItem(
            register = 'm',
            description = "Multi Piece",
            entry = jp.titze.intellij.helix.register.HelixRegisterEntry(
                text = "p1\np2\np3",
                pieces = listOf("p1", "p2", "p3"),
            ),
        )
        multiItem.tagText shouldBe "[3 pieces]"
    }

    fun testLastInsertRegisterDot() {
        myFixture.configureByText("insert.txt", "")
        val editor = myFixture.editor

        jp.titze.intellij.helix.editor.HelixInsertTracker.reset()
        jp.titze.intellij.helix.editor.HelixInsertTracker.startInsert()
        jp.titze.intellij.helix.editor.HelixInsertTracker.recordText("inserted_value")
        jp.titze.intellij.helix.editor.HelixInsertTracker.finishInsert()

        val dotEntry = HelixRegisterManager.get('.')
        dotEntry?.text shouldBe "inserted_value"

        val allRegs = HelixRegisterManager.getAllRegisters(editor)
        val dotItem = allRegs.firstOrNull { it.register == '.' }
        dotItem?.entry?.text shouldBe "inserted_value"
        dotItem?.description shouldBe "Last Insert"
    }

    fun testRegistersCommandExecution() {
        myFixture.configureByText("cmd.txt", "abc")
        val editor = myFixture.editor

        val commands = jp.titze.intellij.helix.command.HelixCommands.COMMANDS
        commands.any { it.name == "registers" && it.aliases.contains("reg") }.shouldBeTrue()

        // Execute :reg and :registers in headless test (safely returns without throwing)
        jp.titze.intellij.helix.command.HelixCommands.execute("reg", editor)
        jp.titze.intellij.helix.command.HelixCommands.execute("registers", editor)
        jp.titze.intellij.helix.action.HelixActions.showRegistersPicker(editor)
    }

    fun testRegistersPickerPasteAction() {
        myFixture.configureByText("test.txt", "target: ")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(8)

        HelixRegisterManager.set('v', jp.titze.intellij.helix.register.HelixRegisterEntry("value"))
        val item = HelixRegisterManager.getAllRegisters(editor).first { it.register == 'v' }

        jp.titze.intellij.helix.action.HelixRegisterActions.paste(editor, after = true, register = item.register)
        editor.document.text shouldBe "target: value"
    }

    fun testYankVisualFeedbackSingleSelection() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.setSelection(0, 5)

        HelixKeyHandler.handleKey('y', editor)

        HelixVisualFeedback.hasActiveFlash(editor).shouldBeTrue()
        val highlighters = HelixVisualFeedback.getActiveHighlighters(editor)
        highlighters.size shouldBe 1
        highlighters[0].startOffset shouldBe 0
        highlighters[0].endOffset shouldBe 5

        HelixVisualFeedback.clearFlash(editor)
        HelixVisualFeedback.hasActiveFlash(editor).shouldBeFalse()
    }

    fun testYankVisualFeedbackMultiCaret() {
        myFixture.configureByText("test.txt", "line1\nline2\nline3")
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        caretModel.primaryCaret.moveToOffset(0)
        caretModel.primaryCaret.setSelection(0, 5)
        val caret2 = caretModel.addCaret(editor.offsetToLogicalPosition(6), false)
        caret2?.setSelection(6, 11)

        caretModel.caretCount shouldBe 2

        HelixKeyHandler.handleKey('y', editor)

        HelixVisualFeedback.hasActiveFlash(editor).shouldBeTrue()
        val highlighters = HelixVisualFeedback.getActiveHighlighters(editor)
        highlighters.size shouldBe 2

        HelixVisualFeedback.clearFlash(editor)
        HelixVisualFeedback.hasActiveFlash(editor).shouldBeFalse()
    }

    fun testSpaceYankToClipboardMultipleSelections() {
        myFixture.configureByText("test.txt", "alpha\nbeta\ngamma")
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        caretModel.primaryCaret.moveToOffset(0)
        caretModel.primaryCaret.setSelection(0, 5) // "alpha"
        val caret2 = caretModel.addCaret(editor.offsetToLogicalPosition(6), false)
        caret2?.setSelection(6, 10) // "beta"

        HelixKeyHandler.handleKey(' ', editor)
        HelixKeyHandler.handleKey('y', editor)

        getClipboardText() shouldBe "alpha\nbeta"
    }

    fun testSpaceYankMainSelectionToClipboardOnlyYanksPrimarySelection() {
        myFixture.configureByText("test.txt", "alpha\nbeta\ngamma")
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        caretModel.primaryCaret.moveToOffset(0)
        caretModel.primaryCaret.setSelection(0, 5) // "alpha"
        val caret2 = caretModel.addCaret(editor.offsetToLogicalPosition(6), false)
        caret2?.setSelection(6, 10) // "beta"

        HelixKeyHandler.handleKey(' ', editor)
        HelixKeyHandler.handleKey('Y', editor)

        getClipboardText() shouldBe "alpha"
    }

    fun testClipboardYankCommands() {
        myFixture.configureByText("test.txt", "first\nsecond")
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        caretModel.primaryCaret.moveToOffset(0)
        caretModel.primaryCaret.setSelection(0, 5) // "first"
        val caret2 = caretModel.addCaret(editor.offsetToLogicalPosition(6), false)
        caret2?.setSelection(6, 12) // "second"

        jp.titze.intellij.helix.command.HelixCommands.execute("yank-to-clipboard", editor)
        getClipboardText() shouldBe "first\nsecond"

        jp.titze.intellij.helix.command.HelixCommands.execute("yank-main-selection-to-clipboard", editor)
        getClipboardText() shouldBe "first"
    }

    fun testClearRegisterSpecificAndAll() {
        HelixRegisterManager.set('a', jp.titze.intellij.helix.register.HelixRegisterEntry("regA"))
        HelixRegisterManager.set('b', jp.titze.intellij.helix.register.HelixRegisterEntry("regB"))
        HelixRegisterManager.recordYank("defaultVal", isLinewise = false, pieces = listOf("defaultVal"))

        HelixRegisterManager.get('a')?.text shouldBe "regA"
        HelixRegisterManager.get('b')?.text shouldBe "regB"
        HelixRegisterManager.defaultRegister?.text shouldBe "defaultVal"

        // Clear specific register 'a'
        HelixRegisterManager.clear('a')
        HelixRegisterManager.get('a').shouldBeNull()
        HelixRegisterManager.get('b')?.text shouldBe "regB"
        HelixRegisterManager.defaultRegister?.text shouldBe "defaultVal"

        // Clear default register '"'
        HelixRegisterManager.clear('"')
        HelixRegisterManager.defaultRegister.shouldBeNull()
        HelixRegisterManager.get('b')?.text shouldBe "regB"

        // Clear all
        HelixRegisterManager.clear()
        HelixRegisterManager.get('b').shouldBeNull()
    }

    fun testClearRegisterCommandExecution() {
        myFixture.configureByText("test.txt", "content")
        val editor = myFixture.editor

        HelixRegisterManager.set('x', jp.titze.intellij.helix.register.HelixRegisterEntry("xVal"))
        HelixRegisterManager.set('y', jp.titze.intellij.helix.register.HelixRegisterEntry("yVal"))

        // Command with argument: :clear-register x
        jp.titze.intellij.helix.command.HelixCommands.execute("clear-register x", editor)
        HelixRegisterManager.get('x').shouldBeNull()
        HelixRegisterManager.get('y')?.text shouldBe "yVal"

        // Command without argument: :clear-register clears all
        jp.titze.intellij.helix.command.HelixCommands.execute("clear-register", editor)
        HelixRegisterManager.get('y').shouldBeNull()
    }
}
