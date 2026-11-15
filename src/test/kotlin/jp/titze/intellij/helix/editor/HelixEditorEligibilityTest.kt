// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.editor

import com.intellij.openapi.editor.textarea.TextComponentEditorImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import javax.swing.JTextArea

class HelixEditorEligibilityTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixSettings.instance.enabled = true
        HelixSettings.instance.activateInOtherEditors = true
    }

    override fun tearDown() {
        HelixSettings.instance.enabled = true
        HelixSettings.instance.activateInOtherEditors = false
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
}
