// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.config.HelixConfigLoader
import jp.titze.intellij.helix.config.HelixOptionOverride
import jp.titze.intellij.helix.config.OptionStatus
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.keymap.HelixKeyContext
import jp.titze.intellij.helix.keymap.HelixKeyOwner
import jp.titze.intellij.helix.keymap.HelixShortcutPolicy
import jp.titze.intellij.helix.ui.HelixTheme

class HelixSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        // Reset settings to defaults
        val settings = HelixSettings.instance
        settings.searchUiMode = HelixSearchUiMode.STOCK_HELIX
        settings.lineNavigationMode = HelixLineNavigationMode.HELIX_STANDARD
        settings.lineNumberMode = HelixLineNumberMode.IDE_DEFAULT
        settings.jumpListMaxEntries = HelixSettings.DEFAULT_JUMP_LIST_MAX_ENTRIES
        settings.promptHistoryMaxEntries = HelixSettings.DEFAULT_PROMPT_HISTORY_MAX_ENTRIES
        settings.colorTheme = HelixColorTheme.SYNC
        settings.colorModes = true
        settings.cursorTint = true
        settings.resetToNormalOnTabSwitch = true
        settings.whichKeyColumnLayout = WhichKeyColumnLayout.AUTO
        settings.whichKeyMaxRows = 0
        settings.ctrlKeyOverrides = emptyMap()
        settings.cursorShapeNormal = HelixCursorShape.BLOCK
        settings.cursorShapeInsert = HelixCursorShape.BAR
        settings.cursorShapeSelect = HelixCursorShape.BLOCK
        settings.searchSmartCase = true
        settings.searchWrapAround = true
        settings.jumpLabelAlphabet = "jfkdlsaurieowpqnvmcxz"
        settings.toolWindowNavigationEnabled = false
        settings.toolWindowSeamlessFocusJump = true
        settings.toolWindowSearchConfirmOnEnter = true
        settings.toolWindowSpaceSwitcher = true
        settings.toolWindowCommitSpaceSwitcher = true
        settings.cliConfigFallbackMode = HelixCliConfigFallbackMode.KEYMAPS_ONLY
        HelixEffectiveSettings.clearAllOverrides()
        jp.titze.intellij.helix.ui.HelixPromptHistory.clear()
        super.tearDown()
    }

    fun testDefaultSettings() {
        val settings = HelixSettings.instance
        settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX
        settings.lineNavigationMode shouldBe HelixLineNavigationMode.HELIX_STANDARD
        settings.jumpListMaxEntries shouldBe 100
        settings.promptHistoryMaxEntries shouldBe 100
        settings.colorTheme shouldBe HelixColorTheme.SYNC
        settings.colorModes.shouldBeTrue()
        settings.cursorTint.shouldBeTrue()
        settings.lineNumberMode shouldBe HelixLineNumberMode.IDE_DEFAULT
        settings.resetToNormalOnTabSwitch.shouldBeTrue()
        settings.whichKeyColumnLayout shouldBe WhichKeyColumnLayout.AUTO
        settings.whichKeyMaxRows shouldBe 0
        settings.cursorShapeNormal shouldBe HelixCursorShape.BLOCK
        settings.cursorShapeInsert shouldBe HelixCursorShape.BAR
        settings.cursorShapeSelect shouldBe HelixCursorShape.BLOCK
        settings.searchSmartCase.shouldBeTrue()
        settings.searchWrapAround.shouldBeTrue()
        settings.jumpLabelAlphabet shouldBe "jfkdlsaurieowpqnvmcxz"
        settings.toolWindowNavigationEnabled.shouldBeFalse()
        settings.toolWindowSeamlessFocusJump.shouldBeTrue()
        settings.toolWindowSearchConfirmOnEnter.shouldBeTrue()
        settings.toolWindowSpaceSwitcher.shouldBeTrue()
        settings.toolWindowCommitSpaceSwitcher.shouldBeTrue()
        settings.cliConfigFallbackMode shouldBe HelixCliConfigFallbackMode.KEYMAPS_ONLY
    }

    fun testLineNumberModePersistence() {
        val settings = HelixSettings.instance
        settings.lineNumberMode = HelixLineNumberMode.SMART
        settings.lineNumberMode shouldBe HelixLineNumberMode.SMART

        settings.lineNumberMode = HelixLineNumberMode.RELATIVE
        settings.lineNumberMode shouldBe HelixLineNumberMode.RELATIVE

        settings.lineNumberMode = HelixLineNumberMode.HYBRID
        settings.lineNumberMode shouldBe HelixLineNumberMode.HYBRID

        settings.lineNumberMode = HelixLineNumberMode.PURE_RELATIVE
        settings.lineNumberMode shouldBe HelixLineNumberMode.PURE_RELATIVE

        settings.lineNumberMode = HelixLineNumberMode.ABSOLUTE
        settings.lineNumberMode shouldBe HelixLineNumberMode.ABSOLUTE

        settings.lineNumberMode = HelixLineNumberMode.OFF
        settings.lineNumberMode shouldBe HelixLineNumberMode.OFF
    }

    fun testWhichKeyLayoutAndMaxRowsPersistence() {
        val settings = HelixSettings.instance

        settings.whichKeyColumnLayout = WhichKeyColumnLayout.FOUR_COLUMNS
        settings.whichKeyColumnLayout shouldBe WhichKeyColumnLayout.FOUR_COLUMNS

        settings.whichKeyColumnLayout = WhichKeyColumnLayout.SINGLE_COLUMN
        settings.whichKeyColumnLayout shouldBe WhichKeyColumnLayout.SINGLE_COLUMN

        settings.whichKeyColumnLayout = WhichKeyColumnLayout.AUTO
        settings.whichKeyColumnLayout shouldBe WhichKeyColumnLayout.AUTO

        settings.whichKeyMaxRows = 8
        settings.whichKeyMaxRows shouldBe 8

        settings.whichKeyMaxRows = -5
        settings.whichKeyMaxRows shouldBe 0

        settings.whichKeyMaxRows = 100
        settings.whichKeyMaxRows shouldBe 50
    }

    fun testJumpListMaxEntriesClamping() {
        val settings = HelixSettings.instance

        settings.jumpListMaxEntries = 50
        settings.jumpListMaxEntries shouldBe 50

        settings.jumpListMaxEntries = 5
        settings.jumpListMaxEntries shouldBe HelixSettings.MIN_JUMP_LIST_ENTRIES

        settings.jumpListMaxEntries = 5000
        settings.jumpListMaxEntries shouldBe HelixSettings.MAX_JUMP_LIST_ENTRIES
    }

    fun testColorThemeSettingsAndResolution() {
        val settings = HelixSettings.instance

        settings.colorTheme = HelixColorTheme.DARK
        settings.colorTheme shouldBe HelixColorTheme.DARK
        HelixTheme.isDark.shouldBeTrue()

        settings.colorTheme = HelixColorTheme.LIGHT
        settings.colorTheme shouldBe HelixColorTheme.LIGHT
        HelixTheme.isDark.shouldBeFalse()

        settings.colorTheme = HelixColorTheme.SYNC
        settings.colorTheme shouldBe HelixColorTheme.SYNC
    }

    fun testConfigurableModificationsAndApply() {
        val configurable = HelixConfigurable()
        configurable.createComponent()

        val settings = HelixSettings.instance
        settings.jumpListMaxEntries = 100
        settings.colorTheme = HelixColorTheme.SYNC
        configurable.reset()
        configurable.isModified.shouldBeFalse()

        settings.jumpListMaxEntries = 50
        configurable.isModified.shouldBeTrue()

        configurable.reset()
        configurable.isModified.shouldBeFalse()

        settings.promptHistoryMaxEntries = 50
        configurable.isModified.shouldBeTrue()

        configurable.reset()
        configurable.isModified.shouldBeFalse()

        settings.resetToNormalOnTabSwitch = false
        configurable.isModified.shouldBeTrue()

        configurable.reset()
        configurable.isModified.shouldBeFalse()

        settings.lineNavigationMode = HelixLineNavigationMode.VIM_STANDARD
        configurable.isModified.shouldBeTrue()

        configurable.reset()
        configurable.isModified.shouldBeFalse()

        configurable.disposeUIResources()
    }

    fun testLineNavigationModeSetting() {
        val settings = HelixSettings.instance
        settings.lineNavigationMode shouldBe HelixLineNavigationMode.HELIX_STANDARD

        settings.lineNavigationMode = HelixLineNavigationMode.VIM_STANDARD
        settings.lineNavigationMode shouldBe HelixLineNavigationMode.VIM_STANDARD

        settings.lineNavigationMode = HelixLineNavigationMode.HELIX_STANDARD
        settings.lineNavigationMode shouldBe HelixLineNavigationMode.HELIX_STANDARD
    }

    fun testCustomJumpListCapacityEnforced() {
        val settings = HelixSettings.instance
        settings.jumpListMaxEntries = 20

        myFixture.configureByText("limit_custom.txt", (1..50).joinToString("\n") { "line $it" })
        val editor = myFixture.editor
        val jumpService = HelixJumpListService.getInstance(myFixture.project)
        jumpService.clear()

        for (i in 0..30) {
            editor.caretModel.primaryCaret.moveToOffset(editor.document.getLineStartOffset(i))
            jumpService.recordCurrent(editor, force = true)
        }

        jumpService.getEntries().size shouldBe 20

        // Test trimToCapacity when capacity is reduced further
        settings.jumpListMaxEntries = 12
        jumpService.trimToCapacity()
        jumpService.getEntries().size shouldBe 12
    }

    fun testPromptHistoryMaxEntriesClamping() {
        val settings = HelixSettings.instance

        settings.promptHistoryMaxEntries = 50
        settings.promptHistoryMaxEntries shouldBe 50

        settings.promptHistoryMaxEntries = 5
        settings.promptHistoryMaxEntries shouldBe HelixSettings.MIN_PROMPT_HISTORY_ENTRIES

        settings.promptHistoryMaxEntries = 5000
        settings.promptHistoryMaxEntries shouldBe HelixSettings.MAX_PROMPT_HISTORY_ENTRIES
    }

    fun testPromptHistoryCapacityAndTrim() {
        val settings = HelixSettings.instance
        settings.promptHistoryMaxEntries = 15

        repeat(25) { i ->
            jp.titze.intellij.helix.ui.HelixPromptHistory.add(
                jp.titze.intellij.helix.ui.HelixPromptCategory.SEARCH,
                "pattern-$i",
            )
        }
        val history = jp.titze.intellij.helix.ui.HelixPromptHistory.get(
            jp.titze.intellij.helix.ui.HelixPromptCategory.SEARCH,
        )
        history.size shouldBe 15

        settings.promptHistoryMaxEntries = 10
        jp.titze.intellij.helix.ui.HelixPromptHistory.trimToCapacity()
        val trimmed = jp.titze.intellij.helix.ui.HelixPromptHistory.get(
            jp.titze.intellij.helix.ui.HelixPromptCategory.SEARCH,
        )
        trimmed.size shouldBe 10
    }

    fun testHelixBundleMessageResolution() {
        HelixBundle.message("settings.displayName") shouldBe "Helix Keymap"
        HelixBundle.message("status.widget.displayName") shouldBe "Helix Mode"
        HelixBundle.message("action.jp.titze.intellij.helix.escape.text") shouldBe "Helix: Escape"

        val am = com.intellij.openapi.actionSystem.ActionManager.getInstance()
        val helixActionIds = am.getActionIdList("jp.titze.intellij.helix.")
        helixActionIds.isNotEmpty().shouldBeTrue()
        helixActionIds.forEach { actionId ->
            val text = am.getAction(actionId)?.templatePresentation?.text
            assertNotNull("Missing action text for $actionId", text)
            text!!.startsWith("Helix: ").shouldBeTrue()
        }

        // Prompts & Search
        HelixBundle.message("prompt.shell.pressEnter") shouldBe "press Enter to execute"
        HelixBundle.message("prompt.matches.count", 3) shouldBe "3 matches"
        HelixBundle.message("prompt.selections.count", 2) shouldBe "2 selections"
        HelixBundle.message("popup.search.matches.found", 5) shouldBe "5 matches found"

        // Popups
        HelixBundle.message("popup.command.title") shouldBe "COMMAND PALETTE"
        HelixBundle.message("popup.filePicker.header", "src", 42) shouldBe "Current Dir: src (42 files)"
        HelixBundle.message("popup.jumplist.title", 10) shouldBe "Jumplist (10)"
        HelixBundle.message("popup.registers.title", 8) shouldBe "Registers (8)"

        // Which-Key Menus
        HelixBundle.message("whichKey.menu.space") shouldBe "SPACE MENU"
        HelixBundle.message("whichKey.menu.goto") shouldBe "GOTO MENU"

        // Notifications & Status
        HelixBundle.message("notification.jumplist.saved", 25) shouldBe "Jump list: saved (line 25)"
        HelixBundle.message("status.directory.cwd", "/tmp") shouldBe "Working directory: /tmp"
        HelixBundle.message("status.shell.piped", 3, "sort") shouldBe "Piped 3 selection(s) through 'sort'"
    }

    fun testHelixConfigurableDisplayNameAndComponent() {
        val configurable = HelixConfigurable()
        configurable.id shouldBe "jp.titze.intellij.helix.settings"
        configurable.displayName shouldBe "Helix Keymap"
        val component = configurable.createComponent()
        assertNotNull(component)
        configurable.disposeUIResources()

        val keybindingsConfigurable = HelixKeybindingsConfigurable()
        keybindingsConfigurable.id shouldBe "jp.titze.intellij.helix.settings.keybindings"
        keybindingsConfigurable.displayName shouldBe "Keybindings"
        assertNotNull(keybindingsConfigurable.createComponent())
        keybindingsConfigurable.disposeUIResources()

        val configFileConfigurable = HelixConfigFileConfigurable()
        configFileConfigurable.id shouldBe "jp.titze.intellij.helix.settings.config"
        configFileConfigurable.displayName shouldBe "Configuration (TOML)"
        assertNotNull(configFileConfigurable.createComponent())
        configFileConfigurable.disposeUIResources()
    }

    fun testActivationScopeSettings() {
        val settings = HelixSettings.instance
        settings.enabled.shouldBeTrue()
        settings.activateInDiff.shouldBeTrue()
        settings.activateInConsole.shouldBeFalse()
        settings.activateInCommitMessage.shouldBeFalse()
        settings.activateInAiPrompt.shouldBeFalse()
        settings.activateInOtherEditors.shouldBeFalse()

        settings.activateInAiPrompt = true
        settings.activateInAiPrompt.shouldBeTrue()
        settings.activateInAiPrompt = false

        settings.enabled = false
        settings.enabled.shouldBeFalse()
        settings.enabled = true
    }

    fun testShortcutPolicyMacDefaults() {
        HelixShortcutPolicy.macOverride = true
        try {
            for (key in HelixShortcutPolicy.KEYS) {
                if (key.supports(HelixKeyContext.NORMAL)) {
                    HelixShortcutPolicy.defaultOwner(key, HelixKeyContext.NORMAL) shouldBe
                        HelixKeyOwner.HELIX
                }
                if (key.supports(HelixKeyContext.INSERT)) {
                    HelixShortcutPolicy.defaultOwner(key, HelixKeyContext.INSERT) shouldBe
                        HelixKeyOwner.HELIX
                }
            }
        } finally {
            HelixShortcutPolicy.macOverride = null
        }
    }

    fun testShortcutPolicyNonMacDefaults() {
        HelixShortcutPolicy.macOverride = false
        try {
            val ctrlC = HelixShortcutPolicy.KEYS.first { it.letter == 'C' }
            val ctrlS = HelixShortcutPolicy.KEYS.first { it.letter == 'S' }
            val ctrlD = HelixShortcutPolicy.KEYS.first { it.letter == 'D' }

            HelixShortcutPolicy.defaultOwner(ctrlC, HelixKeyContext.NORMAL) shouldBe
                HelixKeyOwner.HELIX
            HelixShortcutPolicy.defaultOwner(ctrlS, HelixKeyContext.NORMAL) shouldBe
                HelixKeyOwner.HELIX
            HelixShortcutPolicy.defaultOwner(ctrlD, HelixKeyContext.NORMAL) shouldBe
                HelixKeyOwner.HELIX

            HelixShortcutPolicy.defaultOwner(ctrlC, HelixKeyContext.INSERT) shouldBe
                HelixKeyOwner.IDE
            HelixShortcutPolicy.defaultOwner(ctrlS, HelixKeyContext.INSERT) shouldBe
                HelixKeyOwner.IDE
            HelixShortcutPolicy.defaultOwner(ctrlD, HelixKeyContext.INSERT) shouldBe
                HelixKeyOwner.IDE
        } finally {
            HelixShortcutPolicy.macOverride = null
        }
    }

    fun testShortcutPolicyOverrides() {
        val ctrlC = HelixShortcutPolicy.KEYS.first { it.letter == 'C' }
        val overrides = mapOf("NORMAL:C" to "IDE", "INSERT:C" to "HELIX")

        HelixShortcutPolicy.owner(ctrlC, HelixKeyContext.NORMAL, overrides) shouldBe HelixKeyOwner.IDE
        HelixShortcutPolicy.owner(ctrlC, HelixKeyContext.INSERT, overrides) shouldBe HelixKeyOwner.HELIX
    }

    fun testShortcutPolicyNormalization() {
        HelixShortcutPolicy.macOverride = false
        try {
            val raw = mapOf(
                "NORMAL:C" to "IDE",
                "NORMAL:INVALID" to "HELIX",
                "NORMAL:S" to "IDE",
                "INSERT:UNKNOWN" to "IDE",
                "INSERT:R" to "HELIX",
            )
            val normalized = HelixShortcutPolicy.normalize(raw)
            normalized shouldBe mapOf(
                "NORMAL:C" to "IDE",
                "NORMAL:S" to "IDE",
                "INSERT:R" to "HELIX",
            )
        } finally {
            HelixShortcutPolicy.macOverride = null
        }
    }

    fun testCtrlKeyConfigurableModification() {
        val configurable = HelixKeybindingsConfigurable()
        configurable.createComponent()
        val settings = HelixSettings.instance

        configurable.reset()
        configurable.isModified.shouldBeFalse()

        settings.ctrlKeyOverrides = mapOf("NORMAL:C" to "IDE")
        configurable.isModified.shouldBeTrue()

        configurable.reset()
        configurable.isModified.shouldBeFalse()
        configurable.disposeUIResources()
    }

    fun testCustomConfigPathInSettingsAndConfigurable() {
        val configurable = HelixConfigFileConfigurable()
        configurable.createComponent()
        val settings = HelixSettings.instance
        val originalPath = settings.customConfigPath

        try {
            settings.customConfigPath = ""
            configurable.reset()
            configurable.isModified.shouldBeFalse()

            settings.customConfigPath = "/custom/path/config.toml"
            configurable.isModified.shouldBeTrue()

            configurable.reset()
            configurable.isModified.shouldBeFalse()
        } finally {
            settings.customConfigPath = originalPath
            configurable.disposeUIResources()
        }
    }

    fun testHelixConfigPathPanelStatus() {
        val panel = HelixConfigPathPanel()
        panel.pathField.text = "/non/existent/path/config.toml"
        panel.refreshStatus()
        panel.statusLabel.text.shouldContain("No config file found")

        val tempFile = java.io.File.createTempFile("helix-test-config", ".toml")
        try {
            tempFile.writeText("[keys.normal]\nesc = \"collapse_selection\"\n")
            panel.pathField.text = tempFile.absolutePath
            panel.refreshStatus()
            panel.statusLabel.text.shouldContain("1 custom bindings loaded")
        } finally {
            tempFile.delete()
        }
    }

    fun testHelixConfigPathPanelShowsProjectOverrides() {
        val basePath = project.basePath ?: error("Project basePath missing")
        val helixDir = java.io.File(basePath, ".helix")
        helixDir.mkdirs()
        val projectToml = java.io.File(helixDir, "intellij.toml")
        projectToml.writeText(
            """
            [editor]
            rulers = [80, 120]
            line-number = "relative"
            """.trimIndent(),
        )

        try {
            HelixConfigLoader.loadProjectConfig(project)
            val panel = HelixConfigPathPanel()
            panel.pathField.text = ""
            panel.refreshStatus()

            panel.overridesTableModel.rowCount shouldBe 2
            val keys = (0 until panel.overridesTableModel.rowCount).map {
                panel.overridesTableModel.getValueAt(it, 0) as String
            }
            keys.contains("rulers").shouldBeTrue()
            keys.contains("line-number").shouldBeTrue()

            val sources = (0 until panel.overridesTableModel.rowCount).map {
                panel.overridesTableModel.getValueAt(it, 2) as String
            }
            sources.all { it.startsWith(".helix/") }.shouldBeTrue()
        } finally {
            projectToml.delete()
            helixDir.delete()
            HelixConfigLoader.unloadProjectConfig(project)
        }
    }

    fun testCursorShapePersistence() {
        val settings = HelixSettings.instance
        settings.cursorShapeNormal = HelixCursorShape.UNDERLINE
        settings.cursorShapeNormal shouldBe HelixCursorShape.UNDERLINE

        settings.cursorShapeInsert = HelixCursorShape.HIDDEN
        settings.cursorShapeInsert shouldBe HelixCursorShape.HIDDEN

        settings.cursorShapeSelect = HelixCursorShape.BAR
        settings.cursorShapeSelect shouldBe HelixCursorShape.BAR
    }

    fun testSearchAndJumpSettingsPersistence() {
        val settings = HelixSettings.instance
        settings.searchSmartCase = false
        settings.searchSmartCase.shouldBeFalse()

        settings.searchWrapAround = false
        settings.searchWrapAround.shouldBeFalse()

        settings.jumpLabelAlphabet = "asdf"
        settings.jumpLabelAlphabet shouldBe "asdf"
    }

    fun testEffectiveSettingsOverriddenNoticeInPanels() {
        val appearancePanel = HelixAppearancePanel()
        val behaviorPanel = HelixBehaviorPanel()

        // Initially no overrides
        appearancePanel.lineNumberModeComboBox?.isEnabled?.shouldBeTrue()
        appearancePanel.lineNumberNotice?.isVisible?.shouldBeFalse()
        behaviorPanel.searchSmartCaseCheckBox?.isEnabled?.shouldBeTrue()
        behaviorPanel.searchSmartCaseNotice?.isVisible?.shouldBeFalse()

        // Apply overrides
        val overrides = mapOf(
            "line-number" to HelixOptionOverride(
                key = "line-number",
                value = HelixLineNumberMode.RELATIVE,
                rawValueString = "relative",
                sourceFile = java.io.File("/path/to/intellij.toml"),
                lineNumber = 10,
                status = OptionStatus.APPLIED,
            ),
            "search.smart-case" to HelixOptionOverride(
                key = "search.smart-case",
                value = false,
                rawValueString = "false",
                sourceFile = java.io.File("/path/to/intellij.toml"),
                lineNumber = 15,
                status = OptionStatus.APPLIED,
            ),
        )
        HelixEffectiveSettings.applyTomlOverrides(overrides)

        // Reset panels to update overridden UI states
        appearancePanel.reset()
        behaviorPanel.reset()

        appearancePanel.lineNumberModeComboBox?.isEnabled?.shouldBeFalse()
        appearancePanel.lineNumberNotice?.isVisible?.shouldBeTrue()
        behaviorPanel.searchSmartCaseCheckBox?.isEnabled?.shouldBeFalse()
        behaviorPanel.searchSmartCaseNotice?.isVisible?.shouldBeTrue()

        // Clear overrides
        HelixEffectiveSettings.clearTomlOverrides()
        appearancePanel.reset()
        behaviorPanel.reset()

        appearancePanel.lineNumberModeComboBox?.isEnabled?.shouldBeTrue()
        appearancePanel.lineNumberNotice?.isVisible?.shouldBeFalse()
        behaviorPanel.searchSmartCaseCheckBox?.isEnabled?.shouldBeTrue()
        behaviorPanel.searchSmartCaseNotice?.isVisible?.shouldBeFalse()
    }

    fun testToolWindowSeamlessFocusJumpSettingsPanel() {
        val settings = HelixSettings.instance
        settings.toolWindowSeamlessFocusJump = false
        settings.toolWindowSeamlessFocusJump.shouldBeFalse()

        val panel = HelixToolWindowSettingsPanel()
        panel.reset(settings)
        panel.isModified(settings).shouldBeFalse()

        settings.toolWindowSeamlessFocusJump = true
        panel.isModified(settings).shouldBeTrue()

        panel.apply(settings)
        settings.toolWindowSeamlessFocusJump.shouldBeFalse()
    }

    fun testToolWindowSearchConfirmOnEnterSettingsPanel() {
        val settings = HelixSettings.instance
        settings.toolWindowSearchConfirmOnEnter = false
        settings.toolWindowSearchConfirmOnEnter.shouldBeFalse()

        val panel = HelixToolWindowSettingsPanel()
        panel.reset(settings)
        panel.isModified(settings).shouldBeFalse()

        settings.toolWindowSearchConfirmOnEnter = true
        panel.isModified(settings).shouldBeTrue()

        panel.apply(settings)
        settings.toolWindowSearchConfirmOnEnter.shouldBeFalse()
    }

    fun testToolWindowSpaceSwitcherSettingsPanel() {
        val settings = HelixSettings.instance
        settings.toolWindowSpaceSwitcher = false
        settings.toolWindowSpaceSwitcher.shouldBeFalse()

        val panel = HelixToolWindowSettingsPanel()
        panel.reset(settings)
        panel.isModified(settings).shouldBeFalse()

        settings.toolWindowSpaceSwitcher = true
        panel.isModified(settings).shouldBeTrue()

        panel.apply(settings)
        settings.toolWindowSpaceSwitcher.shouldBeFalse()
    }

    fun testToolWindowCommitSpaceSwitcherSettingsPanel() {
        val settings = HelixSettings.instance
        settings.toolWindowCommitSpaceSwitcher = false
        settings.toolWindowCommitSpaceSwitcher.shouldBeFalse()

        val panel = HelixToolWindowSettingsPanel()
        panel.reset(settings)
        panel.isModified(settings).shouldBeFalse()

        settings.toolWindowCommitSpaceSwitcher = true
        panel.isModified(settings).shouldBeTrue()

        panel.apply(settings)
        settings.toolWindowCommitSpaceSwitcher.shouldBeFalse()
    }
}
