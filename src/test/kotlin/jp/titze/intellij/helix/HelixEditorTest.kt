package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

class HelixEditorTest : BasePlatformTestCase() {

    fun testInitialModeIsNormal() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        assertEquals(HelixMode.NORMAL, state.mode)
        assertTrue(editor.settings.isBlockCursor)
    }

    fun testModeTransitions() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixActions.enterInsert(editor)
        assertEquals(HelixMode.INSERT, state.mode)
        assertFalse(editor.settings.isBlockCursor)

        HelixActions.enterNormalMode(editor)
        assertEquals(HelixMode.NORMAL, state.mode)
        assertTrue(editor.settings.isBlockCursor)

        HelixActions.toggleSelectMode(editor)
        assertEquals(HelixMode.SELECT, state.mode)

        HelixActions.toggleSelectMode(editor)
        assertEquals(HelixMode.NORMAL, state.mode)
    }

    fun testWordMotions() {
        myFixture.configureByText("test.txt", "hello world next")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // In Normal mode, motion 'w' moves to start of "world" without marking
        HelixMotions.moveNextWordStart(editor)
        assertEquals(6, caret.offset)
        assertFalse(caret.hasSelection())

        // In Select mode ('v'), motion 'w' extends selection (marks)
        HelixActions.toggleSelectMode(editor)
        assertEquals(HelixMode.SELECT, HelixStateManager.getOrCreate(editor).mode)
        HelixMotions.moveNextWordStart(editor)
        assertEquals(12, caret.offset)
        assertTrue(caret.hasSelection())
        assertEquals("world ", caret.selectedText)

        // Motion 'b' in Select mode moves back and updates selection
        HelixMotions.movePrevWordStart(editor)
        assertEquals(6, caret.offset)
    }

    fun testLineSelectionMotion() {
        myFixture.configureByText("test.txt", "first line\nsecond line\nthird line\n")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(2)

        // First 'x' selects current line including newline
        HelixMotions.selectLine(editor)
        assertEquals("first line\n", caret.selectedText)

        // Second 'x' extends selection down to next line
        HelixMotions.selectLine(editor)
        assertEquals("first line\nsecond line\n", caret.selectedText)
    }

    fun testSelectAllMotion() {
        val text = "first line\nsecond line\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        HelixMotions.selectAll(editor)
        assertEquals(text, editor.selectionModel.selectedText)
    }

    fun testDeleteAndChangeActions() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // In Select mode, select "hello " and 'd' deletes selection
        HelixActions.toggleSelectMode(editor)
        HelixMotions.moveNextWordStart(editor)
        assertEquals("hello ", caret.selectedText)

        HelixActions.deleteSelection(editor, enterInsert = false)
        assertEquals("world", editor.document.text)
        assertEquals(HelixMode.SELECT, HelixStateManager.getOrCreate(editor).mode)

        // Select "world" and 'c' (change) deletes and enters Insert mode
        HelixMotions.moveWordEnd(editor)
        HelixActions.deleteSelection(editor, enterInsert = true)
        assertEquals("", editor.document.text)
        assertEquals(HelixMode.INSERT, HelixStateManager.getOrCreate(editor).mode)
    }


    fun testMultiCaretLineSelection() {
        val text = "line 1\nline 2\nline 3\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        val primary = caretModel.primaryCaret
        primary.moveToOffset(0)
        val secondary = caretModel.addCaret(editor.offsetToVisualPosition(text.indexOf("line 2")))
        assertNotNull(secondary)

        HelixMotions.selectLine(editor)

        assertEquals("line 1\n", primary.selectedText)
        assertEquals("line 2\n", secondary?.selectedText)
    }

    fun testKeyHandlerDispatch() {
        myFixture.configureByText("test.txt", "foo bar baz")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Press 'w' in Normal mode
        val handledW = HelixKeyHandler.handleKey('w', editor)
        assertTrue(handledW)
        assertEquals(4, editor.caretModel.primaryCaret.offset)

        // Press 'g' sets pending sequence
        val handledG = HelixKeyHandler.handleKey('g', editor)
        assertTrue(handledG)
        assertEquals("g", state.pendingSequence)

        // Press 'g' completes 'gg'
        val handledG2 = HelixKeyHandler.handleKey('g', editor)
        assertTrue(handledG2)
        assertEquals("", state.pendingSequence)
        assertEquals(0, editor.caretModel.primaryCaret.offset)
    }

    fun testSurroundAddWithSelection() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Select "world" (offsets 6 to 11)
        caret.setSelection(6, 11)

        val state = HelixStateManager.getOrCreate(editor)
        assertTrue(HelixKeyHandler.handleKey('m', editor))
        assertEquals("m", state.pendingSequence)

        assertTrue(HelixKeyHandler.handleKey('s', editor))
        assertEquals("ms", state.pendingSequence)

        assertTrue(HelixKeyHandler.handleKey('(', editor))
        assertEquals("", state.pendingSequence)

        assertEquals("hello (world)", editor.document.text)
        assertEquals("(world)", caret.selectedText)
    }

    fun testSurroundAddQuotes() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.setSelection(6, 11)

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('s', editor)
        HelixKeyHandler.handleKey('"', editor)

        assertEquals("hello \"world\"", editor.document.text)
        assertEquals("\"world\"", caret.selectedText)
    }

    fun testSurroundDeleteParentheses() {
        myFixture.configureByText("test.txt", "hello (world) test")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(9) // inside "world"

        val state = HelixStateManager.getOrCreate(editor)
        HelixKeyHandler.handleKey('m', editor)
        assertEquals("m", state.pendingSequence)

        HelixKeyHandler.handleKey('d', editor)
        assertEquals("md", state.pendingSequence)

        HelixKeyHandler.handleKey('(', editor)
        assertEquals("", state.pendingSequence)

        assertEquals("hello world test", editor.document.text)
    }

    fun testSurroundDeleteQuotes() {
        myFixture.configureByText("test.txt", "val s = \"hello world\"")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(12) // inside "hello world"

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('d', editor)
        HelixKeyHandler.handleKey('"', editor)

        assertEquals("val s = hello world", editor.document.text)
    }

    fun testSurroundDeleteWithAlias() {
        myFixture.configureByText("test.txt", "hello (world) test")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(9)

        // 'b' is alias for ()
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('d', editor)
        HelixKeyHandler.handleKey('b', editor)

        assertEquals("hello world test", editor.document.text)
    }

    fun testSurroundReplaceParenthesesToBrackets() {
        myFixture.configureByText("test.txt", "hello (world) test")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(9)

        val state = HelixStateManager.getOrCreate(editor)
        HelixKeyHandler.handleKey('m', editor)
        assertEquals("m", state.pendingSequence)

        HelixKeyHandler.handleKey('r', editor)
        assertEquals("mr", state.pendingSequence)

        HelixKeyHandler.handleKey('(', editor)
        assertEquals("mr(", state.pendingSequence)

        HelixKeyHandler.handleKey('[', editor)
        assertEquals("", state.pendingSequence)

        assertEquals("hello [world] test", editor.document.text)
    }

    fun testSurroundReplaceQuotesToCurlyBraces() {
        myFixture.configureByText("test.txt", "val s = \"hello\"")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(11)

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('r', editor)
        HelixKeyHandler.handleKey('"', editor)
        HelixKeyHandler.handleKey('{', editor)

        assertEquals("val s = {hello}", editor.document.text)
    }

    fun testSurroundNestedParentheses() {
        myFixture.configureByText("test.txt", "val res = ((a + b) * c)")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(13) // inside "a + b"

        // Delete inner parentheses
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('d', editor)
        HelixKeyHandler.handleKey('(', editor)
        assertEquals("val res = (a + b * c)", editor.document.text)

        // Delete outer parentheses
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('d', editor)
        HelixKeyHandler.handleKey('(', editor)
        assertEquals("val res = a + b * c", editor.document.text)
    }

    fun testSurroundMultiCaret() {
        val text = "(foo) and (bar)"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        val primary = caretModel.primaryCaret
        primary.moveToOffset(text.indexOf("foo"))

        val secondary = caretModel.addCaret(editor.offsetToVisualPosition(text.indexOf("bar")))
        assertNotNull(secondary)

        // Replace '(' with '[' for both carets
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('r', editor)
        HelixKeyHandler.handleKey('(', editor)
        HelixKeyHandler.handleKey('[', editor)

        assertEquals("[foo] and [bar]", editor.document.text)

        // Delete '[' for both carets
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('d', editor)
        HelixKeyHandler.handleKey('[', editor)

        assertEquals("foo and bar", editor.document.text)
    }

    fun testSurroundAddWithoutSelection() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0) // on 'h'

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('s', editor)
        HelixKeyHandler.handleKey('(', editor)

        assertEquals("(h)ello world", editor.document.text)
    }

    fun testSurroundEscapeCancelsPending() {
        myFixture.configureByText("test.txt", "hello (world)")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('r', editor)
        HelixKeyHandler.handleKey('(', editor)
        assertEquals("mr(", state.pendingSequence)

        myFixture.testAction(jp.titze.intellij.helix.editor.HelixEscapeAction())
        assertEquals("", state.pendingSequence)
        assertEquals("hello (world)", editor.document.text)
    }

    fun testTextObjectWordInsideAndAround() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val state = HelixStateManager.getOrCreate(editor)

        // Caret on 'e' in "hello"
        caret.moveToOffset(1)

        // Test miw (select inside word)
        HelixKeyHandler.handleKey('m', editor)
        assertEquals("m", state.pendingSequence)
        HelixKeyHandler.handleKey('i', editor)
        assertEquals("mi", state.pendingSequence)
        HelixKeyHandler.handleKey('w', editor)
        assertEquals("", state.pendingSequence)
        assertTrue(caret.hasSelection())
        assertEquals("hello", caret.selectedText)

        // Clear selection
        caret.removeSelection()
        caret.moveToOffset(1)

        // Test maw (select around word - should include trailing space)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('w', editor)
        assertEquals("", state.pendingSequence)
        assertTrue(caret.hasSelection())
        assertEquals("hello ", caret.selectedText)

        // Test maw on last word of line (should include leading space)
        caret.removeSelection()
        caret.moveToOffset(8) // in "world"
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('w', editor)
        assertEquals(" world", caret.selectedText)
    }

    fun testTextObjectWordDeleteAndChange() {
        myFixture.configureByText("test.txt", "first second third")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0) // on 'first'
        // maw then d
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('w', editor)
        assertEquals("first ", caret.selectedText)

        HelixKeyHandler.handleKey('d', editor)
        assertEquals("second third", editor.document.text)

        // miw then c
        caret.moveToOffset(2) // in "second"
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('w', editor)
        assertEquals("second", caret.selectedText)

        HelixKeyHandler.handleKey('c', editor)
        assertEquals(" third", editor.document.text)
        assertEquals(HelixMode.INSERT, HelixStateManager.getOrCreate(editor).mode)
    }

    fun testTextObjectBigWord() {
        myFixture.configureByText("test.txt", "foo.bar(123) next")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(4) // on '.'

        // miW selects full non-whitespace token
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('W', editor)
        assertEquals("foo.bar(123)", caret.selectedText)

        // maW selects full token + trailing whitespace
        caret.removeSelection()
        caret.moveToOffset(4)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('W', editor)
        assertEquals("foo.bar(123) ", caret.selectedText)
    }

    fun testTextObjectParagraph() {
        val text = "line1\nline2\n\nline3\nline4\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(2) // on "line1"

        // mip: select lines of first paragraph
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('p', editor)
        assertEquals("line1\nline2", caret.selectedText)

        // map: select lines + trailing blank line
        caret.removeSelection()
        caret.moveToOffset(2)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('p', editor)
        assertEquals("line1\nline2\n\n", caret.selectedText)
    }

    fun testTextObjectPairsAndClosest() {
        myFixture.configureByText("test.txt", "val result = (calculate(\"param\"))")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(26) // inside "param"

        // mi": inside quotes
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('"', editor)
        assertEquals("param", caret.selectedText)

        // ma": around quotes
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('"', editor)
        assertEquals("\"param\"", caret.selectedText)

        // mim: closest enclosing pair (the quotes)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('m', editor)
        assertEquals("param", caret.selectedText)

        // Move to calculate(
        caret.removeSelection()
        caret.moveToOffset(15) // on "calculate"
        // mi(: inside outer parens
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('(', editor)
        assertEquals("calculate(\"param\")", caret.selectedText)

        // ma(: around outer parens
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('(', editor)
        assertEquals("(calculate(\"param\"))", caret.selectedText)
    }

    fun testTextObjectArgument() {
        myFixture.configureByText("test.txt", "call(first, second, third)")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(14) // inside "second"

        // mia: select inside argument
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('a', editor)
        assertEquals("second", caret.selectedText)

        // maa: select around argument (including comma and space)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('a', editor)
        assertEquals("second, ", caret.selectedText)
    }

    fun testTextObjectMultiCaret() {
        val text = "foo alpha bar\nbaz beta qux\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        val primary = caretModel.primaryCaret
        primary.moveToOffset(text.indexOf("alpha") + 1)

        val secondary = caretModel.addCaret(editor.offsetToVisualPosition(text.indexOf("beta") + 1))
        assertNotNull(secondary)

        // maw across both carets
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('w', editor)

        assertEquals("alpha ", primary.selectedText)
        assertEquals("beta ", secondary?.selectedText)

        // Delete selection across both carets
        HelixKeyHandler.handleKey('d', editor)
        assertEquals("foo bar\nbaz qux\n", editor.document.text)
    }

    fun testSpaceMenuChords() {
        myFixture.configureByText("test.txt", "sample text for space testing")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Press ' ' begins chord
        HelixKeyHandler.handleKey(' ', editor)
        assertEquals(" ", state.pendingSequence)

        // Second key clears chord and executes handler
        HelixKeyHandler.handleKey('y', editor)
        assertTrue(state.pendingSequence.isEmpty())
    }

    fun testSpaceReplaceWithClipboard() {
        myFixture.configureByText("test.txt", "hello old_world end")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(6) // on 'o' in old_world
        caret.setSelection(6, 15) // select "old_world"

        com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(
            java.awt.datatransfer.StringSelection("new_universe")
        )

        // Trigger space R
        HelixKeyHandler.handleKey(' ', editor)
        HelixKeyHandler.handleKey('R', editor)

        assertEquals("hello new_universe end", editor.document.text)
        assertEquals("new_universe", caret.selectedText)
    }

    fun testWhichKeyChordsSequenceReset() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('g', editor)
        assertEquals("g", state.pendingSequence)

        HelixKeyHandler.handleKey('h', editor) // move line start
        assertTrue(state.pendingSequence.isEmpty())
    }

    fun testHelixCommandPopupMatchingAndExecution() {
        myFixture.configureByText("test.txt", "line 1\nline 2")
        val editor = myFixture.editor

        // Verify command item matching
        val writeCmd = jp.titze.intellij.helix.command.HelixCommandPopup.COMMANDS.first { it.name == "write" }
        assertTrue(writeCmd.matches("w"))
        assertTrue(writeCmd.matches("write"))
        assertFalse(writeCmd.matches("unknown_xyz"))

        // Verify colon triggering HelixCommandPopup.show in tests without throwing
        HelixKeyHandler.handleKey(':', editor)
        // Execute command directly
        jp.titze.intellij.helix.command.HelixCommandPopup.executeCommand("format", editor)
    }

    fun test100ggJumpsToLine100() {
        val lines = (1..150).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Type 1, 0, 0, g, g
        HelixKeyHandler.handleKey('1', editor)
        assertEquals(1, state.count)
        HelixKeyHandler.handleKey('0', editor)
        assertEquals(10, state.count)
        HelixKeyHandler.handleKey('0', editor)
        assertEquals(100, state.count)

        HelixKeyHandler.handleKey('g', editor)
        assertEquals("g", state.pendingSequence)
        HelixKeyHandler.handleKey('g', editor)
        assertEquals("", state.pendingSequence)
        assertNull(state.count)

        val caret = editor.caretModel.primaryCaret
        val currentLineNumber = editor.document.getLineNumber(caret.offset)
        // 0-based line index for line 100 is 99
        assertEquals(99, currentLineNumber)
        val expectedOffset = editor.document.getLineStartOffset(99)
        assertEquals(expectedOffset, caret.offset)
    }

    fun test100ggWithSpaceJumpsToLine100() {
        val lines = (1..150).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor

        // Type 1, 0, 0, ' ', g, g
        HelixKeyHandler.handleKey('1', editor)
        HelixKeyHandler.handleKey('0', editor)
        HelixKeyHandler.handleKey('0', editor)
        HelixKeyHandler.handleKey(' ', editor)
        HelixKeyHandler.handleKey('g', editor)
        HelixKeyHandler.handleKey('g', editor)

        val caret = editor.caretModel.primaryCaret
        assertEquals(99, editor.document.getLineNumber(caret.offset))
    }

    fun testCountWithCapitalGJumpsToLine() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('5', editor)
        HelixKeyHandler.handleKey('0', editor)
        HelixKeyHandler.handleKey('G', editor)

        val caret = editor.caretModel.primaryCaret
        assertEquals(49, editor.document.getLineNumber(caret.offset))
    }

    fun testCountMotions() {
        val lines = (1..20).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // 5j moves down 5 lines
        HelixKeyHandler.handleKey('5', editor)
        HelixKeyHandler.handleKey('j', editor)
        assertEquals(5, editor.document.getLineNumber(caret.offset))

        // 3k moves up 3 lines
        HelixKeyHandler.handleKey('3', editor)
        HelixKeyHandler.handleKey('k', editor)
        assertEquals(2, editor.document.getLineNumber(caret.offset))
    }

    fun testNormalModeTypingDoesNotEdit() {
        val text = "hello world"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        jp.titze.intellij.helix.editor.HelixTypedActionHandler.install()

        // Type keys in normal mode via fixture (which goes through TypedActionHandler)
        myFixture.type('z')
        myFixture.type('q')
        myFixture.type('1')
        myFixture.type('2')
        assertEquals(text, editor.document.text)
    }

    fun testInsertModeAllowsEditing() {
        myFixture.configureByText("test.txt", "hello")
        val editor = myFixture.editor
        jp.titze.intellij.helix.editor.HelixTypedActionHandler.install()

        // Press 'i' to enter insert mode
        HelixKeyHandler.handleKey('i', editor)
        assertEquals(HelixMode.INSERT, HelixStateManager.getOrCreate(editor).mode)

        myFixture.type('!')
        assertEquals("!hello", editor.document.text)
    }

    fun testNormalModeBackspaceRemovesCountDigitWithoutEditingText() {
        val text = "some code text"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        jp.titze.intellij.helix.editor.HelixEditorActionHandler.install()

        HelixKeyHandler.handleKey('1', editor)
        HelixKeyHandler.handleKey('0', editor)
        assertEquals(10, state.count)

        myFixture.type('\b') // backspace
        assertEquals(1, state.count)
        assertEquals(text, editor.document.text)

        myFixture.type('\b') // backspace
        assertNull(state.count)
        assertEquals(text, editor.document.text)

        // Another backspace when count is empty should still not delete document text
        myFixture.type('\b')
        assertEquals(text, editor.document.text)
    }
}
