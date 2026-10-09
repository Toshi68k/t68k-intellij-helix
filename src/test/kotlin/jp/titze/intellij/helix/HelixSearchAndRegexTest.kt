package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixSearchActions
import jp.titze.intellij.helix.editor.HelixEventDispatcher
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixPromptCategory
import jp.titze.intellij.helix.ui.HelixPromptHistory
import jp.titze.intellij.helix.ui.HelixPromptHistoryNavigator
import jp.titze.intellij.helix.ui.HelixPromptType
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

class HelixSearchAndRegexTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixPromptHistory.clear()
    }

    override fun tearDown() {
        HelixPromptHistory.clear()
        if (myFixture.editor != null && !myFixture.editor.isDisposed) {
            HelixPromptBar.cancelActivePrompt(myFixture.editor)
        }
        super.tearDown()
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

    fun testCaretSnapshotAndRestore() {
        val text = "alpha beta gamma delta"
        myFixture.configureByText("test.txt", text)
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

    fun testSearchSelectionRaw() {
        myFixture.configureByText("test.txt", "my-var other my-var-extra")
        val editor = myFixture.editor

        val caret = editor.caretModel.primaryCaret
        caret.setSelection(0, 6) // "my-var" (contains hyphen)
        HelixActions.searchSelection(editor, detectWordBoundaries = false)

        HelixSearchActions.lastSearchPattern shouldBe "\\Qmy-var\\E"
    }

    fun testPromptHistoryRingCapacityAndDeduplication() {
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "foo")
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "bar")
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "bar") // consecutive duplicate ignored
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "baz")
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "foo") // moved to most recent

        val list = HelixPromptHistory.get(HelixPromptCategory.SEARCH)
        list shouldBe listOf("bar", "baz", "foo")

        // Test capacity cap (100)
        repeat(150) { i ->
            HelixPromptHistory.add(HelixPromptCategory.SEARCH, "query-$i")
        }
        val capped = HelixPromptHistory.get(HelixPromptCategory.SEARCH)
        capped.size shouldBe 100
        capped.last() shouldBe "query-149"
    }

    fun testPromptHistoryNavigatorUpDownCycling() {
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "pattern-1")
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "pattern-2")
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "pattern-3")

        val navigator = HelixPromptHistoryNavigator(HelixPromptCategory.SEARCH)

        // 1st Up: returns newest entry ("pattern-3") and saves current draft
        navigator.onUp("draft-text") shouldBe "pattern-3"

        // 2nd Up: returns older entry ("pattern-2")
        navigator.onUp("pattern-3") shouldBe "pattern-2"

        // 3rd Up: returns oldest entry ("pattern-1")
        navigator.onUp("pattern-2") shouldBe "pattern-1"

        // 4th Up: already at oldest entry, returns null
        navigator.onUp("pattern-1").shouldBeNull()

        // 1st Down: steps forward to "pattern-2"
        navigator.onDown() shouldBe "pattern-2"

        // 2nd Down: steps forward to "pattern-3"
        navigator.onDown() shouldBe "pattern-3"

        // 3rd Down: steps past newest entry, restores initial draft
        navigator.onDown() shouldBe "draft-text"

        // 4th Down: already at draft, returns null
        navigator.onDown().shouldBeNull()
    }

    fun testPromptCategoryIsolation() {
        HelixPromptHistory.add(HelixPromptCategory.SEARCH, "mySearch")
        HelixPromptHistory.add(HelixPromptCategory.REGEX, "myRegex")
        HelixPromptHistory.add(HelixPromptCategory.SHELL, "myShell")

        HelixPromptHistory.get(HelixPromptCategory.SEARCH) shouldBe listOf("mySearch")
        HelixPromptHistory.get(HelixPromptCategory.REGEX) shouldBe listOf("myRegex")
        HelixPromptHistory.get(HelixPromptCategory.SHELL) shouldBe listOf("myShell")
    }

    fun testSearchSelectionUpdatesPromptHistory() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        HelixActions.search(editor, "hello")

        val history = HelixPromptHistory.get(HelixPromptCategory.SEARCH)
        history.contains("hello").shouldBeTrue()
    }

    fun testSearchNextInNormalModeJumpsSingleCaret() {
        val text = "alpha beta alpha gamma alpha"
        myFixture.configureByText("test.txt", text)
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(0)

        HelixActions.search(editor, "alpha")
        editor.caretModel.caretCount shouldBe 1
        editor.caretModel.currentCaret.selectedText shouldBe "alpha"
        editor.caretModel.currentCaret.selectionStart shouldBe 0

        HelixSearchActions.searchNext(editor)
        editor.caretModel.caretCount shouldBe 1
        editor.caretModel.currentCaret.selectedText shouldBe "alpha"
        editor.caretModel.currentCaret.selectionStart shouldBe 11

        HelixSearchActions.searchNext(editor)
        editor.caretModel.caretCount shouldBe 1
        editor.caretModel.currentCaret.selectedText shouldBe "alpha"
        editor.caretModel.currentCaret.selectionStart shouldBe 23
    }

    fun testSearchNextInSelectModeExtendsSelections() {
        myFixture.configureByText("test.txt", "apple orange apple banana apple")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(0)

        // Initial search in Normal mode
        HelixActions.search(editor, "apple")
        editor.caretModel.caretCount shouldBe 1

        // Switch to Select mode
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.SELECT)

        // In select mode, searchNext should add the next match as an additional caret
        HelixSearchActions.searchNext(editor)
        editor.caretModel.caretCount shouldBe 2
        editor.caretModel.allCarets.map { it.selectedText } shouldBe listOf("apple", "apple")

        // Once more
        HelixSearchActions.searchNext(editor)
        editor.caretModel.caretCount shouldBe 3
        editor.caretModel.allCarets.map { it.selectedText } shouldBe listOf("apple", "apple", "apple")
    }

    fun testSearchPrevInSelectModeExtendsSelections() {
        myFixture.configureByText("test.txt", "item1 item2 item1 item3 item1")
        val editor = myFixture.editor
        // Start near the end
        editor.caretModel.moveToOffset(28)

        HelixActions.search(editor, "item1")
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.SELECT)

        HelixSearchActions.searchPrev(editor)
        editor.caretModel.caretCount shouldBe 2
        editor.caretModel.allCarets.map { it.selectedText } shouldBe listOf("item1", "item1")
    }

    fun testPromptBarCtrlCCancelsPromptWithoutCommenting() {
        myFixture.configureByText("test.txt", "line of code")
        val editor = myFixture.editor

        var executedAction: String? = null
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedAction = actionId
            true
        }
        try {
            val bar = HelixPromptBar.getOrCreate(editor)
            bar.show(HelixPromptType.SEARCH)
            HelixPromptBar.isPromptActive(editor).shouldBeTrue()

            val dispatcher = HelixEventDispatcher()
            val ctrlC = KeyEvent(
                bar,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_C,
                'c',
            )

            val handled = dispatcher.dispatch(ctrlC)
            handled.shouldBeTrue()
            ctrlC.isConsumed.shouldBeTrue()
            HelixPromptBar.isPromptActive(editor).shouldBeFalse()
            executedAction.shouldBeNull()
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testPromptBarEscapeCancelsPrompt() {
        myFixture.configureByText("test.txt", "line of code")
        val editor = myFixture.editor

        val bar = HelixPromptBar.getOrCreate(editor)
        bar.show(HelixPromptType.SEARCH)
        HelixPromptBar.isPromptActive(editor).shouldBeTrue()

        val dispatcher = HelixEventDispatcher()
        val escapeEvent = KeyEvent(
            bar,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_ESCAPE,
            KeyEvent.CHAR_UNDEFINED,
        )

        val handled = dispatcher.dispatch(escapeEvent)
        handled.shouldBeTrue()
        escapeEvent.isConsumed.shouldBeTrue()
        HelixPromptBar.isPromptActive(editor).shouldBeFalse()
    }

    fun testPromptBarRegularKeyPassesThroughDispatcher() {
        myFixture.configureByText("test.txt", "line of code")
        val editor = myFixture.editor

        val bar = HelixPromptBar.getOrCreate(editor)
        bar.show(HelixPromptType.SEARCH)
        try {
            HelixPromptBar.isPromptActive(editor).shouldBeTrue()

            val dispatcher = HelixEventDispatcher()
            val typeA = KeyEvent(
                bar,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_A,
                'a',
            )

            val handled = dispatcher.dispatch(typeA)
            handled.shouldBeFalse()
            typeA.isConsumed.shouldBeFalse()
            HelixPromptBar.isPromptActive(editor).shouldBeTrue()
        } finally {
            bar.cancelAndClose()
        }
    }

    fun testPromptBarCtrlCWithEditorFocusCancelsPromptWithoutCommenting() {
        myFixture.configureByText("test.txt", "line of code")
        val editor = myFixture.editor

        var executedAction: String? = null
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedAction = actionId
            true
        }
        try {
            val bar = HelixPromptBar.getOrCreate(editor)
            bar.show(HelixPromptType.SEARCH)
            HelixPromptBar.isPromptActive(editor).shouldBeTrue()

            val dispatcher = HelixEventDispatcher()
            val ctrlC = KeyEvent(
                editor.contentComponent,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_C,
                'c',
            )

            val handled = dispatcher.dispatch(ctrlC)
            handled.shouldBeTrue()
            ctrlC.isConsumed.shouldBeTrue()
            HelixPromptBar.isPromptActive(editor).shouldBeFalse()
            executedAction.shouldBeNull()
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testPromptBarCtrlCInSelectModeCancelsPrompt() {
        myFixture.configureByText("test.txt", "line of code")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.SELECT)

        var executedAction: String? = null
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedAction = actionId
            true
        }
        try {
            val bar = HelixPromptBar.getOrCreate(editor)
            bar.show(HelixPromptType.SEARCH)
            HelixPromptBar.isPromptActive(editor).shouldBeTrue()

            val dispatcher = HelixEventDispatcher()
            val ctrlC = KeyEvent(
                editor.contentComponent,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_C,
                'c',
            )

            val handled = dispatcher.dispatch(ctrlC)
            handled.shouldBeTrue()
            ctrlC.isConsumed.shouldBeTrue()
            HelixPromptBar.isPromptActive(editor).shouldBeFalse()
            executedAction.shouldBeNull()
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }
}
