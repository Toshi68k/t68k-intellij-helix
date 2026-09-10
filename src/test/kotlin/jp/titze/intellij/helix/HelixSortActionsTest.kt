package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.motion.HelixMotions

class HelixSortActionsTest : BasePlatformTestCase() {

    fun testSortLinesAscending() {
        myFixture.configureByText("test.txt", "banana\napple\ncherry\n")
        val editor = myFixture.editor
        HelixMotions.selectAll(editor)

        HelixCommands.execute("sort", editor)
        editor.document.text shouldBe "apple\nbanana\ncherry\n"
    }

    fun testSortLinesDescending() {
        myFixture.configureByText("test.txt", "banana\napple\ncherry\n")
        val editor = myFixture.editor
        HelixMotions.selectAll(editor)

        HelixCommands.execute("sort -r", editor)
        editor.document.text shouldBe "cherry\nbanana\napple\n"
    }

    fun testSortLinesPartialSelection() {
        myFixture.configureByText("test.txt", "header\nzebra\nmonkey\nape\nfooter\n")
        val editor = myFixture.editor
        val doc = editor.document

        // Select lines 1..3 ("zebra\nmonkey\nape")
        val startOffset = doc.getLineStartOffset(1)
        val endOffset = doc.getLineEndOffset(3)
        editor.caretModel.primaryCaret.setSelection(startOffset, endOffset)

        HelixCommands.execute(":sort", editor)
        editor.document.text shouldBe "header\nape\nmonkey\nzebra\nfooter\n"
    }

    fun testSortBufferWhenNoSelection() {
        myFixture.configureByText("test.txt", "gamma\nalpha\nbeta\n")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.removeSelection()
        editor.caretModel.primaryCaret.moveToOffset(0)

        HelixCommands.execute("sort", editor)
        editor.document.text shouldBe "alpha\nbeta\ngamma\n"
    }

    fun testSortCommandAliases() {
        val sortCmd = HelixCommandPopup.COMMANDS.firstOrNull { it.name == "sort" }
        (sortCmd != null).shouldBeTrue()

        val sortRevCmd = HelixCommandPopup.COMMANDS.firstOrNull { it.name == "sort-reverse" }
        (sortRevCmd != null).shouldBeTrue()
        sortRevCmd!!.aliases.contains("sort -r").shouldBeTrue()
    }
}
