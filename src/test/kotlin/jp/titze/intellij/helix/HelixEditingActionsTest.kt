package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

class HelixEditingActionsTest : BasePlatformTestCase() {
    fun testInitialModeIsNormal() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.mode shouldBe HelixMode.NORMAL
        editor.settings.isBlockCursor.shouldBeTrue()
    }

    fun testModeTransitions() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixActions.enterInsert(editor)
        state.mode shouldBe HelixMode.INSERT
        editor.settings.isBlockCursor.shouldBeFalse()

        HelixActions.enterNormalMode(editor)
        state.mode shouldBe HelixMode.NORMAL
        editor.settings.isBlockCursor.shouldBeTrue()

        HelixActions.toggleSelectMode(editor)
        state.mode shouldBe HelixMode.SELECT

        HelixActions.toggleSelectMode(editor)
        state.mode shouldBe HelixMode.NORMAL
    }

    fun testDeleteAndChangeActions() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // In Select mode, select "hello " and 'd' deletes selection
        HelixActions.toggleSelectMode(editor)
        HelixMotions.moveNextWordStart(editor)
        caret.selectedText shouldBe "hello "

        HelixActions.deleteSelection(editor, enterInsert = false)
        editor.document.text shouldBe "world"
        HelixStateManager.getOrCreate(editor).mode shouldBe HelixMode.SELECT

        // Select "world" and 'c' (change) deletes and enters Insert mode
        HelixMotions.moveWordEnd(editor)
        HelixActions.deleteSelection(editor, enterInsert = true)
        editor.document.text shouldBe ""
        HelixStateManager.getOrCreate(editor).mode shouldBe HelixMode.INSERT
    }

    fun testKeyHandlerDispatch() {
        myFixture.configureByText("test.txt", "foo bar baz")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Press 'w' in Normal mode
        val handledW = HelixKeyHandler.handleKey('w', editor)
        handledW.shouldBeTrue()
        editor.caretModel.primaryCaret.offset shouldBe 4

        // Press 'g' sets pending sequence
        val handledG = HelixKeyHandler.handleKey('g', editor)
        handledG.shouldBeTrue()
        state.pendingSequence shouldBe "g"

        // Press 'g' completes 'gg'
        val handledG2 = HelixKeyHandler.handleKey('g', editor)
        handledG2.shouldBeTrue()
        state.pendingSequence shouldBe ""
        editor.caretModel.primaryCaret.offset shouldBe 0
    }

    fun testSpaceReplaceWithClipboard() {
        myFixture.configureByText("test.txt", "hello old_world end")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(6) // on 'o' in old_world
        caret.setSelection(6, 15) // select "old_world"

        com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(
            java.awt.datatransfer.StringSelection("new_universe"),
        )

        // Trigger space R
        HelixKeyHandler.handleKey(' ', editor)
        HelixKeyHandler.handleKey('R', editor)

        editor.document.text shouldBe "hello new_universe end"
        caret.selectedText shouldBe "new_universe"
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
        editor.document.text shouldBe text
    }

    fun testInsertModeAllowsEditing() {
        myFixture.configureByText("test.txt", "hello")
        val editor = myFixture.editor
        jp.titze.intellij.helix.editor.HelixTypedActionHandler.install()

        // Press 'i' to enter insert mode
        HelixKeyHandler.handleKey('i', editor)
        HelixStateManager.getOrCreate(editor).mode shouldBe HelixMode.INSERT

        myFixture.type('!')
        editor.document.text shouldBe "!hello"
    }

    fun testNormalModeBackspaceRemovesCountDigitWithoutEditingText() {
        val text = "some code text"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        jp.titze.intellij.helix.editor.HelixEditorActionHandler.install()

        HelixKeyHandler.handleKey('1', editor)
        HelixKeyHandler.handleKey('0', editor)
        state.count shouldBe 10

        myFixture.type('\b') // backspace
        state.count shouldBe 1
        editor.document.text shouldBe text

        myFixture.type('\b') // backspace
        state.count.shouldBeNull()
        editor.document.text shouldBe text

        // Another backspace when count is empty should still not delete document text
        myFixture.type('\b')
        editor.document.text shouldBe text
    }

    fun testPasteMultipleLinesCopiedWithXPastesBelowLineAtColumnZero() {
        val initialText = "line 1\nline 2\nline 3\nline 4\nline 5"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Move to line 2 ("line 2")
        caret.moveToOffset(editor.document.getLineStartOffset(1))
        // Select 2 lines with x (line 2 and line 3)
        HelixKeyHandler.handleKey('x', editor)
        HelixKeyHandler.handleKey('x', editor)
        caret.selectedText shouldBe "line 2\nline 3\n"

        // Yank with y
        HelixKeyHandler.handleKey('y', editor)

        // Now move somewhere in the middle of line 5 ("line 5", say offset at column 3)
        caret.removeSelection()
        val line4Start = editor.document.getLineStartOffset(4) // line 5 (0-indexed line 4)
        caret.moveToOffset(line4Start + 3) // in middle of "line 5"

        // Paste with p
        HelixKeyHandler.handleKey('p', editor)

        val expectedText = "line 1\nline 2\nline 3\nline 4\nline 5\nline 2\nline 3"
        editor.document.text shouldBe expectedText
        // Caret should be at the start of the first pasted line (column 0)
        val pastedLineStart = editor.document.getLineStartOffset(5)
        caret.offset shouldBe pastedLineStart
    }

    fun testPasteMultipleLinesCopiedWithXPastesBelowMiddleOfFile() {
        val initialText = "alpha\nbeta\ngamma\ndelta"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Select line 1 ("alpha") and line 2 ("beta") with x
        caret.moveToOffset(0)
        HelixKeyHandler.handleKey('x', editor)
        HelixKeyHandler.handleKey('x', editor)
        caret.selectedText shouldBe "alpha\nbeta\n"
        HelixKeyHandler.handleKey('y', editor)

        // Move to middle of line "gamma" (line index 2, col 3)
        caret.removeSelection()
        val gammaStart = editor.document.getLineStartOffset(2)
        caret.moveToOffset(gammaStart + 3)

        // Press p
        HelixKeyHandler.handleKey('p', editor)

        val expected = "alpha\nbeta\ngamma\nalpha\nbeta\ndelta"
        editor.document.text shouldBe expected
        caret.offset shouldBe editor.document.getLineStartOffset(3)
    }

    fun testPasteMultipleLinesBeforeWithP() {
        val initialText = "line 1\nline 2\nline 3"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Select line 1 and 2
        caret.moveToOffset(0)
        HelixKeyHandler.handleKey('x', editor)
        HelixKeyHandler.handleKey('x', editor)
        HelixKeyHandler.handleKey('y', editor)

        // Move into middle of line 3 (index 2)
        caret.removeSelection()
        val line3Start = editor.document.getLineStartOffset(2)
        caret.moveToOffset(line3Start + 3)

        // Press P (paste before)
        HelixKeyHandler.handleKey('P', editor)

        val expected = "line 1\nline 2\nline 1\nline 2\nline 3"
        editor.document.text shouldBe expected
        caret.offset shouldBe line3Start
    }

    fun testPasteSingleLineCopiedWithXPastesBelowLine() {
        val initialText = "first\nsecond\nthird"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Select single line "first" with x
        caret.moveToOffset(2)
        HelixKeyHandler.handleKey('x', editor)
        caret.selectedText shouldBe "first\n"
        HelixKeyHandler.handleKey('y', editor)

        // Move to column 3 of "second"
        caret.removeSelection()
        caret.moveToOffset(editor.document.getLineStartOffset(1) + 3)
        HelixKeyHandler.handleKey('p', editor)

        val expected = "first\nsecond\nfirst\nthird"
        editor.document.text shouldBe expected
        caret.offset shouldBe editor.document.getLineStartOffset(2)
    }

    fun testJoinTwoLinesNormalMode() {
        val initialText = "hello\nworld\nnext"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(2)

        HelixKeyHandler.handleKey('J', editor)

        editor.document.text shouldBe "hello world\nnext"
        caret.offset shouldBe 5
        caret.hasSelection().shouldBeFalse()
    }

    fun testJoinLinesTrimsWhitespaceAndIndentation() {
        val initialText = "val x = 10   \n    + 20"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('J', editor)

        editor.document.text shouldBe "val x = 10 + 20"
        caret.offset shouldBe 10
    }

    fun testJoinLinesWithCount() {
        val initialText = "line 1\nline 2\nline 3\nline 4"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // 2J joins current line with next 2 lines (3 lines total)
        HelixKeyHandler.handleKey('2', editor)
        HelixKeyHandler.handleKey('J', editor)

        editor.document.text shouldBe "line 1 line 2 line 3\nline 4"
    }

    fun testJoinLinesWithMultilineSelection() {
        val initialText = "line 1\nline 2\nline 3\nline 4"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // Select line 1 and extend to line 2 with x
        HelixKeyHandler.handleKey('x', editor)
        HelixKeyHandler.handleKey('x', editor)
        caret.selectedText shouldBe "line 1\nline 2\n"

        HelixKeyHandler.handleKey('J', editor)

        editor.document.text shouldBe "line 1 line 2\nline 3\nline 4"
    }

    fun testJoinLinesOnLastLineDoesNothing() {
        val initialText = "first line\nsecond line"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(editor.document.getLineStartOffset(1))

        HelixKeyHandler.handleKey('J', editor)

        editor.document.text shouldBe initialText
    }

    fun testJoinLinesInSelectModeKeepsSelection() {
        val initialText = "hello\nworld\nfoo"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('v', editor)
        HelixKeyHandler.handleKey('j', editor)

        HelixKeyHandler.handleKey('J', editor)

        editor.document.text shouldBe "hello world\nfoo"
        HelixStateManager.getOrCreate(editor).mode shouldBe HelixMode.SELECT
        caret.selectedText shouldBe "hello world"
    }

    fun testReplaceCharNormalMode() {
        val initialText = "hello world"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(6) // on 'w'

        val state = HelixStateManager.getOrCreate(editor)
        HelixKeyHandler.handleKey('r', editor)
        state.pendingSequence shouldBe "r"

        HelixKeyHandler.handleKey('W', editor)
        state.pendingSequence shouldBe ""
        editor.document.text shouldBe "hello World"
        caret.offset shouldBe 6
        caret.hasSelection().shouldBeFalse()
    }

    fun testReplaceCharWithCount() {
        val initialText = "hello world"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('3', editor)
        HelixKeyHandler.handleKey('r', editor)
        HelixKeyHandler.handleKey('z', editor)

        editor.document.text shouldBe "zzzlo world"
        caret.offset shouldBe 0
        caret.hasSelection().shouldBeFalse()
    }

    fun testReplaceCharSelectionPreservesNewlines() {
        val initialText = "abc\ndef\n"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.setSelection(0, 7) // "abc\ndef"

        HelixKeyHandler.handleKey('r', editor)
        HelixKeyHandler.handleKey('x', editor)

        editor.document.text shouldBe "xxx\nxxx\n"
        caret.selectedText shouldBe "xxx\nxxx"
        caret.offset shouldBe 0
    }

    fun testReplaceCharWithEnter() {
        val initialText = "hello world"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(5) // space

        jp.titze.intellij.helix.editor.HelixEditorActionHandler.install()

        HelixKeyHandler.handleKey('r', editor)
        HelixStateManager.getOrCreate(editor).pendingSequence shouldBe "r"

        myFixture.type('\n')

        editor.document.text shouldBe "hello\nworld"
        caret.offset shouldBe 5
    }

    fun testReplaceCharMultiCaret() {
        val initialText = "foo bar baz"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.moveToOffset(0) // 'f'
        editor.caretModel.addCaret(editor.offsetToVisualPosition(4), false) // 'b' in bar
        editor.caretModel.addCaret(editor.offsetToVisualPosition(8), false) // 'b' in baz

        HelixKeyHandler.handleKey('r', editor)
        HelixKeyHandler.handleKey('X', editor)

        editor.document.text shouldBe "Xoo Xar Xaz"
    }

    fun testPendingReplaceCancelledByBackspace() {
        val initialText = "cancel test"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        jp.titze.intellij.helix.editor.HelixEditorActionHandler.install()

        HelixKeyHandler.handleKey('r', editor)
        state.pendingSequence shouldBe "r"

        myFixture.type('\b')
        state.pendingSequence shouldBe ""
        editor.document.text shouldBe initialText
    }

    fun testPendingReplaceCancelledByEscape() {
        val initialText = "cancel test"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('r', editor)
        state.pendingSequence shouldBe "r"

        val escapeAction = jp.titze.intellij.helix.editor.HelixEscapeAction()
        val dataContext = com.intellij.ide.DataManager.getInstance().getDataContext(editor.contentComponent)
        val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
            escapeAction,
            null,
            com.intellij.openapi.actionSystem.ActionPlaces.KEYBOARD_SHORTCUT,
            dataContext,
        )
        escapeAction.actionPerformed(event)

        state.pendingSequence shouldBe ""
        editor.document.text shouldBe initialText
    }

    fun testChangeKeyEntersInsertAndEscapeReturnsToNormal() {
        jp.titze.intellij.helix.editor.HelixEditorActionHandler.install()
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.mode shouldBe HelixMode.NORMAL

        // Press 'c' to change selection / character
        HelixKeyHandler.handleKey('c', editor)
        state.mode shouldBe HelixMode.INSERT
        editor.document.text shouldBe "ello world"

        // Escape should return to Normal mode
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ESCAPE)
        state.mode shouldBe HelixMode.NORMAL
    }

    fun testEscapeHandlerReturnsToNormalFromInsert() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.INSERT)

        jp.titze.intellij.helix.editor.HelixEscapeHandler.handleEscape(editor)
        state.mode shouldBe HelixMode.NORMAL
    }

    fun testSingleKeyReplaceWithClipboard() {
        val initialText = "hello old_world end"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(6)
        caret.setSelection(6, 15) // select "old_world"

        com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(
            java.awt.datatransfer.StringSelection("new_universe"),
        )

        HelixKeyHandler.handleKey('R', editor)

        editor.document.text shouldBe "hello new_universe end"
        caret.selectedText shouldBe "new_universe"
    }
}
