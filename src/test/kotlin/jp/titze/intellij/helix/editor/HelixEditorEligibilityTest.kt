// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.editor

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.textarea.TextComponentEditorImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.settings.HelixEffectiveSettings
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import javax.swing.JTextArea

class HelixEditorEligibilityTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixSettings.instance.enabled = true
        HelixSettings.instance.activateInDiff = true
        HelixSettings.instance.activateInReadOnlyViewers = true
        HelixSettings.instance.activateInOtherEditors = true
        HelixEditorEligibility.onReadOnlyNotice = null
        HelixEffectiveSettings.clearAllOverrides()
    }

    override fun tearDown() {
        HelixSettings.instance.enabled = true
        HelixSettings.instance.activateInDiff = true
        HelixSettings.instance.activateInReadOnlyViewers = true
        HelixSettings.instance.activateInOtherEditors = false
        HelixEditorEligibility.onReadOnlyNotice = null
        HelixEffectiveSettings.clearAllOverrides()
        super.tearDown()
    }

    fun testTextComponentEditorIsNotActive() {
        val textArea = JTextArea("sample text")
        val textEditor = TextComponentEditorImpl(project, textArea)

        HelixEditorEligibility.isActive(textEditor).shouldBeFalse()
    }

    fun testStandardCodeEditorIsActive() {
        myFixture.configureByText("test.txt", "hello world")
        HelixEditorEligibility.isActive(myFixture.editor).shouldBeTrue()
    }

    fun testUpdateCursorColorToleratesUnsupportedOperationException() {
        val textArea = JTextArea("sample text")
        val textEditor = TextComponentEditorImpl(project, textArea)

        // TextComponentEditorImpl throws UnsupportedOperationException on getColorsScheme.
        // updateCursor should handle it gracefully without propagating the exception.
        HelixStateManager.updateCursor(textEditor, HelixMode.NORMAL)
    }

    fun testCommitEditorLineNumbersAreNotApplied() {
        myFixture.configureByText("test.txt", "commit message text")
        val editor = myFixture.editor
        editor.settings.isLineNumbersShown = false

        val commitPanel = com.intellij.vcs.commit.MockCommitPanel()
        commitPanel.add(editor.component)

        HelixEditorEligibility.isCommitEditor(editor).shouldBeTrue()

        HelixEffectiveSettings.setSessionOption(
            "line-number",
            jp.titze.intellij.helix.settings.HelixLineNumberMode.SMART,
        )

        // Entering INSERT mode should NOT turn on line numbers in a commit editor
        HelixLineNumberManager.applyLineNumbers(editor, HelixMode.INSERT)
        editor.settings.isLineNumbersShown.shouldBeFalse()

        // Nor in NORMAL mode
        HelixLineNumberManager.applyLineNumbers(editor, HelixMode.NORMAL)
        editor.settings.isLineNumbersShown.shouldBeFalse()

        commitPanel.remove(editor.component)
    }

    fun testAiPromptEditorIsNotActiveAndLineNumbersAreNotApplied() {
        myFixture.configureByText("test.txt", "prompt query")
        val editor = myFixture.editor
        editor.settings.isLineNumbersShown = false

        val promptPanel = com.intellij.air.frontend.prompt.ui.MockAgentPromptPanel()
        promptPanel.add(editor.component)

        try {
            HelixEditorEligibility.isAiPromptEditor(editor).shouldBeTrue()
            HelixSettings.instance.activateInAiPrompt = false
            HelixEditorEligibility.isActive(editor).shouldBeFalse()

            // When explicitly enabled, it becomes active
            HelixSettings.instance.activateInAiPrompt = true
            HelixEditorEligibility.isActive(editor).shouldBeTrue()

            HelixEffectiveSettings.setSessionOption(
                "line-number",
                jp.titze.intellij.helix.settings.HelixLineNumberMode.SMART,
            )

            // Line numbers must still never be applied to AI prompt editors
            HelixLineNumberManager.applyLineNumbers(editor, HelixMode.NORMAL)
            editor.settings.isLineNumbersShown.shouldBeFalse()

            HelixLineNumberManager.applyLineNumbers(editor, HelixMode.INSERT)
            editor.settings.isLineNumbersShown.shouldBeFalse()
        } finally {
            HelixSettings.instance.activateInAiPrompt = false
            promptPanel.remove(editor.component)
        }
    }

    fun testEditorTextFieldIsNotActiveWhenActivateInOtherEditorsIsFalse() {
        myFixture.configureByText("test.txt", "editor text field content")
        val editor = myFixture.editor
        HelixSettings.instance.activateInOtherEditors = false
        HelixSettings.instance.activateInAiPrompt = false

        val field = com.intellij.ui.EditorTextField::class.java.getDeclaredField("MANAGED_BY_FIELD")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val key = field.get(null) as com.intellij.openapi.util.Key<Boolean>

        editor.putUserData(key, true)
        try {
            HelixEditorEligibility.isEditorTextField(editor).shouldBeTrue()
            HelixEditorEligibility.isActive(editor).shouldBeFalse()

            // When activateInOtherEditors is true, it still won't be active if it's an AI prompt editor and activateInAiPrompt is false
            val promptPanel = com.intellij.air.frontend.prompt.ui.MockAgentPromptPanel()
            promptPanel.add(editor.component)
            HelixSettings.instance.activateInOtherEditors = true
            HelixEditorEligibility.isActive(editor).shouldBeFalse()

            // But if activateInAiPrompt is true, it becomes active
            HelixSettings.instance.activateInAiPrompt = true
            HelixEditorEligibility.isActive(editor).shouldBeTrue()

            promptPanel.remove(editor.component)
        } finally {
            HelixSettings.instance.activateInOtherEditors = false
            HelixSettings.instance.activateInAiPrompt = false
            editor.putUserData(key, null)
        }
    }

    fun testReadOnlyViewerEligibilityAndToggle() {
        myFixture.configureByText("viewer.txt", "hello world")
        val editor = myFixture.editor as EditorEx
        editor.isViewer = true
        try {
            HelixSettings.instance.activateInReadOnlyViewers = true
            HelixEditorEligibility.isActive(editor).shouldBeTrue()

            HelixSettings.instance.activateInReadOnlyViewers = false
            HelixEditorEligibility.isActive(editor).shouldBeFalse()
        } finally {
            editor.isViewer = false
        }

        val diffViewer = EditorFactory.getInstance().createViewer(editor.document, project, EditorKind.DIFF)
        try {
            HelixSettings.instance.activateInDiff = true
            HelixSettings.instance.activateInReadOnlyViewers = true
            HelixEditorEligibility.isActive(diffViewer).shouldBeTrue()

            HelixSettings.instance.activateInReadOnlyViewers = false
            HelixEditorEligibility.isActive(diffViewer).shouldBeFalse()

            HelixSettings.instance.activateInReadOnlyViewers = true
            HelixSettings.instance.activateInDiff = false
            HelixEditorEligibility.isActive(diffViewer).shouldBeFalse()
        } finally {
            EditorFactory.getInstance().releaseEditor(diffViewer)
        }
    }

    fun testIsReadOnlyAndEnsureWritable() {
        myFixture.configureByText("ro_guard.txt", "hello world")
        val editor = myFixture.editor as EditorEx
        val notices = mutableListOf<String>()
        HelixEditorEligibility.onReadOnlyNotice = { _, message -> notices.add(message) }

        HelixEditorEligibility.isReadOnly(editor).shouldBeFalse()
        HelixEditorEligibility.ensureWritable(editor).shouldBeTrue()
        notices shouldBe emptyList()

        editor.isViewer = true
        try {
            HelixEditorEligibility.isReadOnly(editor).shouldBeTrue()
            HelixEditorEligibility.ensureWritable(editor).shouldBeFalse()
            notices shouldBe listOf("Buffer is read-only")
        } finally {
            editor.isViewer = false
        }

        notices.clear()
        editor.document.setReadOnly(true)
        try {
            HelixEditorEligibility.isReadOnly(editor).shouldBeTrue()
            HelixEditorEligibility.ensureWritable(editor).shouldBeFalse()
            notices shouldBe listOf("Buffer is read-only")
        } finally {
            editor.document.setReadOnly(false)
        }
    }
}
