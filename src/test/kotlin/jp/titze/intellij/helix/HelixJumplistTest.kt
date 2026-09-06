package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.keymap.HelixKeyHandler

class HelixJumplistTest : BasePlatformTestCase() {
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
