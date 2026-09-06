package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

class HelixMotionsTest : BasePlatformTestCase() {
    fun testWordMotions() {
        myFixture.configureByText("test.txt", "hello world next")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // In Normal mode, motion 'w' moves to start of "world" and selects "hello "
        HelixMotions.moveNextWordStart(editor)
        caret.offset shouldBe 6
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "hello "

        // In Normal mode, another 'w' selects the next word "world " (anchor resets to 6)
        HelixMotions.moveNextWordStart(editor)
        caret.offset shouldBe 12
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "world "

        // Motion 'b' in Normal mode moves back to start of "world" and selects "world "
        HelixMotions.movePrevWordStart(editor)
        caret.offset shouldBe 6
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "world "

        // In Select mode ('v'), motion 'b' extends selection backwards to start of "hello"
        HelixActions.toggleSelectMode(editor)
        HelixStateManager.getOrCreate(editor).mode shouldBe HelixMode.SELECT
        HelixMotions.movePrevWordStart(editor)
        caret.offset shouldBe 0
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "hello world "
    }

    fun testWordMotionAtEndOfLine() {
        myFixture.configureByText("test.txt", "hello world\nnext line")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(6) // on 'world'

        // On the last word of a line, 'w' selects only up to the line break
        HelixMotions.moveNextWordStart(editor)
        caret.offset shouldBe 11 // on '\n'
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "world"

        // Pressing 'w' again crosses the line break into the next line
        HelixMotions.moveNextWordStart(editor)
        caret.offset shouldBe 17 // on 'l' of "line"
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "next "

        // With trailing whitespace before line break:
        myFixture.configureByText("test2.txt", "first second   \nthird")
        val editor2 = myFixture.editor
        val caret2 = editor2.caretModel.primaryCaret
        caret2.moveToOffset(6) // on 'second'

        HelixMotions.moveNextWordStart(editor2)
        caret2.offset shouldBe 15 // on '\n'
        caret2.selectedText shouldBe "second   "

        // Next line with indentation:
        myFixture.configureByText("test3.txt", "line1\n    line2")
        val editor3 = myFixture.editor
        val caret3 = editor3.caretModel.primaryCaret
        caret3.moveToOffset(0) // on 'line1'

        // First 'w' selects up to '\n'
        HelixMotions.moveNextWordStart(editor3)
        caret3.offset shouldBe 5 // on '\n'
        caret3.selectedText shouldBe "line1"

        // Second 'w' selects indentation
        HelixMotions.moveNextWordStart(editor3)
        caret3.offset shouldBe 10 // on 'l' of "line2"
        caret3.selectedText shouldBe "    "

        // Third 'w' selects "line2"
        HelixMotions.moveNextWordStart(editor3)
        caret3.offset shouldBe 15
        caret3.selectedText shouldBe "line2"

        // CRLF line endings (IntelliJ normalizes \r\n to \n in Document):
        myFixture.configureByText("test4.txt", "hello world\r\nnext")
        val editor4 = myFixture.editor
        val caret4 = editor4.caretModel.primaryCaret
        caret4.moveToOffset(6) // on 'world'

        HelixMotions.moveNextWordStart(editor4)
        caret4.offset shouldBe 11
        caret4.selectedText shouldBe "world"

        HelixMotions.moveNextWordStart(editor4)
        caret4.offset shouldBe 16 // end of "next"
        caret4.selectedText shouldBe "next"

        // Select mode: extends selection across the line break
        myFixture.configureByText("test5.txt", "hello world\nnext line")
        val editor5 = myFixture.editor
        val caret5 = editor5.caretModel.primaryCaret
        caret5.moveToOffset(6) // on 'world'

        HelixActions.toggleSelectMode(editor5)
        HelixMotions.moveNextWordStart(editor5)
        caret5.offset shouldBe 11
        caret5.selectedText shouldBe "world"

        HelixMotions.moveNextWordStart(editor5)
        caret5.offset shouldBe 17
        caret5.selectedText shouldBe "world\nnext "
    }

    fun testLineSelectionMotion() {
        myFixture.configureByText("test.txt", "first line\nsecond line\nthird line\n")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(2)

        // First 'x' selects current line including newline
        HelixMotions.selectLine(editor)
        caret.selectedText shouldBe "first line\n"

        // Second 'x' extends selection down to next line
        HelixMotions.selectLine(editor)
        caret.selectedText shouldBe "first line\nsecond line\n"
    }

    fun testSelectAllMotion() {
        val text = "first line\nsecond line\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        HelixMotions.selectAll(editor)
        editor.selectionModel.selectedText shouldBe text
    }

    fun test100ggJumpsToLine100() {
        val lines = (1..150).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Type 1, 0, 0, g, g
        HelixKeyHandler.handleKey('1', editor)
        state.count shouldBe 1
        HelixKeyHandler.handleKey('0', editor)
        state.count shouldBe 10
        HelixKeyHandler.handleKey('0', editor)
        state.count shouldBe 100

        HelixKeyHandler.handleKey('g', editor)
        state.pendingSequence shouldBe "g"
        HelixKeyHandler.handleKey('g', editor)
        state.pendingSequence shouldBe ""
        state.count.shouldBeNull()

        val caret = editor.caretModel.primaryCaret
        val currentLineNumber = editor.document.getLineNumber(caret.offset)
        // 0-based line index for line 100 is 99
        currentLineNumber shouldBe 99
        val expectedOffset = editor.document.getLineStartOffset(99)
        caret.offset shouldBe expectedOffset
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
        editor.document.getLineNumber(caret.offset) shouldBe 99
    }

    fun testCountWithCapitalGJumpsToLine() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('5', editor)
        HelixKeyHandler.handleKey('0', editor)
        HelixKeyHandler.handleKey('G', editor)

        val caret = editor.caretModel.primaryCaret
        editor.document.getLineNumber(caret.offset) shouldBe 49
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
        editor.document.getLineNumber(caret.offset) shouldBe 5

        // 3k moves up 3 lines
        HelixKeyHandler.handleKey('3', editor)
        HelixKeyHandler.handleKey('k', editor)
        editor.document.getLineNumber(caret.offset) shouldBe 2
    }

    fun testNormalModeFindTillChar() {
        val initialText = "val total = 100"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('t', editor)
        HelixKeyHandler.handleKey('t', editor)

        // 't' matches 't' in "total" at index 4 -> target offset is 4 (before 't')
        caret.offset shouldBe 4
        caret.selectedText shouldBe "val "
    }

    fun testNormalModeFindToChar() {
        val initialText = "val total = 100"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('f', editor)
        HelixKeyHandler.handleKey('t', editor)

        // 'f' matches 't' at index 4 -> target offset is 5 (inclusive of 't')
        caret.offset shouldBe 5
        caret.selectedText shouldBe "val t"
    }

    fun testSelectModeFindTillCharExtendsSelection() {
        val initialText = "hello world from helix"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // Enter SELECT mode
        HelixKeyHandler.handleKey('v', editor)
        // Move word to "world"
        HelixKeyHandler.handleKey('w', editor)
        caret.offset shouldBe 6
        caret.selectedText shouldBe "hello "

        // Till 'h' in "helix" (index 17)
        HelixKeyHandler.handleKey('t', editor)
        HelixKeyHandler.handleKey('h', editor)

        caret.offset shouldBe 17
        caret.selectedText shouldBe "hello world from "
    }

    fun testSelectModeFindTillEnterSelectsLineEnd() {
        val initialText = "first line\nsecond line"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        jp.titze.intellij.helix.editor.HelixEditorActionHandler.install()

        // Enter SELECT mode
        HelixKeyHandler.handleKey('v', editor)
        // Till Enter (\n)
        HelixKeyHandler.handleKey('t', editor)
        myFixture.type('\n')

        // Target should be index 10 (just before \n)
        caret.offset shouldBe 10
        caret.selectedText shouldBe "first line"
    }

    fun testSelectModeFindToEnterIncludesNewline() {
        val initialText = "first line\nsecond line"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        jp.titze.intellij.helix.editor.HelixEditorActionHandler.install()

        // Enter SELECT mode
        HelixKeyHandler.handleKey('v', editor)
        // Find to Enter (\n)
        HelixKeyHandler.handleKey('f', editor)
        myFixture.type('\n')

        // Target should be index 11 (after \n)
        caret.offset shouldBe 11
        caret.selectedText shouldBe "first line\n"
    }

    fun testBackwardFindCharMotions() {
        val initialText = "apple banana cherry"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Move to end of "cherry" (offset 19)
        caret.moveToOffset(19)

        // Find backwards to 'b' (index 6)
        HelixKeyHandler.handleKey('F', editor)
        HelixKeyHandler.handleKey('b', editor)

        caret.offset shouldBe 6
        caret.selectedText shouldBe "banana cherry"

        // Clear selection and test T (till prev char)
        caret.removeSelection()
        caret.moveToOffset(19)

        HelixKeyHandler.handleKey('T', editor)
        HelixKeyHandler.handleKey('b', editor)

        caret.offset shouldBe 7
        caret.selectedText shouldBe "anana cherry"
    }

    fun testFindCharCountPrefix() {
        val initialText = "abc abc abc"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // 2nd occurrence of 'b' (at index 5)
        HelixKeyHandler.handleKey('2', editor)
        HelixKeyHandler.handleKey('t', editor)
        HelixKeyHandler.handleKey('b', editor)

        caret.offset shouldBe 5
        caret.selectedText shouldBe "abc a"
    }

    fun testPendingFindCancelledByBackspace() {
        val initialText = "testing find motion"
        myFixture.configureByText("test.txt", initialText)
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        jp.titze.intellij.helix.editor.HelixEditorActionHandler.install()

        HelixKeyHandler.handleKey('t', editor)
        state.pendingSequence shouldBe "t"

        myFixture.type('\b')
        state.pendingSequence shouldBe ""
        editor.document.text shouldBe initialText
    }

    fun testPageDownAndPageUpNormalMode() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        val pageSize = HelixMotions.getPageSize(editor)

        HelixMotions.pageDown(editor)
        editor.document.getLineNumber(caret.offset) shouldBe pageSize
        caret.hasSelection().shouldBeFalse()

        HelixMotions.pageUp(editor)
        editor.document.getLineNumber(caret.offset) shouldBe 0
        caret.hasSelection().shouldBeFalse()
    }

    fun testHalfPageDownAndHalfPageUpNormalMode() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        val halfPage = (HelixMotions.getPageSize(editor) / 2).coerceAtLeast(1)

        HelixMotions.halfPageDown(editor)
        editor.document.getLineNumber(caret.offset) shouldBe halfPage
        caret.hasSelection().shouldBeFalse()

        HelixMotions.halfPageUp(editor)
        editor.document.getLineNumber(caret.offset) shouldBe 0
        caret.hasSelection().shouldBeFalse()
    }

    fun testPageMotionsInSelectModeExtendsSelection() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('v', editor)
        HelixStateManager.getOrCreate(editor).mode shouldBe HelixMode.SELECT

        val halfPage = (HelixMotions.getPageSize(editor) / 2).coerceAtLeast(1)
        HelixMotions.halfPageDown(editor)

        editor.document.getLineNumber(caret.offset) shouldBe halfPage
        caret.hasSelection().shouldBeTrue()
        caret.selectionStart shouldBe 0
        caret.selectionEnd shouldBe caret.offset
    }

    fun testPageMotionsWithCountPrefix() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        val halfPage = (HelixMotions.getPageSize(editor) / 2).coerceAtLeast(1)

        // Type '2' then Ctrl-D ('\u0004')
        HelixKeyHandler.handleKey('2', editor)
        HelixKeyHandler.handleKey('\u0004', editor)

        editor.document.getLineNumber(caret.offset) shouldBe halfPage * 2
    }

    fun testPageMotionsViaActions() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        val dataContext = com.intellij.ide.DataManager.getInstance().getDataContext(editor.contentComponent)
        val pageDownAction = jp.titze.intellij.helix.editor.HelixPageDownAction()
        val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
            pageDownAction,
            null,
            com.intellij.openapi.actionSystem.ActionPlaces.KEYBOARD_SHORTCUT,
            dataContext,
        )

        pageDownAction.update(event)
        event.presentation.isEnabled.shouldBeTrue()

        pageDownAction.actionPerformed(event)
        val pageSize = HelixMotions.getPageSize(editor)
        editor.document.getLineNumber(caret.offset) shouldBe pageSize

        val halfPageUpAction = jp.titze.intellij.helix.editor.HelixHalfPageUpAction()
        val halfUpEvent = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
            halfPageUpAction,
            null,
            com.intellij.openapi.actionSystem.ActionPlaces.KEYBOARD_SHORTCUT,
            dataContext,
        )
        halfPageUpAction.actionPerformed(halfUpEvent)
        val halfPage = (pageSize / 2).coerceAtLeast(1)
        editor.document.getLineNumber(caret.offset) shouldBe pageSize - halfPage
    }

    fun testPageMotionsClampedAtDocumentBounds() {
        val lines = (1..10).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // page down when file has only 10 lines (pageSize=25) -> clamps to last line (index 9)
        HelixMotions.pageDown(editor)
        editor.document.getLineNumber(caret.offset) shouldBe 9

        // page up from last line -> clamps to line 0
        HelixMotions.pageUp(editor)
        editor.document.getLineNumber(caret.offset) shouldBe 0
    }

    fun testHelixEventDispatcherCtrlFAndCtrlB() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()
        val ctrlF = java.awt.event.KeyEvent(
            editor.contentComponent,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            java.awt.event.InputEvent.CTRL_DOWN_MASK,
            java.awt.event.KeyEvent.VK_F,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )

        val handled = dispatcher.dispatch(ctrlF)
        handled.shouldBeTrue()
        ctrlF.isConsumed.shouldBeTrue()

        val pageSize = HelixMotions.getPageSize(editor)
        editor.document.getLineNumber(caret.offset) shouldBe pageSize

        val ctrlB = java.awt.event.KeyEvent(
            editor.contentComponent,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            java.awt.event.InputEvent.CTRL_DOWN_MASK,
            java.awt.event.KeyEvent.VK_B,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )

        val handledB = dispatcher.dispatch(ctrlB)
        handledB.shouldBeTrue()
        ctrlB.isConsumed.shouldBeTrue()
        editor.document.getLineNumber(caret.offset) shouldBe 0
    }

    fun testHelixEventDispatcherCtrlDAndCtrlU() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()
        val ctrlD = java.awt.event.KeyEvent(
            editor.contentComponent,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            java.awt.event.InputEvent.CTRL_DOWN_MASK,
            java.awt.event.KeyEvent.VK_D,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )

        dispatcher.dispatch(ctrlD).shouldBeTrue()
        ctrlD.isConsumed.shouldBeTrue()

        val halfPage = (HelixMotions.getPageSize(editor) / 2).coerceAtLeast(1)
        editor.document.getLineNumber(caret.offset) shouldBe halfPage

        val ctrlU = java.awt.event.KeyEvent(
            editor.contentComponent,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            java.awt.event.InputEvent.CTRL_DOWN_MASK,
            java.awt.event.KeyEvent.VK_U,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )

        dispatcher.dispatch(ctrlU).shouldBeTrue()
        ctrlU.isConsumed.shouldBeTrue()
        editor.document.getLineNumber(caret.offset) shouldBe 0
    }

    fun testHelixEventDispatcherIgnoredInInsertMode() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        HelixActions.enterInsert(editor)

        val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()
        val ctrlF = java.awt.event.KeyEvent(
            editor.contentComponent,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            java.awt.event.InputEvent.CTRL_DOWN_MASK,
            java.awt.event.KeyEvent.VK_F,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )

        dispatcher.dispatch(ctrlF).shouldBeFalse()
        ctrlF.isConsumed.shouldBeFalse()
    }

    fun testHelixEventDispatcherWithCount() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.appendCountDigit('2')

        val dispatcher = jp.titze.intellij.helix.editor.HelixEventDispatcher()
        val ctrlD = java.awt.event.KeyEvent(
            editor.contentComponent,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            java.awt.event.InputEvent.CTRL_DOWN_MASK,
            java.awt.event.KeyEvent.VK_D,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )

        dispatcher.dispatch(ctrlD).shouldBeTrue()
        val halfPage = (HelixMotions.getPageSize(editor) / 2).coerceAtLeast(1)
        editor.document.getLineNumber(editor.caretModel.offset) shouldBe halfPage * 2
    }

    fun testMoveFileEndViaGe() {
        val lines = (1..200).joinToString("\n") { "line $it" }
        myFixture.configureByText("test.txt", lines)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey('g', editor)
        HelixKeyHandler.handleKey('e', editor)

        caret.offset shouldBe lines.length
        editor.document.getLineNumber(caret.offset) shouldBe 199
    }

    fun testMoveLineStartAndEnd() {
        myFixture.configureByText("test.txt", "   val hello = 42\n")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(10)

        // Move to start of line (actual first character)
        HelixMotions.moveLineStart(editor)
        caret.offset shouldBe 0

        // Move to first non-whitespace character of line
        HelixMotions.moveLineFirstNonWhitespace(editor)
        caret.offset shouldBe 3

        // Move to end of line
        HelixMotions.moveLineEnd(editor)
        caret.offset shouldBe 17
    }

    fun testMoveLineStartAndEndMultipleCarets() {
        myFixture.configureByText("test.txt", "   line one\n     line two\n")
        val editor = myFixture.editor
        val doc = editor.document

        // Add a secondary caret on the second line
        editor.caretModel.primaryCaret.moveToOffset(8)
        val secondCaret = editor.caretModel.addCaret(
            editor.offsetToVisualPosition(doc.getLineStartOffset(1) + 7),
            false,
        )
        secondCaret.shouldNotBeNull()

        editor.caretModel.caretCount shouldBe 2

        // Move all carets to line start (actual first character)
        HelixMotions.moveLineStart(editor)
        editor.caretModel.primaryCaret.offset shouldBe 0
        secondCaret.offset shouldBe doc.getLineStartOffset(1)

        // Move all carets to first non-whitespace character
        HelixMotions.moveLineFirstNonWhitespace(editor)
        editor.caretModel.primaryCaret.offset shouldBe 3
        secondCaret.offset shouldBe doc.getLineStartOffset(1) + 5

        // Move all carets to line end
        HelixMotions.moveLineEnd(editor)
        editor.caretModel.primaryCaret.offset shouldBe doc.getLineEndOffset(0)
        secondCaret.offset shouldBe doc.getLineEndOffset(1)
    }

    fun testKeyHandlerGotoLineStartAndFirstNonWhitespace() {
        myFixture.configureByText("test.txt", "   val hello = 42\n")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Test gs: move to first non-whitespace
        caret.moveToOffset(10)
        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('s', editor).shouldBeTrue()
        caret.offset shouldBe 3

        // Test gh: move to actual line start (first character)
        caret.moveToOffset(10)
        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('h', editor).shouldBeTrue()
        caret.offset shouldBe 0
    }

    fun testViewModeTransientCenter() {
        myFixture.configureByText("view_center.txt", (1..50).joinToString("\n") { "line $it" })
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
        state.pendingSequence shouldBe "z"

        HelixKeyHandler.handleKey('c', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""

        HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""
    }

    fun testViewModeTransientTopAndBottom() {
        myFixture.configureByText("view_tb.txt", (1..50).joinToString("\n") { "line $it" })
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""

        HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('b', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""

        HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""
    }

    fun testViewModeScrollWithoutMovingCaret() {
        myFixture.configureByText("view_scroll.txt", (1..100).joinToString("\n") { "line $it" })
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val startOffset = editor.document.getLineStartOffset(10)
        caret.moveToOffset(startOffset)

        HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('j', editor).shouldBeTrue()
        caret.offset shouldBe startOffset

        HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('k', editor).shouldBeTrue()
        caret.offset shouldBe startOffset
    }

    fun testStickyViewModeZ() {
        myFixture.configureByText("view_sticky.txt", (1..50).joinToString("\n") { "line $it" })
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        val caret = editor.caretModel.primaryCaret
        val startOffset = editor.document.getLineStartOffset(5)
        caret.moveToOffset(startOffset)

        // Enter sticky view mode
        HelixKeyHandler.handleKey('Z', editor).shouldBeTrue()
        state.pendingSequence shouldBe "Z"

        // Execute multiple view commands while remaining in sticky view mode
        HelixKeyHandler.handleKey('j', editor).shouldBeTrue()
        state.pendingSequence shouldBe "Z"
        caret.offset shouldBe startOffset

        HelixKeyHandler.handleKey('k', editor).shouldBeTrue()
        state.pendingSequence shouldBe "Z"
        caret.offset shouldBe startOffset

        HelixKeyHandler.handleKey('c', editor).shouldBeTrue()
        state.pendingSequence shouldBe "Z"

        // Press Escape to exit sticky view mode
        jp.titze.intellij.helix.editor.HelixEscapeHandler.handleEscape(editor).shouldBeTrue()
        state.pendingSequence shouldBe ""
    }
}
