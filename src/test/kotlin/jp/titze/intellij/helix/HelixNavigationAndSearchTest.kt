package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixPromptType
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixNavigationAndSearchTest : BasePlatformTestCase() {
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
            marginY = 20,
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
            marginY = 20,
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
            marginY = 20,
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

        HelixWhichKeyPopup.show(editor, "z")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "Z")
        HelixWhichKeyPopup.hide()
        HelixWhichKeyPopup.isShowing() shouldBe false
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

        caret.offset shouldBe 12
        caret.hasSelection().shouldBeTrue()
        caret.selectedText shouldBe "(hello world"
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

    fun testManualSaveJumpWithCtrlS() {
        myFixture.configureByText("jump_test.txt", "line 1\nline 2\nline 3\nline 4\nline 5")
        val editor = myFixture.editor
        val jumpService = HelixJumpListService.getInstance(myFixture.project)
        jumpService.clear()

        // Move to line 3 (index 2)
        editor.caretModel.primaryCaret.moveToOffset(editor.document.getLineStartOffset(2))
        HelixKeyHandler.handleKey('\u0013', editor).shouldBeTrue() // Ctrl-S

        val entries = jumpService.getEntries()
        entries.size shouldBe 1
        entries[0].line shouldBe 3
        entries[0].previewText shouldBe "line 3"
    }

    fun testJumpBackwardAndForward() {
        myFixture.configureByText("jump_nav.txt", "alpha\nbeta\ngamma\ndelta\nepsilon")
        val editor = myFixture.editor
        val jumpService = HelixJumpListService.getInstance(myFixture.project)
        jumpService.clear()

        // Jump 1: at alpha (line 1)
        editor.caretModel.primaryCaret.moveToOffset(editor.document.getLineStartOffset(0))
        jumpService.recordCurrent(editor, force = true)

        // Jump 2: at gamma (line 3)
        editor.caretModel.primaryCaret.moveToOffset(editor.document.getLineStartOffset(2))
        jumpService.recordCurrent(editor, force = true)

        // Jump 3: at epsilon (line 5)
        editor.caretModel.primaryCaret.moveToOffset(editor.document.getLineStartOffset(4))

        // Jump backward once -> should go to gamma (line 3)
        val jumped1 = jumpService.jumpBackward(editor, count = 1)
        jumped1.shouldBeTrue()
        editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 shouldBe 3

        // Jump backward again -> should go to alpha (line 1)
        val jumped2 = jumpService.jumpBackward(editor, count = 1)
        jumped2.shouldBeTrue()
        editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 shouldBe 1

        // Jump forward once -> should go to gamma (line 3)
        val forward1 = jumpService.jumpForward(editor, count = 1)
        forward1.shouldBeTrue()
        editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 shouldBe 3

        // Jump forward again -> should go to epsilon (line 5)
        val forward2 = jumpService.jumpForward(editor, count = 1)
        forward2.shouldBeTrue()
        editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 shouldBe 5
    }

    fun testSignificantMotionJumpRecording() {
        val lines = (1..50).joinToString("\n") { "line $it" }
        myFixture.configureByText("motion_jump.txt", lines)
        val editor = myFixture.editor
        val jumpService = HelixJumpListService.getInstance(myFixture.project)
        jumpService.clear()

        // Caret starts at line 1
        editor.caretModel.primaryCaret.moveToOffset(0)

        // Press 'G' to jump to end of file
        HelixKeyHandler.handleKey('G', editor).shouldBeTrue()
        editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 shouldBe 50

        // Jumplist should have recorded origin (line 1)
        jumpService.getEntries().size shouldBe 1
        jumpService.getEntries()[0].line shouldBe 1

        // Press Ctrl-O ('\u000F') to jump backward
        HelixKeyHandler.handleKey('\u000F', editor).shouldBeTrue()
        editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 shouldBe 1
    }

    fun testJumpWithCount() {
        val lines = (1..20).joinToString("\n") { "step $it" }
        myFixture.configureByText("count_jump.txt", lines)
        val editor = myFixture.editor
        val jumpService = HelixJumpListService.getInstance(myFixture.project)
        jumpService.clear()

        for (i in 0..4) {
            editor.caretModel.primaryCaret.moveToOffset(editor.document.getLineStartOffset(i))
            jumpService.recordCurrent(editor, force = true)
        }
        // Jumps at lines 1, 2, 3, 4, 5
        jumpService.getEntries().size shouldBe 5

        // Jump back with count = 3 from line 5 -> line 2
        jumpService.jumpBackward(editor, count = 3).shouldBeTrue()
        editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 shouldBe 2
    }

    fun testJumplistCapacityLimit() {
        myFixture.configureByText("limit_test.txt", (1..150).joinToString("\n") { "val $it" })
        val editor = myFixture.editor
        val jumpService = HelixJumpListService.getInstance(myFixture.project)
        jumpService.clear()

        for (i in 0..120) {
            editor.caretModel.primaryCaret.moveToOffset(editor.document.getLineStartOffset(i))
            jumpService.recordCurrent(editor, force = true)
        }

        jumpService.getEntries().size shouldBe 100
    }

    fun testJumpsCommandAndNumericLineGoto() {
        myFixture.configureByText("cmd_jumps.txt", "a\nb\nc\nd\ne\nf\ng\nh\ni\nj")
        val editor = myFixture.editor
        val jumpService = HelixJumpListService.getInstance(myFixture.project)
        jumpService.clear()

        // Verify :jumps command exists in COMMANDS
        HelixCommandPopup.COMMANDS.any { it.name == "jumps" }.shouldBeTrue()

        // Execute :4 -> jumps to line 4 and records line 1
        HelixCommandPopup.executeCommand("4", editor)
        editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 shouldBe 4

        jumpService.getEntries().size shouldBe 1
        jumpService.getEntries()[0].line shouldBe 1
    }
}
