// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.editor

import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class HelixThreadingAndInjectedEditorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixSettings.instance.enabled = true
    }

    private fun createMockEditorWindow(hostEditor: Editor): EditorWindow = Proxy.newProxyInstance(
        EditorWindow::class.java.classLoader,
        arrayOf(EditorWindow::class.java, Editor::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getDelegate" -> hostEditor

            "isDisposed" -> hostEditor.isDisposed

            "getUserData" -> null

            "putUserData" -> null

            "toString" -> "MockEditorWindow(delegate=$hostEditor)"

            else -> {
                val targetMethod = try {
                    hostEditor.javaClass.getMethod(method.name, *(method.parameterTypes ?: emptyArray()))
                } catch (_: NoSuchMethodException) {
                    null
                }
                if (targetMethod != null) {
                    targetMethod.invoke(hostEditor, *(args ?: emptyArray()))
                } else {
                    null
                }
            }
        }
    } as EditorWindow

    fun testUpdateCursorFromBackgroundThreadDoesNotThrow() {
        myFixture.configureByText("test.txt", "line 1\nline 2")
        val editor = myFixture.editor

        val exceptionRef = AtomicReference<Throwable?>()
        val thread = Thread {
            try {
                HelixStateManager.updateCursor(editor, HelixMode.NORMAL)
                HelixStateManager.updateCursor(editor, HelixMode.INSERT)
            } catch (t: Throwable) {
                exceptionRef.set(t)
            }
        }
        thread.start()
        thread.join(5000)

        exceptionRef.get() shouldBe null
    }

    fun testGetOrCreateFromBackgroundThreadDoesNotThrow() {
        myFixture.configureByText("test_bg.txt", "abc")
        val editor = myFixture.editor

        val exceptionRef = AtomicReference<Throwable?>()
        val thread = Thread {
            try {
                val state = HelixStateManager.getOrCreate(editor)
                state.mode shouldBe HelixMode.NORMAL
            } catch (t: Throwable) {
                exceptionRef.set(t)
            }
        }
        thread.start()
        thread.join(5000)

        exceptionRef.get() shouldBe null
    }

    fun testActionHandlerIsEnabledForCaretFromBackgroundThread() {
        myFixture.configureByText("test_enter.txt", "foo\nbar")
        val editor = myFixture.editor
        val caret = editor.caretModel.primaryCaret

        val enterHandler = HelixEditorActionHandler(IdeActions.ACTION_EDITOR_ENTER, null)

        val resultRef = AtomicBoolean(false)
        val exceptionRef = AtomicReference<Throwable?>()
        val thread = Thread {
            try {
                resultRef.set(enterHandler.isEnabled(editor, caret, null))
            } catch (t: Throwable) {
                exceptionRef.set(t)
            }
        }
        thread.start()
        thread.join(5000)

        exceptionRef.get() shouldBe null
        resultRef.get().shouldBeTrue()
    }

    fun testInjectedEditorWindowDelegatesToHostEditor() {
        myFixture.configureByText("host.txt", "select * from table")
        val hostEditor = myFixture.editor
        val mockEditorWindow = createMockEditorWindow(hostEditor)

        HelixStateManager.resolveHostEditor(mockEditorWindow) shouldBe hostEditor

        val hostState = HelixStateManager.getOrCreate(hostEditor)
        val injectedState = HelixStateManager.getOrCreate(mockEditorWindow as Editor)

        injectedState shouldBe hostState

        hostState.setMode(HelixMode.INSERT)
        HelixStateManager.get(mockEditorWindow as Editor)?.mode shouldBe HelixMode.INSERT

        HelixEditorEligibility.isActive(mockEditorWindow as Editor).shouldBeTrue()
    }

    fun testEnterActionOnInjectedEditorInInsertModeDelegatesToOriginalHandler() {
        myFixture.configureByText("host_ins.txt", "content")
        val hostEditor = myFixture.editor
        val mockEditorWindow = createMockEditorWindow(hostEditor)
        val hostState = HelixStateManager.getOrCreate(hostEditor)
        hostState.setMode(HelixMode.INSERT)

        val originalExecuted = AtomicBoolean(false)
        val originalHandler = object : EditorActionHandler() {
            override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
                originalExecuted.set(true)
            }

            override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean = true
        }

        val handler = HelixEditorActionHandler(IdeActions.ACTION_EDITOR_ENTER, originalHandler)
        val caret = hostEditor.caretModel.primaryCaret

        handler.isEnabled(mockEditorWindow as Editor, caret, DataContext.EMPTY_CONTEXT).shouldBeTrue()
        handler.execute(mockEditorWindow as Editor, caret, DataContext.EMPTY_CONTEXT)

        originalExecuted.get().shouldBeTrue()
    }

    fun testEnterActionOnInjectedEditorInNormalModeDoesNotDelegateToOriginalHandler() {
        myFixture.configureByText("host_nor.txt", "content")
        val hostEditor = myFixture.editor
        val mockEditorWindow = createMockEditorWindow(hostEditor)
        val hostState = HelixStateManager.getOrCreate(hostEditor)
        hostState.setMode(HelixMode.NORMAL)

        val originalExecuted = AtomicBoolean(false)
        val originalHandler = object : EditorActionHandler() {
            override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
                originalExecuted.set(true)
            }

            override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean = true
        }

        val handler = HelixEditorActionHandler(IdeActions.ACTION_EDITOR_ENTER, originalHandler)
        val caret = hostEditor.caretModel.primaryCaret

        handler.execute(mockEditorWindow as Editor, caret, DataContext.EMPTY_CONTEXT)

        originalExecuted.get().shouldBeFalse()
    }
}
