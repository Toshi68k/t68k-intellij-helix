package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixShellActions
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.shell.HelixShellExecutor
import jp.titze.intellij.helix.shell.HelixShellResult
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixPromptType

class HelixShellActionsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixShellExecutor.reset()
    }

    override fun tearDown() {
        HelixShellExecutor.reset()
        super.tearDown()
    }

    fun testShellPipeReplacesSelection() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        editor.selectionModel.setSelection(0, 5) // "hello"

        HelixShellExecutor.shellRunner = { cmd, input, _ ->
            cmd shouldBe "tr a-z A-Z"
            HelixShellResult(0, input.orEmpty().uppercase(), "")
        }

        val success = HelixShellActions.pipeSelections(editor, "tr a-z A-Z")
        success.shouldBeTrue()
        editor.document.text shouldBe "HELLO world"
        editor.selectionModel.selectedText shouldBe "HELLO"
    }

    fun testShellPipeTrailingNewlineStrippingWhenInputHasNoNewline() {
        myFixture.configureByText("test.txt", "word")
        val editor = myFixture.editor
        editor.selectionModel.setSelection(0, 4)

        HelixShellExecutor.shellRunner = { _, input, _ ->
            // Output has trailing newline like unix CLI tools
            HelixShellResult(0, "${input.orEmpty().uppercase()}\n", "")
        }

        val success = HelixShellActions.pipeSelections(editor, "some-cli")
        success.shouldBeTrue()
        editor.document.text shouldBe "WORD"
    }

    fun testShellPipeMultiCaretDescendingOrder() {
        myFixture.configureByText("test.txt", "cat dog cat")
        val editor = myFixture.editor

        // Select all "cat"
        HelixKeyHandler.handleKey('%', editor)
        HelixActions.selectRegex(editor, "cat")
        editor.caretModel.caretCount shouldBe 2

        HelixShellExecutor.shellRunner = { _, input, _ ->
            HelixShellResult(0, if (input == "cat") "elephant" else input.orEmpty(), "")
        }

        val success = HelixShellActions.pipeSelections(editor, "replace")
        success.shouldBeTrue()
        editor.document.text shouldBe "elephant dog elephant"
        val selectedTexts = editor.caretModel.allCarets.map { it.selectedText }
        selectedTexts shouldBe listOf("elephant", "elephant")
    }

    fun testShellPipeErrorPreservesBuffer() {
        myFixture.configureByText("test.txt", "safe text")
        val editor = myFixture.editor
        editor.selectionModel.setSelection(0, 4)

        HelixShellExecutor.shellRunner = { _, _, _ ->
            HelixShellResult(1, "", "Command not found")
        }

        val success = HelixShellActions.pipeSelections(editor, "failing-cmd")
        success.shouldBeFalse()
        editor.document.text shouldBe "safe text"
    }

    fun testShellInsertOutput() {
        myFixture.configureByText("test.txt", "bar")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(0)

        HelixShellExecutor.shellRunner = { _, _, _ ->
            HelixShellResult(0, "foo_", "")
        }

        val success = HelixShellActions.insertOutput(editor, "echo foo_", append = false)
        success.shouldBeTrue()
        editor.document.text shouldBe "foo_bar"
        editor.selectionModel.selectedText shouldBe "foo_"
    }

    fun testShellAppendOutput() {
        myFixture.configureByText("test.txt", "foo")
        val editor = myFixture.editor
        editor.selectionModel.setSelection(0, 3)

        HelixShellExecutor.shellRunner = { _, _, _ ->
            HelixShellResult(0, "_bar", "")
        }

        val success = HelixShellActions.insertOutput(editor, "echo _bar", append = true)
        success.shouldBeTrue()
        editor.document.text shouldBe "foo_bar"
        editor.selectionModel.selectedText shouldBe "_bar"
    }

    fun testShellKeepPipeFiltersSelections() {
        myFixture.configureByText("test.txt", "apple banana cherry")
        val editor = myFixture.editor

        // Select all words
        HelixKeyHandler.handleKey('%', editor)
        HelixActions.selectRegex(editor, "\\w+")
        editor.caretModel.caretCount shouldBe 3

        HelixShellExecutor.shellRunner = { _, input, _ ->
            val exitCode = if (input == "banana") 0 else 1
            HelixShellResult(exitCode, "", "")
        }

        val success = HelixShellActions.keepPipeSelections(editor, "grep banana")
        success.shouldBeTrue()
        editor.caretModel.caretCount shouldBe 1
        editor.selectionModel.selectedText shouldBe "banana"
    }

    fun testKeyHandlerOpensShellPrompts() {
        myFixture.configureByText("test.txt", "content")
        val editor = myFixture.editor
        val promptBar = HelixPromptBar.getOrCreate(editor)

        HelixKeyHandler.handleKey('|', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('!', editor).shouldBeTrue()
        HelixKeyHandler.handleKey('$', editor).shouldBeTrue()

        promptBar.show(HelixPromptType.PIPE)
        promptBar.isVisible.shouldBeTrue()
        promptBar.cancelAndClose()
        promptBar.isVisible.shouldBeFalse()

        promptBar.show(HelixPromptType.INSERT_OUTPUT)
        promptBar.isVisible.shouldBeTrue()
        promptBar.cancelAndClose()
        promptBar.isVisible.shouldBeFalse()

        promptBar.show(HelixPromptType.KEEP_PIPE)
        promptBar.isVisible.shouldBeTrue()
        promptBar.cancelAndClose()
        promptBar.isVisible.shouldBeFalse()
    }

    fun testCommandPalettePiping() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        editor.selectionModel.setSelection(0, 6)

        HelixShellExecutor.shellRunner = { cmd, input, _ ->
            cmd shouldBe "rev"
            HelixShellResult(0, input.orEmpty().reversed(), "")
        }

        HelixCommands.execute("pipe rev", editor)
        editor.document.text shouldBe "elpmas"
    }

    fun testShellPipeToDoesNotMutate() {
        myFixture.configureByText("test.txt", "immutable content")
        val editor = myFixture.editor
        editor.selectionModel.setSelection(0, 9)

        var capturedInput: String? = null
        HelixShellExecutor.shellRunner = { _, input, _ ->
            capturedInput = input
            HelixShellResult(0, "discarded", "")
        }

        val success = HelixShellActions.pipeToSelections(editor, "sink-cmd")
        success.shouldBeTrue()
        capturedInput shouldBe "immutable"
        editor.document.text shouldBe "immutable content"
    }

    fun testShellAppendOutputMultiCaret() {
        myFixture.configureByText("test.txt", "one two three")
        val editor = myFixture.editor

        HelixKeyHandler.handleKey('%', editor)
        HelixActions.selectRegex(editor, "\\w+")
        editor.caretModel.caretCount shouldBe 3

        HelixShellExecutor.shellRunner = { _, _, _ ->
            HelixShellResult(0, "!", "")
        }

        val success = HelixShellActions.insertOutput(editor, "echo !", append = true)
        success.shouldBeTrue()
        editor.document.text shouldBe "one! two! three!"
        val selectedTexts = editor.caretModel.allCarets.map { it.selectedText }
        selectedTexts shouldBe listOf("!", "!", "!")
    }

    fun testRealProcessExecutionEcho() {
        // Test actual ProcessBuilder execution without mock runner
        HelixShellExecutor.reset()
        val result = HelixShellExecutor.execute("echo helix_shell_test")
        result.exitCode shouldBe 0
        result.stdout.trim() shouldBe "helix_shell_test"
    }

    fun testShellPipeUndoSingleStep() {
        myFixture.configureByText("test.txt", "original text")
        val editor = myFixture.editor
        editor.selectionModel.setSelection(0, 8) // "original"

        HelixShellExecutor.shellRunner = { _, input, _ ->
            HelixShellResult(0, input.orEmpty().uppercase(), "")
        }

        HelixShellActions.pipeSelections(editor, "mock-upper")
        editor.document.text shouldBe "ORIGINAL text"

        val fileEditor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            .getSelectedEditor(myFixture.file.virtualFile)
        val undoManager = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
        undoManager.undo(fileEditor)
        editor.document.text shouldBe "original text"
    }
}
