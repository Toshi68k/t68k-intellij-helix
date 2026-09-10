package jp.titze.intellij.helix

import com.intellij.openapi.editor.CaretState
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.motion.HelixMotions

class HelixSelectionGroomingTest : BasePlatformTestCase() {

    fun testTrimSelectionsKeyHandler() {
        myFixture.configureByText("test.txt", "   hello   \n  world  \n   pure   ")
        val editor = myFixture.editor

        val states = listOf(
            CaretState(
                editor.offsetToLogicalPosition(11),
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(11),
            ),
            CaretState(
                editor.offsetToLogicalPosition(21),
                editor.offsetToLogicalPosition(12),
                editor.offsetToLogicalPosition(21),
            ),
        )
        editor.caretModel.setCaretsAndSelections(states)
        editor.caretModel.caretCount shouldBe 2

        HelixKeyHandler.handleKey('_', editor)

        val carets = editor.caretModel.allCarets.sortedBy { it.selectionStart }
        carets[0].selectedText shouldBe "hello"
        carets[0].selectionStart shouldBe 3
        carets[0].selectionEnd shouldBe 8

        carets[1].selectedText shouldBe "world"
        carets[1].selectionStart shouldBe 14
        carets[1].selectionEnd shouldBe 19
    }

    fun testTrimWhitespaceOnlySelectionCollapses() {
        myFixture.configureByText("test.txt", "abc   def")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(6)
        caret.setSelection(3, 6)
        caret.hasSelection().shouldBeTrue()

        HelixActions.trimSelections(editor)

        caret.hasSelection().shouldBeFalse()
        caret.offset shouldBe 3
    }

    fun testTrimBackwardSelectionPreservesOrientation() {
        myFixture.configureByText("test.txt", "   hello   ")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        // Backward: cursor at 0, anchor at 11
        caret.moveToOffset(0)
        caret.setSelection(11, 0)
        (caret.offset < caret.leadSelectionOffset).shouldBeTrue()

        HelixActions.trimSelections(editor)

        caret.selectedText shouldBe "hello"
        caret.selectionStart shouldBe 3
        caret.selectionEnd shouldBe 8
        caret.offset shouldBe 3
        caret.leadSelectionOffset shouldBe 8
    }

    fun testAlignSelectionsKeyHandler() {
        val original = """
            let a = 1;
            let fooBar = 2;
            let z = 3;
        """.trimIndent()
        myFixture.configureByText("test.txt", original)
        val editor = myFixture.editor
        val doc = editor.document

        // Select the '=' on each line
        val states = listOf(0, 1, 2).map { line ->
            val lineStart = doc.getLineStartOffset(line)
            val lineEnd = doc.getLineEndOffset(line)
            val text = doc.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
            val eqIdx = lineStart + text.indexOf('=')
            CaretState(
                editor.offsetToLogicalPosition(eqIdx + 1),
                editor.offsetToLogicalPosition(eqIdx),
                editor.offsetToLogicalPosition(eqIdx + 1),
            )
        }
        editor.caretModel.setCaretsAndSelections(states)
        editor.caretModel.caretCount shouldBe 3

        HelixKeyHandler.handleKey('&', editor)

        val newLines = editor.document.text.lines()
        val eqCols = newLines.map { it.indexOf('=') }
        eqCols[0] shouldBe eqCols[1]
        eqCols[1] shouldBe eqCols[2]
        eqCols[0] shouldBe 11

        val carets = editor.caretModel.allCarets.sortedBy { it.selectionStart }
        carets.forEach {
            it.selectedText shouldBe "="
        }
    }

    fun testKeepSelectionsRegex() {
        myFixture.configureByText("test.txt", "apple 123 banana 456")
        val editor = myFixture.editor
        val states = listOf(
            CaretState(
                editor.offsetToLogicalPosition(5),
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(5),
            ),
            CaretState(
                editor.offsetToLogicalPosition(9),
                editor.offsetToLogicalPosition(6),
                editor.offsetToLogicalPosition(9),
            ),
            CaretState(
                editor.offsetToLogicalPosition(16),
                editor.offsetToLogicalPosition(10),
                editor.offsetToLogicalPosition(16),
            ),
        )
        editor.caretModel.setCaretsAndSelections(states)
        editor.caretModel.caretCount shouldBe 3

        HelixActions.filterSelections(editor, """\d+""", keepMatching = true)

        val carets = editor.caretModel.allCarets
        carets shouldHaveSize 1
        carets[0].selectedText shouldBe "123"
    }

    fun testRemoveSelectionsRegex() {
        myFixture.configureByText("test.txt", "apple 123 banana")
        val editor = myFixture.editor
        val states = listOf(
            CaretState(
                editor.offsetToLogicalPosition(5),
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(5),
            ),
            CaretState(
                editor.offsetToLogicalPosition(9),
                editor.offsetToLogicalPosition(6),
                editor.offsetToLogicalPosition(9),
            ),
            CaretState(
                editor.offsetToLogicalPosition(16),
                editor.offsetToLogicalPosition(10),
                editor.offsetToLogicalPosition(16),
            ),
        )
        editor.caretModel.setCaretsAndSelections(states)
        editor.caretModel.caretCount shouldBe 3

        HelixActions.filterSelections(editor, """\d+""", keepMatching = false)

        val carets = editor.caretModel.allCarets.sortedBy { it.selectionStart }
        carets shouldHaveSize 2
        carets[0].selectedText shouldBe "apple"
        carets[1].selectedText shouldBe "banana"
    }

    fun testEnsureSelectionsForward() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)
        caret.setSelection(5, 0)
        (caret.offset < caret.leadSelectionOffset).shouldBeTrue()

        HelixActions.ensureSelectionsForward(editor)

        caret.offset shouldBe 5
        caret.leadSelectionOffset shouldBe 0
        caret.selectionStart shouldBe 0
        caret.selectionEnd shouldBe 5
        (caret.offset >= caret.leadSelectionOffset).shouldBeTrue()
    }

    fun testMergeConsecutiveSelections() {
        myFixture.configureByText("test.txt", "0123456789abcdefgh")
        val editor = myFixture.editor
        val states = listOf(
            CaretState(
                editor.offsetToLogicalPosition(4),
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(4),
            ),
            CaretState(
                editor.offsetToLogicalPosition(8),
                editor.offsetToLogicalPosition(4),
                editor.offsetToLogicalPosition(8),
            ),
            CaretState(
                editor.offsetToLogicalPosition(16),
                editor.offsetToLogicalPosition(12),
                editor.offsetToLogicalPosition(16),
            ),
        )
        editor.caretModel.setCaretsAndSelections(states)
        editor.caretModel.caretCount shouldBe 3

        HelixActions.mergeSelections(editor)

        val carets = editor.caretModel.allCarets.sortedBy { it.selectionStart }
        carets shouldHaveSize 2
        carets[0].selectionStart shouldBe 0
        carets[0].selectionEnd shouldBe 8
        carets[0].selectedText shouldBe "01234567"

        carets[1].selectionStart shouldBe 12
        carets[1].selectionEnd shouldBe 16
        carets[1].selectedText shouldBe "cdef"
    }

    fun testRotateSelectionsContentsForwardAndBackward() {
        myFixture.configureByText("test.txt", "alpha ... beta ... gamma")
        val editor = myFixture.editor
        val states = listOf(
            CaretState(
                editor.offsetToLogicalPosition(5),
                editor.offsetToLogicalPosition(0),
                editor.offsetToLogicalPosition(5),
            ),
            CaretState(
                editor.offsetToLogicalPosition(14),
                editor.offsetToLogicalPosition(10),
                editor.offsetToLogicalPosition(14),
            ),
            CaretState(
                editor.offsetToLogicalPosition(24),
                editor.offsetToLogicalPosition(19),
                editor.offsetToLogicalPosition(24),
            ),
        )
        editor.caretModel.setCaretsAndSelections(states)

        // Rotate forward: alpha -> gamma, beta -> alpha, gamma -> beta
        HelixActions.rotateSelectionsContents(editor, forward = true)
        editor.document.text shouldBe "gamma ... alpha ... beta"

        val caretsFwd = editor.caretModel.allCarets.sortedBy { it.selectionStart }
        caretsFwd[0].selectedText shouldBe "gamma"
        caretsFwd[1].selectedText shouldBe "alpha"
        caretsFwd[2].selectedText shouldBe "beta"

        // Rotate backward: returns to original
        HelixActions.rotateSelectionsContents(editor, forward = false)
        editor.document.text shouldBe "alpha ... beta ... gamma"
        val caretsBack = editor.caretModel.allCarets.sortedBy { it.selectionStart }
        caretsBack[0].selectedText shouldBe "alpha"
        caretsBack[1].selectedText shouldBe "beta"
        caretsBack[2].selectedText shouldBe "gamma"
    }

    fun testExtendToLineBoundsAndShrinkToLineBounds() {
        val text = "first line\nsecond line\nthird line\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        val caret = editor.caretModel.primaryCaret
        // Place cursor inside "second"
        caret.moveToOffset(14)

        HelixKeyHandler.handleKey('X', editor)
        caret.selectionStart shouldBe 11
        caret.selectionEnd shouldBe 23
        caret.selectedText shouldBe "second line\n"

        HelixMotions.shrinkToLineBounds(editor)
        caret.selectionStart shouldBe 11
        caret.selectionEnd shouldBe 22
        caret.selectedText shouldBe "second line"
    }

    fun testExtendToLineBoundsWithCount() {
        val text = "first line\nsecond line\nthird line\nfourth line\n"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(14)

        HelixKeyHandler.handleKey('2', editor)
        HelixKeyHandler.handleKey('X', editor)
        caret.selectionStart shouldBe 11
        caret.selectionEnd shouldBe 34
        caret.selectedText shouldBe "second line\nthird line\n"
    }

    fun testCommandPaletteExecution() {
        myFixture.configureByText("test.txt", "  test  ")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(8)
        caret.setSelection(0, 8)

        HelixCommands.execute("trim-selections", editor)
        caret.selectedText shouldBe "test"

        HelixCommands.execute("extend-to-line-bounds", editor)
        caret.selectionStart shouldBe 0
        caret.selectionEnd shouldBe 8
    }
}
