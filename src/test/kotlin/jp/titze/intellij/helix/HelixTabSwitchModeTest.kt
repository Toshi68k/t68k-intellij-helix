package jp.titze.intellij.helix

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.editor.HelixFileEditorListener
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager

@Suppress("DEPRECATION")
class HelixTabSwitchModeTest : BasePlatformTestCase() {

    override fun tearDown() {
        HelixSettings.instance.resetToNormalOnTabSwitch = true
        super.tearDown()
    }

    fun testTabSwitchResetsInsertToNormalWhenEnabled() {
        HelixSettings.instance.resetToNormalOnTabSwitch = true

        val file1 = myFixture.configureByText("file1.txt", "content 1")
        val editor1 = myFixture.editor
        val textEditor1 = FileEditorManager.getInstance(project).getSelectedEditor(file1.virtualFile) as TextEditor

        HelixActions.enterInsert(editor1)
        HelixStateManager.getOrCreate(editor1).mode shouldBe HelixMode.INSERT

        val listener = HelixFileEditorListener()
        val event = FileEditorManagerEvent(
            FileEditorManager.getInstance(project),
            null,
            null,
            null,
            file1.virtualFile,
            textEditor1,
            null,
        )

        listener.selectionChanged(event)

        val state = HelixStateManager.getOrCreate(editor1)
        state.mode shouldBe HelixMode.NORMAL
        editor1.settings.isBlockCursor.shouldBeTrue()
    }

    fun testTabSwitchPreservesInsertWhenDisabled() {
        HelixSettings.instance.resetToNormalOnTabSwitch = false

        val file1 = myFixture.configureByText("file2.txt", "content 2")
        val editor1 = myFixture.editor
        val textEditor1 = FileEditorManager.getInstance(project).getSelectedEditor(file1.virtualFile) as TextEditor

        HelixActions.enterInsert(editor1)
        HelixStateManager.getOrCreate(editor1).mode shouldBe HelixMode.INSERT

        val listener = HelixFileEditorListener()
        val event = FileEditorManagerEvent(
            FileEditorManager.getInstance(project),
            null,
            null,
            null,
            file1.virtualFile,
            textEditor1,
            null,
        )

        listener.selectionChanged(event)

        val state = HelixStateManager.getOrCreate(editor1)
        state.mode shouldBe HelixMode.INSERT
    }

    fun testFileOpenedResetsToNormal() {
        HelixSettings.instance.resetToNormalOnTabSwitch = true

        val file = myFixture.configureByText("file3.txt", "content 3")
        val editor = myFixture.editor
        HelixActions.enterInsert(editor)
        HelixStateManager.getOrCreate(editor).mode shouldBe HelixMode.INSERT

        val listener = HelixFileEditorListener()
        listener.fileOpened(FileEditorManager.getInstance(project), file.virtualFile)

        val state = HelixStateManager.getOrCreate(editor)
        state.mode shouldBe HelixMode.NORMAL
        editor.settings.isBlockCursor.shouldBeTrue()
    }
}
