package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixMotions
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

class HelixSelectionTest : BasePlatformTestCase() {
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
}
