// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.keymap.HelixKeyHandler

class HelixCodeNavigationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        jp.titze.intellij.helix.register.HelixRegisterManager.clear()
        jp.titze.intellij.helix.motion.HelixMotionHistory.lastMotion = null
        jp.titze.intellij.helix.editor.HelixInsertTracker.reset()
        jp.titze.intellij.helix.motion.HelixJumpMotions.changeRangesProvider = null
    }

    override fun tearDown() {
        jp.titze.intellij.helix.register.HelixRegisterManager.clear()
        jp.titze.intellij.helix.motion.HelixMotionHistory.lastMotion = null
        jp.titze.intellij.helix.editor.HelixInsertTracker.reset()
        jp.titze.intellij.helix.motion.HelixJumpMotions.changeRangesProvider = null
        super.tearDown()
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

    fun testGotoNextAndPreviousBuffer() {
        val fileA = myFixture.addFileToProject("FileA.txt", "content A")
        val fileB = myFixture.addFileToProject("FileB.txt", "content B")
        val fileC = myFixture.addFileToProject("FileC.txt", "content C")

        myFixture.openFileInEditor(fileA.virtualFile)
        myFixture.openFileInEditor(fileB.virtualFile)
        myFixture.openFileInEditor(fileC.virtualFile)

        val fem = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        fem.selectedFiles.first().name shouldBe "FileC.txt"

        val bufferOrder = fem.openFiles.map { it.name }
        (bufferOrder.size >= 3).shouldBeTrue()
        val indexC = bufferOrder.indexOf("FileC.txt")
        val expectedNext1 = bufferOrder[(indexC + 1) % bufferOrder.size]
        val expectedNext2 = bufferOrder[(indexC + 2) % bufferOrder.size]
        val expectedPrev1 = bufferOrder[Math.floorMod(indexC - 1, bufferOrder.size)]

        // gn advances to expectedNext1
        val editorC = fem.selectedTextEditor ?: myFixture.editor
        HelixKeyHandler.handleKey('g', editorC).shouldBeTrue()
        HelixKeyHandler.handleKey('n', editorC).shouldBeTrue()
        fem.selectedFiles.first().name shouldBe expectedNext1

        // gn advances to expectedNext2
        val editorNext1 = fem.selectedTextEditor ?: myFixture.editor
        HelixKeyHandler.handleKey('g', editorNext1).shouldBeTrue()
        HelixKeyHandler.handleKey('n', editorNext1).shouldBeTrue()
        fem.selectedFiles.first().name shouldBe expectedNext2

        // gp goes backwards to expectedNext1
        val editorNext2 = fem.selectedTextEditor ?: myFixture.editor
        HelixKeyHandler.handleKey('g', editorNext2).shouldBeTrue()
        HelixKeyHandler.handleKey('p', editorNext2).shouldBeTrue()
        fem.selectedFiles.first().name shouldBe expectedNext1

        // ]b advances forward to expectedNext2
        val editorNext1Again = fem.selectedTextEditor ?: myFixture.editor
        HelixKeyHandler.handleKey(']', editorNext1Again).shouldBeTrue()
        HelixKeyHandler.handleKey('b', editorNext1Again).shouldBeTrue()
        fem.selectedFiles.first().name shouldBe expectedNext2

        // [b goes backwards to expectedNext1
        val editorNext2Again = fem.selectedTextEditor ?: myFixture.editor
        HelixKeyHandler.handleKey('[', editorNext2Again).shouldBeTrue()
        HelixKeyHandler.handleKey('b', editorNext2Again).shouldBeTrue()
        fem.selectedFiles.first().name shouldBe expectedNext1

        // Count prefix: 2gn jumps 2 positions forward
        val currentIndex = bufferOrder.indexOf(fem.selectedFiles.first().name)
        val expectedAfter2 = bufferOrder[(currentIndex + 2) % bufferOrder.size]
        val currentEditor = fem.selectedTextEditor ?: myFixture.editor
        HelixKeyHandler.handleKey('2', currentEditor).shouldBeTrue()
        HelixKeyHandler.handleKey('g', currentEditor).shouldBeTrue()
        HelixKeyHandler.handleKey('n', currentEditor).shouldBeTrue()
        fem.selectedFiles.first().name shouldBe expectedAfter2
    }

    fun testGotoNextAndPreviousBufferSingleFile() {
        myFixture.configureByText("OnlyFile.txt", "content")
        val editor = myFixture.editor

        // With only 1 open buffer, gn/gp return without error and the file remains active
        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('n', editor).shouldBeTrue()
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.first().name shouldBe
            "OnlyFile.txt"

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('p', editor).shouldBeTrue()
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.first().name shouldBe
            "OnlyFile.txt"
    }

    fun testGotoLastEditLocation() {
        myFixture.configureByText("test.txt", "test content")
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('.', editor).shouldBeTrue()
    }

    fun testVisualLineMotionsGjAndGk() {
        myFixture.configureByText("test.txt", "line 1\nline 2\nline 3\n")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0)
        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('j', editor).shouldBeTrue()
        caret.visualPosition.line shouldBe 1

        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('k', editor).shouldBeTrue()
        caret.visualPosition.line shouldBe 0

        // In Select mode, gj extends selection
        HelixActions.toggleSelectMode(editor)
        HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('j', editor).shouldBeTrue()
        caret.hasSelection().shouldBeTrue()
    }

    fun testGotoLastAccessedFileGa() {
        val fileA = myFixture.addFileToProject("FileA.txt", "content A")
        val fileB = myFixture.addFileToProject("FileB.txt", "content B")

        myFixture.openFileInEditor(fileA.virtualFile)
        myFixture.openFileInEditor(fileB.virtualFile)

        jp.titze.intellij.helix.motion.HelixFileNavigation.recordFileAccess(
            project,
            fileA.virtualFile,
            fileB.virtualFile,
        )

        val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)

        // ga switches from fileB to fileA
        val editorB = fileEditorManager.selectedTextEditor ?: myFixture.editor
        HelixKeyHandler.handleKey('g', editorB).shouldBeTrue()
        HelixKeyHandler.handleKey('a', editorB).shouldBeTrue()
        fileEditorManager.selectedFiles.first().name shouldBe "FileA.txt"

        // ga switches back from fileA to fileB
        val editorA = fileEditorManager.selectedTextEditor ?: myFixture.editor
        HelixKeyHandler.handleKey('g', editorA).shouldBeTrue()
        HelixKeyHandler.handleKey('a', editorA).shouldBeTrue()
        fileEditorManager.selectedFiles.first().name shouldBe "FileB.txt"

        jp.titze.intellij.helix.motion.HelixFileNavigation.reset()
    }

    fun testGotoLastModifiedFileGm() {
        val fileA = myFixture.addFileToProject("ModA.txt", "content A")
        val fileB = myFixture.addFileToProject("ModB.txt", "content B")

        myFixture.openFileInEditor(fileA.virtualFile)
        myFixture.openFileInEditor(fileB.virtualFile)

        jp.titze.intellij.helix.motion.HelixFileNavigation.recordFileModified(project, fileA.virtualFile)

        // From fileB, gm jumps to fileA
        HelixKeyHandler.handleKey('g', myFixture.editor).shouldBeTrue()
        HelixKeyHandler.handleKey('m', myFixture.editor).shouldBeTrue()
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.first().name shouldBe
            "ModA.txt"

        jp.titze.intellij.helix.motion.HelixFileNavigation.reset()
    }

    fun testGotoDeclarationGD() {
        myFixture.configureByText("Test.kt", "class Foo { val x = 1 }")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }
        try {
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('D', editor).shouldBeTrue()
            executedActions shouldBe listOf("GotoDeclarationOnly")
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testGotoDeclarationGDFallback() {
        myFixture.configureByText("Test.kt", "class Foo { val x = 1 }")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            actionId != "GotoDeclarationOnly"
        }
        try {
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('D', editor).shouldBeTrue()
            executedActions shouldBe listOf("GotoDeclarationOnly", "GotoDeclaration")
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testEntryNavigationKeymap() {
        val text = "val a = 1\nval b = 2\nval c = 3\n"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        val entryAIdx = text.indexOf("val a = 1")
        val entryBIdx = text.indexOf("val b = 2")
        val entryCIdx = text.indexOf("val c = 3")

        // First ]e from start selects first entry (val a = 1)
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('e', editor).shouldBeTrue()
        caret.offset shouldBe entryAIdx
        caret.hasSelection().shouldBeTrue()

        // ]e -> next entry (val b = 2)
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('e', editor).shouldBeTrue()
        caret.offset shouldBe entryBIdx
        caret.hasSelection().shouldBeTrue()

        // ]e -> next entry (val c = 3)
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('e', editor).shouldBeTrue()
        caret.offset shouldBe entryCIdx
        caret.hasSelection().shouldBeTrue()

        // [e -> previous entry (val b = 2)
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('e', editor).shouldBeTrue()
        caret.offset shouldBe entryBIdx
        caret.hasSelection().shouldBeTrue()

        // [e -> previous entry (val a = 1)
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('e', editor).shouldBeTrue()
        caret.offset shouldBe entryAIdx
        caret.hasSelection().shouldBeTrue()

        // Reset to beginning and test count prefix: 2]e
        caret.removeSelection()
        caret.moveToOffset(0)
        HelixKeyHandler.handleKey('2', editor).shouldBeTrue()
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('e', editor).shouldBeTrue()
        caret.offset shouldBe entryBIdx
        caret.hasSelection().shouldBeTrue()

        // From entry C (index 20), count prefix 2[e jumps back to entry A
        caret.moveToOffset(entryCIdx)
        caret.setSelection(entryCIdx, entryCIdx + "val c = 3".length)
        HelixKeyHandler.handleKey('2', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('e', editor).shouldBeTrue()
        caret.offset shouldBe entryAIdx
        caret.hasSelection().shouldBeTrue()
    }

    fun testSpellingErrorNavigationKeymap() {
        myFixture.configureByText("Test.txt", "Some text with spelling errors\n")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }
        try {
            // [s -> GotoPreviousSpellingError
            HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('s', editor).shouldBeTrue()
            executedActions shouldBe listOf("GotoPreviousSpellingError")

            // ]s -> GotoNextSpellingError
            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('s', editor).shouldBeTrue()
            executedActions shouldBe listOf("GotoPreviousSpellingError", "GotoNextSpellingError")

            // With count prefix: 2[s
            HelixKeyHandler.handleKey('2', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('s', editor).shouldBeTrue()
            executedActions shouldBe listOf(
                "GotoPreviousSpellingError",
                "GotoNextSpellingError",
                "GotoPreviousSpellingError",
                "GotoPreviousSpellingError",
            )

            // With count prefix: 2]s
            HelixKeyHandler.handleKey('2', editor).shouldBeTrue()
            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('s', editor).shouldBeTrue()
            executedActions shouldBe listOf(
                "GotoPreviousSpellingError",
                "GotoNextSpellingError",
                "GotoPreviousSpellingError",
                "GotoPreviousSpellingError",
                "GotoNextSpellingError",
                "GotoNextSpellingError",
            )
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testSpellingAndMoveLineCommandsExecution() {
        myFixture.configureByText("Test.txt", "Sample text\n")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }
        try {
            jp.titze.intellij.helix.command.HelixCommands.execute("move-line-up", editor)
            jp.titze.intellij.helix.command.HelixCommands.execute("move-line-down", editor)
            jp.titze.intellij.helix.command.HelixCommands.execute("goto-prev-spelling-error", editor)
            jp.titze.intellij.helix.command.HelixCommands.execute("goto-next-spelling-error", editor)
            executedActions shouldBe listOf(
                "MoveLineUp",
                "MoveLineDown",
                "GotoPreviousSpellingError",
                "GotoNextSpellingError",
            )
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testEntryCommandsExecution() {
        val text = "val a = 1\nval b = 2\nval c = 3\n"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        jp.titze.intellij.helix.command.HelixCommands.execute("goto-next-entry", editor)
        caret.offset shouldBe text.indexOf("val a = 1")
        caret.hasSelection().shouldBeTrue()

        jp.titze.intellij.helix.command.HelixCommands.execute("goto-next-entry", editor)
        caret.offset shouldBe text.indexOf("val b = 2")
        caret.hasSelection().shouldBeTrue()

        jp.titze.intellij.helix.command.HelixCommands.execute("goto-prev-entry", editor)
        caret.offset shouldBe text.indexOf("val a = 1")
        caret.hasSelection().shouldBeTrue()
    }

    fun testRepeatLastMotionEntryNavigation() {
        val text = "val a = 1\nval b = 2\nval c = 3\n"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        caret.moveToOffset(0)

        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('e', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("val a = 1")

        HelixActions.repeatLastMotion(editor, 1)
        caret.offset shouldBe text.indexOf("val b = 2")
    }

    fun testYankDiagnosticWhenDiagnosticPresent() {
        val text = "fun main() {\n    val x = 1\n}\n"
        myFixture.configureByText("test.kt", text)
        val editor = myFixture.editor

        val markup = editor.markupModel
        val lineStart = editor.document.getLineStartOffset(1)
        val lineEnd = editor.document.getLineEndOffset(1)
        val highlighter = markup.addRangeHighlighter(
            lineStart,
            lineEnd,
            com.intellij.openapi.editor.markup.HighlighterLayer.ERROR,
            null,
            com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE,
        )
        highlighter.errorStripeTooltip = "Unresolved reference: unresolvedVar"

        editor.caretModel.primaryCaret.moveToOffset(lineStart + 2)

        val result = HelixActions.yankDiagnostic(editor)
        result.shouldBeTrue()

        val clipboardText = jp.titze.intellij.helix.register.HelixRegisterManager.getFromClipboard()?.text
        clipboardText shouldBe "Unresolved reference: unresolvedVar"
        jp.titze.intellij.helix.register.HelixRegisterManager.defaultRegister?.text shouldBe
            "Unresolved reference: unresolvedVar"

        jp.titze.intellij.helix.register.HelixRegisterManager.clear()
        jp.titze.intellij.helix.command.HelixCommands.execute("yank-diagnostic", editor)
        jp.titze.intellij.helix.register.HelixRegisterManager.defaultRegister?.text shouldBe
            "Unresolved reference: unresolvedVar"
    }

    fun testYankDiagnosticWhenNoDiagnosticPresent() {
        myFixture.configureByText("test.kt", "fun main() {}\n")
        val editor = myFixture.editor
        val result = HelixActions.yankDiagnostic(editor)
        result.shouldBeFalse()
    }

    fun testXmlElementNavigationForwardAndBackward() {
        val text = "<root>\n    <item>One</item>\n    <item>Two</item>\n</root>"
        myFixture.configureByText("test.xml", text)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        caret.moveToOffset(0) // at <root>

        // Move to next tag: <item>
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('x', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("<item>One")

        // Move to closing tag: </item>
        HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('x', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("</item>")

        // Move backward to <item>
        HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('x', editor).shouldBeTrue()
        caret.offset shouldBe text.indexOf("<item>One")
    }

    fun testChangeNavigationDispatchesDiffActionsInDiffEditor() {
        myFixture.configureByText("Test.txt", "line 1\nline 2\n")
        val regularEditor = myFixture.editor
        val diffDoc = com.intellij.openapi.editor.EditorFactory.getInstance().createDocument("a\nb\nc\n")
        val diffEditor = com.intellij.openapi.editor.EditorFactory.getInstance()
            .createEditor(diffDoc, project, com.intellij.openapi.editor.EditorKind.DIFF)
        val executedActions = mutableListOf<Pair<String, com.intellij.openapi.editor.Editor>>()
        HelixActionDelegate.actionExecutor = { actionId, ed ->
            executedActions.add(actionId to ed)
            true
        }
        try {
            // Regular editor continues using VCS gutter marker actions
            HelixKeyHandler.handleKey(']', regularEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', regularEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('[', regularEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', regularEditor).shouldBeTrue()

            // Diff editor routes ]g / [g / count / Alt+. to NextDiff / PreviousDiff
            HelixKeyHandler.handleKey(']', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('[', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('2', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey(']', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', diffEditor).shouldBeTrue()
            HelixActions.repeatLastMotion(diffEditor, 1)

            executedActions.map { it.first } shouldBe listOf(
                "VcsShowNextChangeMarker",
                "VcsShowPrevChangeMarker",
                "NextDiff",
                "PreviousDiff",
                "NextDiff",
                "NextDiff",
                "NextDiff",
            )
            executedActions.drop(2).all { it.second === diffEditor }.shouldBeTrue()
        } finally {
            HelixActionDelegate.actionExecutor = null
            com.intellij.openapi.editor.EditorFactory.getInstance().releaseEditor(diffEditor)
        }
    }

    fun testChangeNavigationExecutesRegisteredComponentActionInDiffEditor() {
        val diffDoc = com.intellij.openapi.editor.EditorFactory.getInstance().createDocument("a\nb\n")
        val diffEditor = com.intellij.openapi.editor.EditorFactory.getInstance()
            .createEditor(diffDoc, project, com.intellij.openapi.editor.EditorKind.DIFF)
        val panel = javax.swing.JPanel(java.awt.BorderLayout())
        panel.add(diffEditor.component, java.awt.BorderLayout.CENTER)

        val invoked = mutableListOf<String>()
        val nextAction = object : com.intellij.diff.actions.impl.NextDifferenceAction() {
            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                invoked.add("next:${e.place}")
            }
        }
        val prevAction = object : com.intellij.openapi.actionSystem.AnAction() {
            init {
                com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom(
                    this,
                    com.intellij.openapi.actionSystem.IdeActions.ACTION_PREVIOUS_DIFF,
                )
            }

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                invoked.add("prev:${e.place}")
            }
        }
        nextAction.registerCustomShortcutSet(panel, null)
        prevAction.registerCustomShortcutSet(panel, null)

        try {
            HelixKeyHandler.handleKey(']', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('[', diffEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', diffEditor).shouldBeTrue()

            invoked shouldBe listOf(
                "next:${com.intellij.openapi.actionSystem.ActionPlaces.KEYBOARD_SHORTCUT}",
                "prev:${com.intellij.openapi.actionSystem.ActionPlaces.KEYBOARD_SHORTCUT}",
            )
        } finally {
            com.intellij.openapi.editor.EditorFactory.getInstance().releaseEditor(diffEditor)
        }
    }

    fun testChangeNavigationMovesCaretAcrossSimpleDiffViewerHunks() {
        val leftText = "line 0\nline 1\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7\nline 8\nline 9\n"
        val rightText = "line 0\nchanged 1\nline 2\nline 3\nchanged 4\nline 5\nline 6\nline 7\nchanged 8\nline 9\n"
        val factory = com.intellij.diff.DiffContentFactory.getInstance()
        val request = com.intellij.diff.requests.SimpleDiffRequest(
            "Diff",
            factory.create(project, leftText),
            factory.create(project, rightText),
            "Left",
            "Right",
        )
        val context = object : com.intellij.diff.DiffContext() {
            override fun getProject() = this@HelixCodeNavigationTest.project
            override fun isWindowFocused() = true
            override fun isFocusedInWindow() = true
            override fun requestFocusInWindow() { /* no-op */ }
        }
        val viewer = com.intellij.diff.tools.simple.SimpleDiffViewer(context, request)
        com.intellij.ide.impl.HeadlessDataManager.fallbackToProductionDataManager(viewer)
        try {
            viewer.init()
            viewer.setCurrentSide(com.intellij.diff.util.Side.RIGHT)
            viewer.rediff(true)
            val deadline = System.currentTimeMillis() + 5_000
            while (viewer.hasPendingRediff() && System.currentTimeMillis() < deadline) {
                com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
                Thread.sleep(10)
            }
            viewer.hasPendingRediff().shouldBeFalse()
            val rightEditor = viewer.editor2
            val caret = rightEditor.caretModel.primaryCaret
            caret.moveToOffset(0)

            // ]g jumps to first diff hunk (line 1), then second (line 4), then third (line 8)
            HelixKeyHandler.handleKey(']', rightEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', rightEditor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 1

            HelixKeyHandler.handleKey(']', rightEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', rightEditor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 4

            HelixKeyHandler.handleKey(']', rightEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', rightEditor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 8

            // [g jumps back to second diff hunk (line 4)
            HelixKeyHandler.handleKey('[', rightEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', rightEditor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 4

            // ]G jumps to last diff hunk (line 8), [G jumps to first diff hunk (line 1)
            HelixKeyHandler.handleKey(']', rightEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('G', rightEditor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 8

            HelixKeyHandler.handleKey('[', rightEditor).shouldBeTrue()
            HelixKeyHandler.handleKey('G', rightEditor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 1
        } finally {
            com.intellij.openapi.util.Disposer.dispose(viewer)
        }
    }

    fun testChangeNavigationInNormalEditorJumpsToHunkStart() {
        val currentText = listOf(
            "line 0",
            "mod 1",
            "mod 2",
            "mod 3",
            "line 4",
            "line 5",
            "mod 6",
            "mod 7",
            "mod 8",
            "line 9",
            "line 10",
            "line 11",
            "line 12",
        ).joinToString("\n", postfix = "\n")

        myFixture.configureByText("Changes.txt", currentText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val hunks = listOf(
            jp.titze.intellij.helix.motion.HelixJumpMotions.ChangeLineRange(1, 4),
            jp.titze.intellij.helix.motion.HelixJumpMotions.ChangeLineRange(6, 9),
            jp.titze.intellij.helix.motion.HelixJumpMotions.ChangeLineRange(11, 11),
        )
        jp.titze.intellij.helix.motion.HelixJumpMotions.changeRangesProvider = { hunks }

        try {
            caret.moveToOffset(0)

            // ]g jumps to start line (1) of multi-line hunk 1..3, not end line 3
            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 1
            caret.hasSelection().shouldBeFalse()

            // ]g from start of hunk 1..3 jumps to start line (6) of hunk 6..8
            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 6

            // ]g jumps to pure deletion line (11)
            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 11

            // [g from deletion jumps back to start line (6) of hunk 6..8
            HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 6

            // [g from inside multi-line hunk (line 7) jumps back to start of hunk 1..3 (line 1)
            caret.moveToOffset(editor.document.getLineStartOffset(7))
            HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 1

            // 2]g from line 0 lands on hunk 6..8 (line 6), Alt+. advances to line 11
            caret.moveToOffset(0)
            HelixKeyHandler.handleKey('2', editor).shouldBeTrue()
            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 6
            HelixActions.repeatLastMotion(editor, 1)
            caret.logicalPosition.line shouldBe 11

            // 2[g from line 11 returns to start of hunk 1..3 (line 1)
            HelixKeyHandler.handleKey('2', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 1

            // ]G and [G jump to last and first hunks at their start lines
            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('G', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 11

            HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('G', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 1
        } finally {
            jp.titze.intellij.helix.motion.HelixJumpMotions.changeRangesProvider = null
        }
    }

    fun testChangeNavigationInNormalEditorBoundaryHunksAndSelectMode() {
        val currentText = "new 0\nnew 1\nsame 2\nsame 3\nnew 4\nnew 5"

        myFixture.configureByText("BoundaryChanges.txt", currentText)
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret
        val hunks = listOf(
            jp.titze.intellij.helix.motion.HelixJumpMotions.ChangeLineRange(0, 2),
            jp.titze.intellij.helix.motion.HelixJumpMotions.ChangeLineRange(4, 6),
        )
        jp.titze.intellij.helix.motion.HelixJumpMotions.changeRangesProvider = { hunks }

        try {
            // [G reaches hunk starting at line 0; ]G reaches hunk reaching EOF at its start line (4)
            caret.moveToOffset(editor.document.getLineStartOffset(2))
            HelixKeyHandler.handleKey('[', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('G', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 0

            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('G', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 4

            // Select mode extends selection to start of next hunk
            caret.moveToOffset(0)
            HelixActions.toggleSelectMode(editor)
            HelixKeyHandler.handleKey(']', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('g', editor).shouldBeTrue()
            caret.logicalPosition.line shouldBe 4
            caret.hasSelection().shouldBeTrue()
            caret.selectionStart shouldBe 0
        } finally {
            jp.titze.intellij.helix.motion.HelixJumpMotions.changeRangesProvider = null
        }
    }

    fun testChangeNavigationExtractsRangesFromTracker() {
        val currentText = "line 0\nmod 1\nmod 2\nline 3\n"
        val baseText = "line 0\norig 1\norig 2\nline 3\n"
        val psiFile = myFixture.configureByText("TrackerReflect.txt", currentText)
        val editor = myFixture.editor

        data class FakeRange(val line1: Int, val line2: Int)
        class FakeTracker(
            private val valid: Boolean,
            private val availableEditor: com.intellij.openapi.editor.Editor?,
            private val rangeList: List<FakeRange>,
        ) {
            @Suppress("unused")
            fun isValid(): Boolean = valid

            @Suppress("unused")
            fun isAvailableAt(ed: com.intellij.openapi.editor.Editor): Boolean = ed === availableEditor

            @Suppress("unused")
            fun getRanges(): List<FakeRange> = rangeList
        }
        val validTracker = FakeTracker(true, editor, listOf(FakeRange(1, 3), FakeRange(4, 4)))
        val invalidTracker = FakeTracker(false, editor, listOf(FakeRange(0, 1)))

        jp.titze.intellij.helix.motion.HelixJumpMotions.extractChangeRangesFromTracker(validTracker, editor) shouldBe
            listOf(
                jp.titze.intellij.helix.motion.HelixJumpMotions.ChangeLineRange(1, 3),
                jp.titze.intellij.helix.motion.HelixJumpMotions.ChangeLineRange(4, 4),
            )
        jp.titze.intellij.helix.motion.HelixJumpMotions.extractChangeRangesFromTracker(invalidTracker, editor) shouldBe
            null

        // Verify real LineStatusTrackerManager lookup does not throw when no tracker is attached
        jp.titze.intellij.helix.motion.HelixJumpMotions.readTrackerChangeRanges(project, editor) shouldBe null

        // Verify reflective extraction against IntelliJ's real SimpleLocalLineStatusTracker
        val trackerClass = Class.forName("com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker")
        val createMethod = trackerClass.getMethod(
            "createTracker",
            com.intellij.openapi.project.Project::class.java,
            com.intellij.openapi.editor.Document::class.java,
            com.intellij.openapi.vfs.VirtualFile::class.java,
        )
        val realTracker = createMethod.invoke(null, project, editor.document, psiFile.virtualFile)
        realTracker.shouldNotBeNull()
        try {
            trackerClass.getMethod("setBaseRevision", CharSequence::class.java).invoke(realTracker, baseText)
            val extracted = jp.titze.intellij.helix.motion.HelixJumpMotions
                .extractChangeRangesFromTracker(realTracker, editor)
            extracted shouldBe listOf(jp.titze.intellij.helix.motion.HelixJumpMotions.ChangeLineRange(1, 3))
        } finally {
            trackerClass.getMethod("release").invoke(realTracker)
        }
    }
}
