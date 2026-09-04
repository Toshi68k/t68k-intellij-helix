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
}
