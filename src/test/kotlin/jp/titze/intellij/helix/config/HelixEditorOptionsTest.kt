// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.config

import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.action.HelixSearchActions
import jp.titze.intellij.helix.editor.HelixEditorEligibility
import jp.titze.intellij.helix.motion.HelixJumpToWord
import jp.titze.intellij.helix.settings.HelixCursorShape
import jp.titze.intellij.helix.settings.HelixEffectiveSettings
import jp.titze.intellij.helix.settings.HelixLineNumberMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import java.io.File

class HelixEditorOptionsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixEffectiveSettings.clearAllOverrides()
        HelixSettings.instance.customConfigPath = ""
    }

    override fun tearDown() {
        HelixEffectiveSettings.clearAllOverrides()
        val settings = HelixSettings.instance
        settings.lineNumberMode = HelixLineNumberMode.IDE_DEFAULT
        settings.colorModes = true
        settings.cursorTint = true
        settings.cursorShapeNormal = HelixCursorShape.BLOCK
        settings.cursorShapeInsert = HelixCursorShape.BAR
        settings.cursorShapeSelect = HelixCursorShape.BLOCK
        settings.searchSmartCase = true
        settings.searchWrapAround = true
        settings.jumpLabelAlphabet = "jfkdlsaurieowpqnvmcxz"
        settings.customConfigPath = ""
        super.tearDown()
    }

    fun testParseEditorScalarsAndArrays() {
        val toml = """
            [editor]
            scrolloff = 8
            cursorline = true
            rulers = [80, 120]
            line-number = "relative"
            color-modes = false
            cursor-tint = false
            jump-label-alphabet = "fdsajklgh"
        """.trimIndent()

        val result = HelixTomlParser.parse(toml)
        result.editorOptions.keys shouldContain "scrolloff"
        result.editorOptions["scrolloff"]?.value shouldBe 8L
        result.editorOptions["cursorline"]?.value shouldBe true
        result.editorOptions["rulers"]?.value shouldBe listOf(80L, 120L)
        result.editorOptions["line-number"]?.value shouldBe "relative"
        result.editorOptions["color-modes"]?.value shouldBe false
        result.editorOptions["cursor-tint"]?.value shouldBe false
        result.editorOptions["jump-label-alphabet"]?.value shouldBe "fdsajklgh"

        // Verify line numbers
        result.editorOptions["scrolloff"]?.lineNumber shouldBe 2
        result.editorOptions["cursorline"]?.lineNumber shouldBe 3
    }

    fun testParseDottedKeysAndSubTables() {
        val toml = """
            [editor]
            search.smart-case = false
            search.wrap-around = false
            soft-wrap.enable = true
            whitespace.render = "all"
            indent-guides.render = true

            [editor.cursor-shape]
            normal = "block"
            insert = "bar"
            select = "underline"
        """.trimIndent()

        val result = HelixTomlParser.parse(toml)
        result.editorOptions["search.smart-case"]?.value shouldBe false
        result.editorOptions["search.wrap-around"]?.value shouldBe false
        result.editorOptions["soft-wrap.enable"]?.value shouldBe true
        result.editorOptions["whitespace.render"]?.value shouldBe "all"
        result.editorOptions["indent-guides.render"]?.value shouldBe true
        result.editorOptions["cursor-shape.normal"]?.value shouldBe "block"
        result.editorOptions["cursor-shape.insert"]?.value shouldBe "bar"
        result.editorOptions["cursor-shape.select"]?.value shouldBe "underline"
    }

    fun testParseInlineTables() {
        val toml = """
            [editor]
            cursor-shape = { normal = "underline", insert = "hidden", select = "block" }
            search = { smart-case = false, wrap-around = true }
        """.trimIndent()

        val result = HelixTomlParser.parse(toml)
        result.editorOptions["cursor-shape.normal"]?.value shouldBe "underline"
        result.editorOptions["cursor-shape.insert"]?.value shouldBe "hidden"
        result.editorOptions["cursor-shape.select"]?.value shouldBe "block"
        result.editorOptions["search.smart-case"]?.value shouldBe false
        result.editorOptions["search.wrap-around"]?.value shouldBe true
    }

    fun testValidationDiagnostics() {
        val toml = """
            [editor]
            scrolloff = "invalid_number"
            line-number = "unknown_mode"
            true-color = true
            lsp.display-messages = true
            non-existent-option = 123
        """.trimIndent()

        val result = HelixTomlParser.parse(toml)
        val tempFile = File.createTempFile("helix-test-options", ".toml")
        try {
            tempFile.writeText(toml)
            val overrides = HelixEditorOptions.evaluateTomlOptions(result.editorOptions, tempFile)

            // Invalid options
            overrides["scrolloff"]?.status shouldBe OptionStatus.INVALID
            overrides["line-number"]?.status shouldBe OptionStatus.INVALID

            // Inapplicable CLI options
            overrides["true-color"]?.status shouldBe OptionStatus.NOT_APPLICABLE
            overrides["lsp.display-messages"]?.status shouldBe OptionStatus.NOT_APPLICABLE

            // Ignored/unknown option
            overrides["non-existent-option"]?.status shouldBe OptionStatus.IGNORED

            // Config validation diagnostics
            val configResult = HelixConfigLoader.validateConfig(toml)
            configResult.errors.any { it.contains("scrolloff") }.shouldBeTrue()
            configResult.errors.any { it.contains("line-number") }.shouldBeTrue()
            configResult.errors.any { it.contains("non-existent-option") }.shouldBeTrue()
            // Inapplicable options should NOT generate warning diagnostics
            configResult.errors.any { it.contains("true-color") }.shouldBeFalse()
        } finally {
            tempFile.delete()
        }
    }

    fun testPrecedenceHierarchy() {
        val settings = HelixSettings.instance
        settings.searchSmartCase = true

        // 1. Built-in / Settings UI value
        HelixEffectiveSettings.searchSmartCase.shouldBeTrue()
        HelixEffectiveSettings.isOverridden("search.smart-case").shouldBeFalse()

        // 2. TOML Override
        val tempFile = File.createTempFile("helix-test-precedence", ".toml")
        try {
            val tomlOverride = HelixOptionOverride(
                key = "search.smart-case",
                value = false,
                rawValueString = "false",
                sourceFile = tempFile,
                lineNumber = 5,
                status = OptionStatus.APPLIED,
            )
            HelixEffectiveSettings.applyTomlOverrides(mapOf("search.smart-case" to tomlOverride))

            HelixEffectiveSettings.isOverridden("search.smart-case").shouldBeTrue()
            HelixEffectiveSettings.searchSmartCase.shouldBeFalse()
            // Verify non-destructive: HelixSettings persisted value is untouched
            settings.searchSmartCase.shouldBeTrue()

            // 3. Session override (:set-option) wins over TOML
            HelixEffectiveSettings.setSessionOverride("search.smart-case", true)
            HelixEffectiveSettings.isOverridden("search.smart-case").shouldBeTrue()
            HelixEffectiveSettings.searchSmartCase.shouldBeTrue()
            settings.searchSmartCase.shouldBeTrue()

            // Clear session override -> falls back to TOML
            HelixEffectiveSettings.clearSessionOverride("search.smart-case")
            HelixEffectiveSettings.searchSmartCase.shouldBeFalse()

            // Clear TOML overrides -> falls back to Settings UI
            HelixEffectiveSettings.clearTomlOverrides()
            HelixEffectiveSettings.isOverridden("search.smart-case").shouldBeFalse()
            HelixEffectiveSettings.searchSmartCase.shouldBeTrue()
        } finally {
            tempFile.delete()
        }
    }

    fun testEditorApplication() {
        myFixture.configureByText("test.txt", "line 1\nline 2\nline 3\n")
        val editor = myFixture.editor

        val toml = """
            [editor]
            scrolloff = 12
            cursorline = true
            rulers = [72, 100]
            soft-wrap.enable = true
            whitespace.render = "all"
            indent-guides.render = true
            line-number = "relative"
        """.trimIndent()

        val parsed = HelixTomlParser.parse(toml)
        val tempFile = File.createTempFile("helix-test-apply", ".toml")
        try {
            tempFile.writeText(toml)
            val overrides = HelixEditorOptions.evaluateTomlOptions(parsed.editorOptions, tempFile)
            HelixEffectiveSettings.applyTomlOverrides(overrides)
            HelixEffectiveSettings.applyAllToEditor(editor)

            editor.settings.verticalScrollOffset shouldBe 12
            editor.settings.isCaretRowShown.shouldBeTrue()
            editor.settings.softMargins shouldBe listOf(72, 100)
            editor.settings.isUseSoftWraps.shouldBeTrue()
            editor.settings.isWhitespacesShown.shouldBeTrue()
            editor.settings.isIndentGuidesShown.shouldBeTrue()
            HelixEffectiveSettings.lineNumberMode shouldBe HelixLineNumberMode.RELATIVE
        } finally {
            tempFile.delete()
        }
    }

    fun testCursorShapesPerMode() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor

        HelixEffectiveSettings.setSessionOverride("cursor-shape.normal", HelixCursorShape.BLOCK)
        HelixEffectiveSettings.setSessionOverride("cursor-shape.insert", HelixCursorShape.BAR)
        HelixEffectiveSettings.setSessionOverride("cursor-shape.select", HelixCursorShape.UNDERLINE)

        HelixStateManager.updateCursor(editor, HelixMode.NORMAL)
        editor.caretModel.primaryCaret.visualAttributes.shape shouldBe CaretVisualAttributes.Shape.BLOCK

        HelixStateManager.updateCursor(editor, HelixMode.INSERT)
        editor.caretModel.primaryCaret.visualAttributes.shape shouldBe CaretVisualAttributes.Shape.BAR

        HelixStateManager.updateCursor(editor, HelixMode.SELECT)
        editor.caretModel.primaryCaret.visualAttributes.shape shouldBe CaretVisualAttributes.Shape.UNDERSCORE

        // Hidden cursor shape test
        HelixEffectiveSettings.setSessionOverride("cursor-shape.insert", HelixCursorShape.HIDDEN)
        HelixStateManager.updateCursor(editor, HelixMode.INSERT)
        editor.caretModel.primaryCaret.visualAttributes.shape shouldBe CaretVisualAttributes.Shape.BAR
        editor.caretModel.primaryCaret.visualAttributes.thickness shouldBe 0.0f

        // Deactivation resets attributes
        HelixEditorEligibility.deactivate(editor)
        editor.caretModel.primaryCaret.visualAttributes shouldBe CaretVisualAttributes.getDefault()
    }

    fun testSearchSmartCaseAndWrapAround() {
        myFixture.configureByText("test.txt", "Apple apple APPLE")

        // Smart-case enabled: all lowercase is case-insensitive
        HelixEffectiveSettings.setSessionOverride("search.smart-case", true)
        val regexLower = HelixSearchActions.compileRegex("apple")
        regexLower.find("APPLE").shouldNotBeNull()

        // Smart-case enabled: uppercase present is case-sensitive
        val regexMixed = HelixSearchActions.compileRegex("Apple")
        regexMixed.find("apple").shouldBeNull()
        regexMixed.find("Apple").shouldNotBeNull()

        // Smart-case disabled: lowercase stays case-sensitive
        HelixEffectiveSettings.setSessionOverride("search.smart-case", false)
        val regexStrict = HelixSearchActions.compileRegex("apple")
        regexStrict.find("APPLE").shouldBeNull()

        // Wrap around test
        HelixEffectiveSettings.setSessionOverride("search.wrap-around", false)
        HelixEffectiveSettings.searchWrapAround.shouldBeFalse()
        HelixEffectiveSettings.setSessionOverride("search.wrap-around", true)
        HelixEffectiveSettings.searchWrapAround.shouldBeTrue()
    }

    fun testJumpLabelAlphabet() {
        HelixEffectiveSettings.setSessionOverride("jump-label-alphabet", "xyz")
        HelixEffectiveSettings.jumpLabelAlphabet shouldBe "xyz"

        val labels = HelixJumpToWord.generateLabels(3)
        labels shouldBe listOf("x", "y", "z")
    }

    fun testSetOptionRuntimeCommand() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor

        val success = HelixEditorOptions.setOption("scrolloff", "15", editor)
        success.shouldBeTrue()
        HelixEffectiveSettings.scrolloff shouldBe 15
        editor.settings.verticalScrollOffset shouldBe 15

        val invalid = HelixEditorOptions.setOption("scrolloff", "not_a_number", editor)
        invalid.shouldBeFalse()
    }
}
