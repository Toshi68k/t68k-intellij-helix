package jp.titze.intellij.helix

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeSameInstanceAs
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixPromptType
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixEditorTest : BasePlatformTestCase() {

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


    fun testMultiCaretLineSelection() {
        val text = "line 1\nline 2\nline 3\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        val primary = caretModel.primaryCaret
        primary.moveToOffset(0)
        val secondary = caretModel.addCaret(editor.offsetToVisualPosition(text.indexOf("line 2")))
        secondary.shouldNotBeNull()

        HelixMotions.selectLine(editor)

        primary.selectedText shouldBe "line 1\n"
        secondary.selectedText shouldBe "line 2\n"
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

    fun testSurroundAddWithSelection() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Select "world" (offsets 6 to 11)
        caret.setSelection(6, 11)

        val state = HelixStateManager.getOrCreate(editor)
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        state.pendingSequence shouldBe "m"

        HelixKeyHandler.handleKey('s', editor).shouldBeTrue()
        state.pendingSequence shouldBe "ms"

        HelixKeyHandler.handleKey('(', editor).shouldBeTrue()
        state.pendingSequence shouldBe ""

        editor.document.text shouldBe "hello (world)"
        caret.selectedText shouldBe "(world)"
    }

    fun testSurroundAddQuotes() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.setSelection(6, 11)

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('s', editor)
        HelixKeyHandler.handleKey('"', editor)

        editor.document.text shouldBe "hello \"world\""
        caret.selectedText shouldBe "\"world\""
    }

    fun testSurroundDeleteParentheses() {
        myFixture.configureByText("test.txt", "hello (world) test")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(9) // inside "world"

        val state = HelixStateManager.getOrCreate(editor)
        HelixKeyHandler.handleKey('m', editor)
        state.pendingSequence shouldBe "m"

        HelixKeyHandler.handleKey('d', editor)
        state.pendingSequence shouldBe "md"

        HelixKeyHandler.handleKey('(', editor)
        state.pendingSequence shouldBe ""

        editor.document.text shouldBe "hello world test"
    }

    fun testSurroundDeleteQuotes() {
        myFixture.configureByText("test.txt", "val s = \"hello world\"")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(12) // inside "hello world"

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('d', editor)
        HelixKeyHandler.handleKey('"', editor)

        editor.document.text shouldBe "val s = hello world"
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

        editor.document.text shouldBe "hello world test"
    }

    fun testSurroundReplaceParenthesesToBrackets() {
        myFixture.configureByText("test.txt", "hello (world) test")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(9)

        val state = HelixStateManager.getOrCreate(editor)
        HelixKeyHandler.handleKey('m', editor)
        state.pendingSequence shouldBe "m"

        HelixKeyHandler.handleKey('r', editor)
        state.pendingSequence shouldBe "mr"

        HelixKeyHandler.handleKey('(', editor)
        state.pendingSequence shouldBe "mr("

        HelixKeyHandler.handleKey('[', editor)
        state.pendingSequence shouldBe ""

        editor.document.text shouldBe "hello [world] test"
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

        editor.document.text shouldBe "val s = {hello}"
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
        editor.document.text shouldBe "val res = (a + b * c)"

        // Delete outer parentheses
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('d', editor)
        HelixKeyHandler.handleKey('(', editor)
        editor.document.text shouldBe "val res = a + b * c"
    }

    fun testSurroundMultiCaret() {
        val text = "(foo) and (bar)"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        val primary = caretModel.primaryCaret
        primary.moveToOffset(text.indexOf("foo"))

        val secondary = caretModel.addCaret(editor.offsetToVisualPosition(text.indexOf("bar")))
        secondary.shouldNotBeNull()

        // Replace '(' with '[' for both carets
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('r', editor)
        HelixKeyHandler.handleKey('(', editor)
        HelixKeyHandler.handleKey('[', editor)

        editor.document.text shouldBe "[foo] and [bar]"

        // Delete '[' for both carets
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('d', editor)
        HelixKeyHandler.handleKey('[', editor)

        editor.document.text shouldBe "foo and bar"
    }

    fun testSurroundAddWithoutSelection() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0) // on 'h'

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('s', editor)
        HelixKeyHandler.handleKey('(', editor)

        editor.document.text shouldBe "(h)ello world"
    }

    fun testSurroundEscapeCancelsPending() {
        myFixture.configureByText("test.txt", "hello (world)")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('r', editor)
        HelixKeyHandler.handleKey('(', editor)
        state.pendingSequence shouldBe "mr("

        myFixture.testAction(jp.titze.intellij.helix.editor.HelixEscapeAction())
        state.pendingSequence shouldBe ""
        editor.document.text shouldBe "hello (world)"
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
        state.pendingSequence shouldBe "m"
        HelixKeyHandler.handleKey('i', editor)
        state.pendingSequence shouldBe "mi"
        HelixKeyHandler.handleKey('w', editor)
        state.pendingSequence shouldBe ""
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "hello"

        // Clear selection
        caret.removeSelection()
        caret.moveToOffset(1)

        // Test maw (select around word - should include trailing space)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('w', editor)
        state.pendingSequence shouldBe ""
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "hello "

        // Test maw on last word of line (should include leading space)
        caret.removeSelection()
        caret.moveToOffset(8) // in "world"
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('w', editor)
        caret.selectedText shouldBe " world"
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
        caret.selectedText shouldBe "first "

        HelixKeyHandler.handleKey('d', editor)
        editor.document.text shouldBe "second third"

        // miw then c
        caret.moveToOffset(2) // in "second"
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('w', editor)
        caret.selectedText shouldBe "second"

        HelixKeyHandler.handleKey('c', editor)
        editor.document.text shouldBe " third"
        HelixStateManager.getOrCreate(editor).mode shouldBe HelixMode.INSERT
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
        caret.selectedText shouldBe "foo.bar(123)"

        // maW selects full token + trailing whitespace
        caret.removeSelection()
        caret.moveToOffset(4)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('W', editor)
        caret.selectedText shouldBe "foo.bar(123) "
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
        caret.selectedText shouldBe "line1\nline2"

        // map: select lines + trailing blank line
        caret.removeSelection()
        caret.moveToOffset(2)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('p', editor)
        caret.selectedText shouldBe "line1\nline2\n\n"
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
        caret.selectedText shouldBe "param"

        // ma": around quotes
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('"', editor)
        caret.selectedText shouldBe "\"param\""

        // mim: closest enclosing pair (the quotes)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('m', editor)
        caret.selectedText shouldBe "param"

        // Move to calculate(
        caret.removeSelection()
        caret.moveToOffset(15) // on "calculate"
        // mi(: inside outer parens
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('i', editor)
        HelixKeyHandler.handleKey('(', editor)
        caret.selectedText shouldBe "calculate(\"param\")"

        // ma(: around outer parens
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('(', editor)
        caret.selectedText shouldBe "(calculate(\"param\"))"
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
        caret.selectedText shouldBe "second"

        // maa: select around argument (including comma and space)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('a', editor)
        caret.selectedText shouldBe "second, "
    }

    fun testTextObjectMultiCaret() {
        val text = "foo alpha bar\nbaz beta qux\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caretModel = editor.caretModel

        val primary = caretModel.primaryCaret
        primary.moveToOffset(text.indexOf("alpha") + 1)

        val secondary = caretModel.addCaret(editor.offsetToVisualPosition(text.indexOf("beta") + 1))
        secondary.shouldNotBeNull()

        // maw across both carets
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('a', editor)
        HelixKeyHandler.handleKey('w', editor)

        primary.selectedText shouldBe "alpha "
        secondary.selectedText shouldBe "beta "

        // Delete selection across both carets
        HelixKeyHandler.handleKey('d', editor)
        editor.document.text shouldBe "foo bar\nbaz qux\n"
    }

    fun testSpaceMenuChords() {
        myFixture.configureByText("test.txt", "sample text for space testing")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Press ' ' begins chord
        HelixKeyHandler.handleKey(' ', editor)
        state.pendingSequence shouldBe " "

        // Second key clears chord and executes handler
        HelixKeyHandler.handleKey('y', editor)
        state.pendingSequence.isEmpty().shouldBeTrue()
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

        editor.document.text shouldBe "hello new_universe end"
        caret.selectedText shouldBe "new_universe"
    }

    fun testWhichKeyChordsSequenceReset() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('g', editor)
        state.pendingSequence shouldBe "g"

        HelixKeyHandler.handleKey('h', editor) // move line start
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testHelixCommandPopupMatchingAndExecution() {
        myFixture.configureByText("test.txt", "line 1\nline 2")
        val editor = myFixture.editor

        // Verify command item matching
        val writeCmd = jp.titze.intellij.helix.command.HelixCommandPopup.COMMANDS.first { it.name == "write" }
        writeCmd.matches("w").shouldBeTrue()
        writeCmd.matches("write").shouldBeTrue()
        writeCmd.matches("unknown_xyz").shouldBeFalse()

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
            dataContext
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
            java.awt.datatransfer.StringSelection("new_universe")
        )

        HelixKeyHandler.handleKey('R', editor)

        editor.document.text shouldBe "hello new_universe end"
        caret.selectedText shouldBe "new_universe"
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
            dataContext
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
            dataContext
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
            java.awt.event.KeyEvent.CHAR_UNDEFINED
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
            java.awt.event.KeyEvent.CHAR_UNDEFINED
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
            java.awt.event.KeyEvent.CHAR_UNDEFINED
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
            java.awt.event.KeyEvent.CHAR_UNDEFINED
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
            java.awt.event.KeyEvent.CHAR_UNDEFINED
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
            java.awt.event.KeyEvent.CHAR_UNDEFINED
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

    fun testSelectRegexWholeBufferPercentS() {
        val text = "apple banana apple orange apple grape"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        // '%' selects entire buffer
        HelixKeyHandler.handleKey('%', editor)
        editor.selectionModel.selectedText shouldBe text

        // 's' select_regex
        val matched = HelixActions.selectRegex(editor, "apple")
        matched.shouldBeTrue()

        val caretModel = editor.caretModel
        caretModel.caretCount shouldBe 3

        val selectedTexts = caretModel.allCarets.map { it.selectedText }
        selectedTexts shouldBe listOf("apple", "apple", "apple")
    }

    fun testSelectRegexWithSubsequentDelete() {
        val text = "foo alpha bar alpha baz alpha"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('%', editor)
        val matched = HelixActions.selectRegex(editor, "alpha")
        matched.shouldBeTrue()

        editor.caretModel.caretCount shouldBe 3

        // 'd' deletes selection across all carets
        HelixKeyHandler.handleKey('d', editor)
        editor.document.text shouldBe "foo  bar  baz "
        editor.caretModel.caretCount shouldBe 3
    }

    fun testSelectRegexPartialSelection() {
        val text = "target line 1\ntarget line 2\nignore target line 3\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        // Select line 1 only with 'x'
        HelixKeyHandler.handleKey('x', editor)
        editor.selectionModel.selectedText shouldBe "target line 1\n"

        // Select "target" inside line 1 selection
        val matched = HelixActions.selectRegex(editor, "target")
        matched.shouldBeTrue()
        editor.caretModel.caretCount shouldBe 1
        editor.caretModel.primaryCaret.selectedText shouldBe "target"
        editor.caretModel.primaryCaret.selectionStart shouldBe 0
    }

    fun testSelectRegexRegexPattern() {
        val text = "item_100, item_200, item_300, other_400"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('%', editor)
        val matched = HelixActions.selectRegex(editor, """item_\d+""")
        matched.shouldBeTrue()

        val carets = editor.caretModel.allCarets
        carets.size shouldBe 3
        carets.map { it.selectedText } shouldBe listOf("item_100", "item_200", "item_300")
    }

    fun testSelectRegexNoMatchKeepsSelection() {
        val text = "foo bar baz"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('%', editor)
        val matched = HelixActions.selectRegex(editor, "not_found")
        matched.shouldBeFalse()
        editor.caretModel.caretCount shouldBe 1
        editor.selectionModel.selectedText shouldBe text
    }

    fun testCountRegexMatches() {
        val text = "abc 123 abc 456 abc"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('%', editor)
        HelixActions.countRegexMatches(editor, "abc") shouldBe 3
        HelixActions.countRegexMatches(editor, """\d+""") shouldBe 2
        HelixActions.countRegexMatches(editor, "zzz") shouldBe 0
    }

    fun testSplitSelection() {
        val text = "apple,banana,orange,grape"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('%', editor)
        val success = HelixActions.splitSelection(editor, ",")
        success.shouldBeTrue()

        val carets = editor.caretModel.allCarets
        carets.size shouldBe 4
        carets.map { it.selectedText } shouldBe listOf("apple", "banana", "orange", "grape")
    }

    fun testForwardSearchAndNext() {
        val text = "apple orange apple banana apple"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // Forward search for "apple"
        val found = HelixActions.search(editor, "apple", backward = false)
        found.shouldBeTrue()
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 0
        caret.selectionEnd shouldBe 5

        // 'n' -> next match
        HelixKeyHandler.handleKey('n', editor)
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 13
        caret.selectionEnd shouldBe 18

        // 'n' -> next match
        HelixKeyHandler.handleKey('n', editor)
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 26
        caret.selectionEnd shouldBe 31

        // 'n' -> wrap around to first match
        HelixKeyHandler.handleKey('n', editor)
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 0
        caret.selectionEnd shouldBe 5

        // 'N' -> reverse direction back to last match
        HelixKeyHandler.handleKey('N', editor)
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 26
        caret.selectionEnd shouldBe 31

        // 'n' after 'N' must continue in original forward direction (wrap around to first match)
        HelixKeyHandler.handleKey('n', editor)
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 0
        caret.selectionEnd shouldBe 5
    }

    fun testBackwardSearchAndPrev() {
        val text = "apple orange apple banana apple"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(text.length)

        // Backward search for "apple"
        val found = HelixActions.search(editor, "apple", backward = true)
        found.shouldBeTrue()
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 26
        caret.selectionEnd shouldBe 31

        // 'n' repeats in same direction (backward)
        HelixKeyHandler.handleKey('n', editor)
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 13
        caret.selectionEnd shouldBe 18

        // 'N' reverses direction (forward)
        HelixKeyHandler.handleKey('N', editor)
        caret.selectedText shouldBe "apple"
        caret.selectionStart shouldBe 26
        caret.selectionEnd shouldBe 31
    }

    fun testSearchWordUnderCursorAsterisk() {
        val text = "hello world hello universe"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(1) // on "hello"

        HelixKeyHandler.handleKey('*', editor)
        // Moves to next occurrence of "hello"
        caret.selectedText shouldBe "hello"
        caret.selectionStart shouldBe 12
        caret.selectionEnd shouldBe 17
    }

    fun testCountDocumentRegexMatches() {
        val text = "foo 123 foo 456 foo"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        HelixActions.countDocumentRegexMatches(editor, "foo") shouldBe 3
        HelixActions.countDocumentRegexMatches(editor, """\d+""") shouldBe 2
        HelixActions.countDocumentRegexMatches(editor, "nonexistent") shouldBe 0
    }

    fun testCopySelectionOnNextLineSingleCaret() {
        val text = "hello\nworld\ntest"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(1) // 'e' on line 0

        val handled = HelixKeyHandler.handleKey('C', editor)
        handled.shouldBeTrue()
        editor.caretModel.caretCount shouldBe 2

        val carets = editor.caretModel.allCarets.sortedBy { it.offset }
        carets[0].offset shouldBe 1 // 'e' on line 0
        carets[1].offset shouldBe 7 // 'o' on line 1 ("world")
        carets[1] shouldBe editor.caretModel.primaryCaret
    }

    fun testCopySelectionOnNextLineWithSelection() {
        val text = "val foo = 1\nval bar = 2\nval baz = 3"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(7)
        caret.setSelection(4, 7) // "foo"

        val handled = HelixKeyHandler.handleKey('C', editor)
        handled.shouldBeTrue()
        editor.caretModel.caretCount shouldBe 2

        val carets = editor.caretModel.allCarets.sortedBy { it.offset }
        carets[0].selectedText shouldBe "foo"
        carets[1].selectedText shouldBe "bar"
        editor.caretModel.primaryCaret shouldBe carets[1]
    }

    fun testCopySelectionOnNextLineWithCount() {
        val text = "line 0\nline 1\nline 2\nline 3"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(2)

        HelixKeyHandler.handleKey('3', editor)
        HelixKeyHandler.handleKey('C', editor)

        editor.caretModel.caretCount shouldBe 4
        val carets = editor.caretModel.allCarets.sortedBy { it.offset }
        for (i in 0..3) {
            val lineStart = editor.document.getLineStartOffset(i)
            carets[i].offset shouldBe lineStart + 2
        }
    }

    fun testCopySelectionOnPrevLine() {
        val text = "line 0\nline 1\nline 2"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val line2Start = editor.document.getLineStartOffset(2)
        caret.moveToOffset(line2Start + 3)

        HelixMotions.copySelectionOnPrevLine(editor, 1)
        editor.caretModel.caretCount shouldBe 2

        val carets = editor.caretModel.allCarets.sortedBy { it.offset }
        val line1Start = editor.document.getLineStartOffset(1)
        carets[0].offset shouldBe line1Start + 3
        carets[1].offset shouldBe line2Start + 3
        editor.caretModel.primaryCaret shouldBe carets[0]
    }

    fun testRemovePrimarySelection() {
        val text = "line 0\nline 1\nline 2"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)
        HelixMotions.copySelectionOnNextLine(editor, 1)
        editor.caretModel.caretCount shouldBe 2

        HelixMotions.removePrimarySelection(editor)
        editor.caretModel.caretCount shouldBe 1
    }

    fun testRotateSelectionsForwardAndBackward() {
        val text = "line 0\nline 1\nline 2"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        // Create 3 carets: line 0, 1, 2
        HelixKeyHandler.handleKey('2', editor)
        HelixKeyHandler.handleKey('C', editor)
        editor.caretModel.caretCount shouldBe 3

        val line0Offset = editor.document.getLineStartOffset(0)
        val line1Offset = editor.document.getLineStartOffset(1)
        val line2Offset = editor.document.getLineStartOffset(2)

        // Currently primary is on line 2 (bottom one)
        editor.caretModel.primaryCaret.offset shouldBe line2Offset

        // Rotate forward: line 2 -> line 0
        HelixKeyHandler.handleKey(')', editor)
        editor.caretModel.primaryCaret.offset shouldBe line0Offset

        // Rotate forward: line 0 -> line 1
        HelixKeyHandler.handleKey(')', editor)
        editor.caretModel.primaryCaret.offset shouldBe line1Offset

        // Rotate backward: line 1 -> line 0
        HelixKeyHandler.handleKey('(', editor)
        editor.caretModel.primaryCaret.offset shouldBe line0Offset

        // Rotate backward: line 0 -> line 2
        HelixKeyHandler.handleKey('(', editor)
        editor.caretModel.primaryCaret.offset shouldBe line2Offset
    }

    fun testSplitSelectionOnNewlines() {
        val text = "hello\nworld\nagain"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        // Select all
        HelixMotions.selectAll(editor)
        editor.caretModel.primaryCaret.selectedText shouldBe text

        HelixMotions.splitSelectionOnNewlines(editor)
        editor.caretModel.caretCount shouldBe 3

        val carets = editor.caretModel.allCarets.sortedBy { it.offset }
        carets[0].selectedText shouldBe "hello"
        carets[1].selectedText shouldBe "world"
        carets[2].selectedText shouldBe "again"
    }

    fun testFlipSelection() {
        val text = "hello world"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(5)
        caret.setSelection(0, 5) // anchor 0, cursor 5

        caret.leadSelectionOffset shouldBe 0
        caret.offset shouldBe 5

        HelixMotions.flipSelection(editor)
        caret.leadSelectionOffset shouldBe 5
        caret.offset shouldBe 0

        HelixMotions.flipSelection(editor)
        caret.leadSelectionOffset shouldBe 0
        caret.offset shouldBe 5
    }

    fun testHelixSettingsSearchUiMode() {
        val settings = HelixSettings.instance
        val original = settings.searchUiMode

        try {
            settings.searchUiMode = HelixSearchUiMode.STOCK_HELIX
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX

            settings.searchUiMode = HelixSearchUiMode.POPUP
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP
        } finally {
            settings.searchUiMode = original
        }
    }

    fun testHelixCommandToggleSearchUi() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor
        val settings = HelixSettings.instance
        val original = settings.searchUiMode

        try {
            settings.searchUiMode = HelixSearchUiMode.STOCK_HELIX
            HelixCommandPopup.executeCommand("toggle-search-ui", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP

            HelixCommandPopup.executeCommand("toggle-search-ui", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX

            HelixCommandPopup.executeCommand("set search-ui=popup", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP

            HelixCommandPopup.executeCommand("set search-ui=inline", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX
        } finally {
            settings.searchUiMode = original
        }
    }

    fun testCaretSnapshotAndRestore() {
        myFixture.configureByText("test.txt", "alpha beta gamma delta")
        val editor = myFixture.editor

        // Set primary caret selection
        editor.caretModel.primaryCaret.moveToOffset(5)
        editor.caretModel.primaryCaret.setSelection(0, 5) // "alpha"

        val snapshot = HelixActions.captureCarets(editor)
        snapshot.size shouldBe 1
        snapshot[0].offset shouldBe 5
        snapshot[0].selectionStart shouldBe 0
        snapshot[0].selectionEnd shouldBe 5

        // Move caret elsewhere
        editor.caretModel.primaryCaret.moveToOffset(15)
        editor.caretModel.primaryCaret.removeSelection()
        editor.caretModel.primaryCaret.offset shouldBe 15
        editor.caretModel.primaryCaret.hasSelection().shouldBeFalse()

        // Restore snapshot
        HelixActions.restoreCarets(editor, snapshot)
        editor.caretModel.primaryCaret.offset shouldBe 5
        editor.caretModel.primaryCaret.selectedText shouldBe "alpha"
    }

    fun testIncrementalSearchPreviewAndCancel() {
        val text = "apple banana apple orange apple"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        val snapshot = HelixActions.captureCarets(editor)

        // Live preview typing "banana"
        val found = HelixActions.previewSearch(editor, "banana", backward = false, count = 1, baseSnapshot = snapshot)
        found.shouldBeTrue()
        caret.selectedText shouldBe "banana"
        caret.selectionStart shouldBe 6
        caret.selectionEnd shouldBe 12

        // Cancel / revert (simulating Esc)
        HelixActions.restoreCarets(editor, snapshot)
        caret.offset shouldBe 0
        caret.hasSelection().shouldBeFalse()
    }

    fun testIncrementalSelectRegexPreviewAndCancel() {
        val text = "val a = 123; val b = 456;"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Select full line
        caret.setSelection(0, text.length)
        val snapshot = HelixActions.captureCarets(editor)

        // Incremental regex select \d+
        val found = HelixActions.previewSelectRegex(editor, """\d+""", baseSnapshot = snapshot)
        found.shouldBeTrue()
        editor.caretModel.caretCount shouldBe 2
        val carets = editor.caretModel.allCarets.sortedBy { it.offset }
        carets[0].selectedText shouldBe "123"
        carets[1].selectedText shouldBe "456"

        // Cancel / revert (simulating Esc)
        HelixActions.restoreCarets(editor, snapshot)
        editor.caretModel.caretCount shouldBe 1
        editor.caretModel.primaryCaret.selectedText shouldBe text
    }

    fun testIncrementalSplitRegexPreviewAndCancel() {
        val text = "foo,bar,baz"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Select full text
        caret.setSelection(0, text.length)
        val snapshot = HelixActions.captureCarets(editor)

        // Incremental split on comma
        val found = HelixActions.previewSplitRegex(editor, ",", baseSnapshot = snapshot)
        found.shouldBeTrue()
        editor.caretModel.caretCount shouldBe 3
        val carets = editor.caretModel.allCarets.sortedBy { it.offset }
        carets[0].selectedText shouldBe "foo"
        carets[1].selectedText shouldBe "bar"
        carets[2].selectedText shouldBe "baz"

        // Cancel / revert (simulating Esc)
        HelixActions.restoreCarets(editor, snapshot)
        editor.caretModel.caretCount shouldBe 1
        editor.caretModel.primaryCaret.selectedText shouldBe text
    }

    fun testPromptBarComponentCreation() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor

        val bar = HelixPromptBar.getOrCreate(editor)
        bar.shouldNotBeNull()
        HelixPromptBar.getOrCreate(editor) shouldBeSameInstanceAs bar

        bar.show(HelixPromptType.SEARCH)
        bar.isVisible.shouldBeTrue()

        bar.cancelAndClose()
        bar.isVisible.shouldBeFalse()
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
        val secondCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(doc.getLineStartOffset(1) + 7), false)
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

    fun testHelixWhichKeyPopupPositionCalculation() {
        // Normal dimensions: 1000x800 viewport, 300x200 popup, margin 20
        val pos = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 1000,
            viewHeight = 800,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 0,
            visibleY = 0,
            marginX = 20,
            marginY = 20
        )
        pos.x shouldBe 680 // 1000 - 300 - 20
        pos.y shouldBe 580 // 800 - 200 - 20

        // With viewport offset: visibleX = 50, visibleY = 100
        val posWithOffset = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 1000,
            viewHeight = 800,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 50,
            visibleY = 100,
            marginX = 20,
            marginY = 20
        )
        posWithOffset.x shouldBe 730 // 50 + 680
        posWithOffset.y shouldBe 680 // 100 + 580

        // Very small viewport (smaller than popup) clamps to 0 offset
        val posClamped = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 200,
            viewHeight = 150,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 0,
            visibleY = 0,
            marginX = 20,
            marginY = 20
        )
        posClamped.x shouldBe 0
        posClamped.y shouldBe 0
    }

    fun testHelixWhichKeyPopupTriggerAndHide() {
        myFixture.configureByText("test.txt", "line 1\nline 2\n")
        val editor = myFixture.editor

        // Typing space or g should trigger which-key show safely without throwing
        HelixWhichKeyPopup.show(editor, " ")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "g")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "m")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "[")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "]")
        HelixWhichKeyPopup.hide()
    }

    fun testParagraphNavigationForwardAndBackward() {
        val text = "p1 line 1\np1 line 2\n\np2 line 1\np2 line 2\n\np3 line 1\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val doc = editor.document
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)

        // Move to next paragraph (blank line between p1 and p2)
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('p', editor).shouldBeTrue()
        val blankLine1 = doc.getLineStartOffset(2)
        caret.offset shouldBe blankLine1

        // Move to next paragraph (blank line between p2 and p3)
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('p', editor).shouldBeTrue()
        val blankLine2 = doc.getLineStartOffset(5)
        caret.offset shouldBe blankLine2

        // Move backward to previous paragraph
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('p', editor).shouldBeTrue()
        caret.offset shouldBe blankLine1
    }

    fun testAddNewlineAboveAndBelow() {
        val text = "line 1\nline 2\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val doc = editor.document
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(2) // in "line 1"

        // Add newline below: ] Space
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()

        doc.text shouldBe "line 1\n\nline 2\n"
        doc.getLineNumber(caret.offset) shouldBe 0

        // Add newline above: [ Space
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()

        doc.text shouldBe "\nline 1\n\nline 2\n"
        doc.getLineNumber(caret.offset) shouldBe 1
    }

    fun testCommentNavigation() {
        val text = "val a = 1\n// first comment\nval b = 2\n// second comment\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)

        // Jump to first comment
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('c', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("// first comment")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "// first comment"

        // Jump to second comment
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('c', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("// second comment")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "// second comment"

        // Jump back to first comment
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('c', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("// first comment")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "// first comment"
    }

    fun testFunctionAndClassNavigation() {
        val text = "class Foo {\n    fun bar() {}\n    fun baz() {}\n}\nclass Second {\n}\n"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)

        // Jump forward to first function
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("fun bar")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "fun bar() {}"

        // Jump forward to second function
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("fun baz")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "fun baz() {}"

        // Jump backward to first function
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("fun bar")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "fun bar() {}"

        // Jump forward to next class
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("class Second")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "class Second {\n}"

        // Jump backward to first class
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("class Foo")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "class Foo {\n    fun bar() {}\n    fun baz() {}\n}"
    }
}

