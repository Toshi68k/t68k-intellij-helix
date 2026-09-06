package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.keymap.HelixKeyHandler

class HelixCodeNavigationTest : BasePlatformTestCase() {

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

    fun testFunctionNavigationWithLambdasAndBlocks() {
        val text = "fun first() {\n    val f = { x: Int -> x * 2 }\n" +
            "    items.forEach { println(it) }\n}\n\n" +
            "fun second() {\n    val g = { y: Int -> y + 1 }\n}\n"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)

        // Jump forward to first()
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("fun first")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe
            "fun first() {\n    val f = { x: Int -> x * 2 }\n    items.forEach { println(it) }\n}"

        // Jump forward to second() - must NOT select lambdas inside first()
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("fun second")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "fun second() {\n    val g = { y: Int -> y + 1 }\n}"

        // Jump backward to first() - must NOT select lambdas inside second() or first()
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("fun first")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe
            "fun first() {\n    val f = { x: Int -> x * 2 }\n    items.forEach { println(it) }\n}"
    }

    fun testObjectAndEnclosingClassNavigation() {
        val text = "// Header\n\nobject Singleton {\n    fun hello() {}\n}\n\nclass Other {\n    fun world() {}\n}\n"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Put cursor inside Singleton on "fun hello"
        caret.moveToOffset(text.indexOf("fun hello"))

        // [t from inside Singleton should select Singleton!
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("object Singleton")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "object Singleton {\n    fun hello() {}\n}"

        // ]t from Singleton should jump to Other
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("class Other")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "class Other {\n    fun world() {}\n}"

        // [t from Other should jump back to Singleton
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("object Singleton")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "object Singleton {\n    fun hello() {}\n}"
    }

    fun testDestructuringDeclarationNotMatchedAsClass() {
        val text = "class MyProcessor {\n    fun process(rangesToSearch: List<Pair<Int, Int>>) {\n" +
            "        for ((rangeStart, rangeEnd) in rangesToSearch) {\n" +
            "            println(rangeStart)\n        }\n    }\n}\n"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        // Put cursor on the for-loop line inside the class
        caret.moveToOffset(text.indexOf("for ((rangeStart"))

        // [t should select the enclosing MyProcessor, NOT (rangeStart, rangeEnd)!
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("class MyProcessor")
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe text.trimEnd()
    }

    fun testMatchBracketsParentheses() {
        val text = "val x = (1 + 2)"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val openIdx = text.indexOf('(')
        val closeIdx = text.indexOf(')')

        caret.moveToOffset(openIdx)
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe closeIdx

        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe openIdx
    }

    fun testMatchBracketsBraces() {
        val text = "fun foo() { println(42) }"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val openIdx = text.indexOf('{')
        val closeIdx = text.indexOf('}')

        caret.moveToOffset(openIdx)
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe closeIdx

        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe openIdx
    }

    fun testMatchBracketsSquareBrackets() {
        val text = "val list = [1, 2, 3]"
        myFixture.configureByText("test.py", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val openIdx = text.indexOf('[')
        val closeIdx = text.indexOf(']')

        caret.moveToOffset(openIdx)
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe closeIdx

        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe openIdx
    }

    fun testMatchBracketsAngleBrackets() {
        val text = "val map = Map<String, Int>()"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val openIdx = text.indexOf('<')
        val closeIdx = text.indexOf('>')

        caret.moveToOffset(openIdx)
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe closeIdx

        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe openIdx
    }

    fun testMatchBracketsInSelectMode() {
        val text = "(hello world)"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)
        HelixActions.toggleSelectMode(editor)

        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()

        caret.offset shouldBe 13
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "(hello world)"
    }

    fun testMatchBracketsInSelectModeBackward() {
        val text = "(hello world)"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(12) // on ')'
        HelixActions.toggleSelectMode(editor)

        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()

        caret.offset shouldBe 0
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "(hello world)"
    }

    fun testMatchBracketsInSelectModeToggle() {
        val text = "(hello world)"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)
        HelixActions.toggleSelectMode(editor)

        // Forward to ')'
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe 13
        caret.selectedText shouldBe "(hello world)"

        // Backward to '('
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        caret.offset shouldBe 0
        caret.selectedText shouldBe "(hello world)"
    }

    fun testMatchBracketsMultiCaret() {
        val text = "(first) + (second)"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor

        val primary = editor.caretModel.primaryCaret
        primary.moveToOffset(0) // at (first)
        val secondCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(10)) // at (second)
        secondCaret.shouldNotBeNull()

        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', editor).shouldBeTrue()

        primary.offset shouldBe 6 // at closing ) of (first)
        secondCaret.offset shouldBe 17 // at closing ) of (second)
    }
}
