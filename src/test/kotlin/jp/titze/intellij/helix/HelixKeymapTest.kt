// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.config.HelixConfigLoader
import jp.titze.intellij.helix.config.HelixConfigResult
import jp.titze.intellij.helix.config.HelixConfigWatcher
import jp.titze.intellij.helix.config.HelixTomlParser
import jp.titze.intellij.helix.keymap.HelixCommandRegistry
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.keymap.HelixKeyStroke
import jp.titze.intellij.helix.keymap.HelixKeymapConflictDetector
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.keymap.HelixKeymapResult
import jp.titze.intellij.helix.keymap.HelixKeymapTrie
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixWhichKeyMenus
import java.awt.event.KeyEvent
import java.io.File

class HelixKeymapTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixRegisterManager.clear()
        HelixKeymapManager.resetToDefaults()
        HelixConfigWatcher.explicitWatchedFile = null
        HelixConfigWatcher.onReloadCallback = null
        HelixSettings.instance.customConfigPath = ""
    }

    override fun tearDown() {
        HelixRegisterManager.clear()
        HelixKeymapManager.resetToDefaults()
        HelixConfigWatcher.explicitWatchedFile = null
        HelixConfigWatcher.onReloadCallback = null
        HelixSettings.instance.customConfigPath = ""
        super.tearDown()
    }

    fun testKeyStrokeParsing() {
        val strokeA = HelixKeyStroke.fromString("a")
        strokeA.keyChar shouldBe 'a'
        strokeA.isCtrl.shouldBeFalse()

        val strokeSpace = HelixKeyStroke.fromString("space")
        strokeSpace.keyChar shouldBe ' '
        strokeSpace.keyCode shouldBe KeyEvent.VK_SPACE

        val strokeCw = HelixKeyStroke.fromString("C-w")
        strokeCw.keyChar shouldBe 'w'
        strokeCw.isCtrl.shouldBeTrue()
        strokeCw.toDisplayString() shouldBe "C-w"

        val strokeAx = HelixKeyStroke.fromString("A-x")
        strokeAx.keyChar shouldBe 'x'
        strokeAx.isAlt.shouldBeTrue()
        strokeAx.toDisplayString() shouldBe "A-x"

        val fromCharCtrlW = HelixKeyStroke.fromChar('\u0017')
        fromCharCtrlW.isCtrl.shouldBeTrue()
        fromCharCtrlW.keyChar shouldBe 'w'
        fromCharCtrlW.matches(strokeCw).shouldBeTrue()
    }

    fun testCommandRegistryExecution() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor

        HelixCommandRegistry.get("move_char_right").shouldNotBeNull()
        HelixCommandRegistry.execute("move_char_right", editor, count = 3).shouldBeTrue()
        editor.caretModel.offset shouldBe 3

        HelixCommandRegistry.execute("no_op", editor).shouldBeTrue()

        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }
        try {
            HelixCommandRegistry.execute(":action ReformatCode", editor, count = 2).shouldBeTrue()
            executedActions shouldBe listOf("ReformatCode", "ReformatCode")

            HelixCommandRegistry.executeList(listOf("no_op", ":action SaveAll"), editor).shouldBeTrue()
            executedActions.contains("SaveAll").shouldBeTrue()
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testTrieBindingAndResolution() {
        val trie = HelixKeymapTrie()
        val spaceStroke = HelixKeyStroke.fromString("space")
        val tStroke = HelixKeyStroke.fromString("t")

        trie.bind(listOf(spaceStroke, tStroke), ":action ActivateTerminalToolWindow", "Terminal")

        val branchResult = trie.resolve(listOf(spaceStroke))
        branchResult.shouldBeInstanceOf<HelixKeymapResult.Branch>()

        val leafResult = trie.resolve(listOf(spaceStroke, tStroke))
        leafResult.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        leafResult.command shouldBe ":action ActivateTerminalToolWindow"
        leafResult.label shouldBe "Terminal"

        trie.unbind(listOf(spaceStroke, tStroke))
        trie.resolve(listOf(spaceStroke, tStroke)).shouldBeInstanceOf<HelixKeymapResult.None>()
    }

    fun testCustomKeymapManagerOverride() {
        myFixture.configureByText("test.txt", "abc def")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }
        try {
            HelixKeymapManager.bind(
                HelixMode.NORMAL,
                "space t",
                ":action ActivateTerminalToolWindow",
                "Terminal",
            )

            // Step 1: type space
            HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
            state.pendingSequence shouldBe " "

            // Step 2: type t
            HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
            state.pendingSequence shouldBe ""
            executedActions shouldBe listOf("ActivateTerminalToolWindow")
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testCustomSingleKeyRemap() {
        myFixture.configureByText("test.txt", "foo bar")
        val editor = myFixture.editor

        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }
        try {
            // Remap 'x' to ReformatCode
            HelixKeymapManager.bind(HelixMode.NORMAL, "x", ":action ReformatCode")
            HelixKeyHandler.handleKey('x', editor).shouldBeTrue()
            executedActions shouldBe listOf("ReformatCode")
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testDynamicWhichKeyOverlay() {
        HelixKeymapManager.bind(
            HelixMode.NORMAL,
            "space t",
            ":action ActivateTerminalToolWindow",
            "Terminal",
        )

        val menu = HelixWhichKeyMenus.getMenu(" ", HelixMode.NORMAL)
        menu.shouldNotBeNull()
        val itemT = menu.second.firstOrNull { it.key == "t" }
        itemT.shouldNotBeNull()
        itemT.label shouldBe "Terminal"
        itemT.intelliJAction shouldBe "ActivateTerminalToolWindow"
    }

    fun testDynamicWhichKeyNewPrefix() {
        HelixKeymapManager.bind(
            HelixMode.NORMAL,
            ", f",
            "file_picker",
            "Custom Files",
        )

        val menu = HelixWhichKeyMenus.getMenu(",", HelixMode.NORMAL)
        menu.shouldNotBeNull()
        menu.first shouldBe ", MENU"
        val itemF = menu.second.firstOrNull { it.key == "f" }
        itemF.shouldNotBeNull()
        itemF.label shouldBe "Custom Files"
    }

    fun testTomlParserKeySections() {
        val toml = """
            # Sample Helix Config
            theme = "base16_default"

            [keys.normal]
            "C-s" = ":w"
            "X" = "extend_line_up"
            "C-d" = ["half_page_down", "align_view_center"]

            [keys.normal.space]
            t = ":action ActivateTerminalToolWindow"
            "g"."l" = ":action Vcs.Show.Log"

            [keys.insert]
            "C-h" = "delete_char_backward"
        """.trimIndent()

        val result = HelixTomlParser.parse(toml)
        result.errors.isEmpty().shouldBeTrue()

        val normalBindings = result.bindings.filter { it.mode == HelixMode.NORMAL }
        normalBindings.size shouldBe 5

        val csBinding = normalBindings.first { it.sequence == "C-s" }
        csBinding.action shouldBe ":w"

        val spaceTBinding = normalBindings.first { it.sequence == "space t" }
        spaceTBinding.action shouldBe ":action ActivateTerminalToolWindow"

        val glBinding = normalBindings.first { it.sequence == "space g l" }
        glBinding.action shouldBe ":action Vcs.Show.Log"

        val cdBinding = normalBindings.first { it.sequence == "C-d" }
        cdBinding.action shouldBe listOf("half_page_down", "align_view_center")

        val insertBindings = result.bindings.filter { it.mode == HelixMode.INSERT }
        insertBindings.size shouldBe 1
        insertBindings[0].sequence shouldBe "C-h"
    }

    fun testConfigLoaderExecution() {
        myFixture.configureByText("test.txt", "line1\nline2\nline3")
        val editor = myFixture.editor

        val tempFile = File.createTempFile("helix-test-config", ".toml")
        try {
            tempFile.writeText(
                """
                [keys.normal]
                "space"."t" = ":action ActivateTerminalToolWindow"
                "C-j" = ["move_line_down", "move_line_down"]
                """.trimIndent(),
            )

            val loadResult = HelixConfigLoader.loadConfig(tempFile)
            loadResult.errors.isEmpty().shouldBeTrue()
            loadResult.bindingCount shouldBe 2

            val executedActions = mutableListOf<String>()
            HelixActionDelegate.actionExecutor = { actionId, _ ->
                executedActions.add(actionId)
                true
            }
            try {
                // Test space t
                HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
                HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
                executedActions shouldBe listOf("ActivateTerminalToolWindow")

                // Test macro C-j
                val stroke = HelixKeyStroke.fromString("C-j")
                val trieResult = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(stroke))
                trieResult.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
                HelixCommandRegistry.execute(trieResult.command, editor)
                editor.caretModel.logicalPosition.line shouldBe 2
            } finally {
                HelixActionDelegate.actionExecutor = null
            }
        } finally {
            tempFile.delete()
        }
    }

    fun testConfigWatcherPathMatching() {
        val userHome = System.getProperty("user.home")
        HelixConfigWatcher.isMatchingConfigPath("$userHome/.config/helix/config.toml").shouldBeTrue()
        HelixConfigWatcher.isMatchingConfigPath("/some/path/to/other.toml").shouldBeFalse()
        HelixConfigWatcher.isMatchingConfigPath("$userHome/.config/helix/other.txt").shouldBeFalse()

        val tempCustom = File.createTempFile("custom-helix", "config.toml")
        try {
            HelixSettings.instance.customConfigPath = tempCustom.absolutePath
            HelixConfigWatcher.isMatchingConfigPath(tempCustom.absolutePath).shouldBeTrue()
        } finally {
            HelixSettings.instance.customConfigPath = ""
            tempCustom.delete()
        }
    }

    fun testConfigWatcherAutoReload() {
        val tempFile = File.createTempFile("helix-watch-test", "config.toml")
        try {
            tempFile.writeText(
                """
                [keys.normal]
                "x" = ":action Cut"
                """.trimIndent(),
            )
            HelixSettings.instance.customConfigPath = tempFile.absolutePath
            HelixConfigWatcher.explicitWatchedFile = tempFile

            var reloadedResult: HelixConfigResult? = null
            HelixConfigWatcher.onReloadCallback = { reloadedResult = it }

            val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(tempFile)
            vf.shouldNotBeNull()
            val mockEvent = com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent(
                this,
                vf,
                0L,
                1L,
            )

            tempFile.writeText(
                """
                [keys.normal]
                "x" = ":action Copy"
                """.trimIndent(),
            )

            HelixConfigWatcher.lastReloadTime = 0L
            val watcher = HelixConfigWatcher()
            watcher.after(listOf(mockEvent))

            reloadedResult.shouldNotBeNull()
            reloadedResult?.bindingCount shouldBe 1

            val strokeX = HelixKeyStroke.fromString("x")
            val trieResult = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(strokeX))
            trieResult.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
            trieResult.command shouldBe ":action Copy"
        } finally {
            HelixConfigWatcher.explicitWatchedFile = null
            HelixConfigWatcher.onReloadCallback = null
            HelixSettings.instance.customConfigPath = ""
            tempFile.delete()
        }
    }

    fun testShorthandSectionHeadersAndAliases() {
        val toml = """
            [keys.space]
            t = ":action ActivateTerminalToolWindow"

            [keys.goto]
            d = "goto_definition"

            [keys.view]
            c = "align_view_center"

            [keys.match]
            m = "match_brackets"

            [keys.window]
            v = "vsplit"

            [keys.normal.goto]
            x = ":action CustomGoto"

            [keys.normal.window]
            s = "hsplit"
        """.trimIndent()

        val result = HelixTomlParser.parse(toml)
        result.errors.isEmpty().shouldBeTrue()

        val bindings = result.bindings
        bindings.any { it.sequence == "space t" && it.action == ":action ActivateTerminalToolWindow" }.shouldBeTrue()
        bindings.any { it.sequence == "g d" && it.action == "goto_definition" }.shouldBeTrue()
        bindings.any { it.sequence == "z c" && it.action == "align_view_center" }.shouldBeTrue()
        bindings.any { it.sequence == "m m" && it.action == "match_brackets" }.shouldBeTrue()
        bindings.any { it.sequence == "C-w v" && it.action == "vsplit" }.shouldBeTrue()
        bindings.any { it.sequence == "g x" && it.action == ":action CustomGoto" }.shouldBeTrue()
        bindings.any { it.sequence == "C-w s" && it.action == "hsplit" }.shouldBeTrue()
    }

    fun testWordModifierKeyStrokes() {
        val ctrlS = HelixKeyStroke.fromString("ctrl-s")
        ctrlS.isCtrl.shouldBeTrue()
        ctrlS.keyChar shouldBe 's'

        val controlS = HelixKeyStroke.fromString("control-s")
        controlS.isCtrl.shouldBeTrue()
        controlS.keyChar shouldBe 's'

        val altX = HelixKeyStroke.fromString("alt-x")
        altX.isAlt.shouldBeTrue()
        altX.keyChar shouldBe 'x'

        val shiftTab = HelixKeyStroke.fromString("shift-tab")
        shiftTab.isShift.shouldBeTrue()
        shiftTab.keyCode shouldBe java.awt.event.KeyEvent.VK_TAB

        val metaF = HelixKeyStroke.fromString("meta-f")
        metaF.isMeta.shouldBeTrue()
        metaF.keyChar shouldBe 'f'

        val ins = HelixKeyStroke.fromString("ins")
        ins.keyCode shouldBe java.awt.event.KeyEvent.VK_INSERT

        val insert = HelixKeyStroke.fromString("insert")
        insert.keyCode shouldBe java.awt.event.KeyEvent.VK_INSERT
    }

    fun testExtendedCommandsRegistered() {
        HelixCommandRegistry.get("match_brackets").shouldNotBeNull()
        HelixCommandRegistry.get("surround_add").shouldNotBeNull()
        HelixCommandRegistry.get("surround_delete").shouldNotBeNull()
        HelixCommandRegistry.get("surround_replace").shouldNotBeNull()
        HelixCommandRegistry.get("select_textobject_around").shouldNotBeNull()
        HelixCommandRegistry.get("select_textobject_inner").shouldNotBeNull()
        HelixCommandRegistry.get("goto_prev_paragraph").shouldNotBeNull()
        HelixCommandRegistry.get("goto_next_paragraph").shouldNotBeNull()
        HelixCommandRegistry.get("goto_prev_comment").shouldNotBeNull()
        HelixCommandRegistry.get("goto_next_comment").shouldNotBeNull()
        HelixCommandRegistry.get("goto_prev_function").shouldNotBeNull()
        HelixCommandRegistry.get("goto_next_function").shouldNotBeNull()
        HelixCommandRegistry.get("goto_prev_diag").shouldNotBeNull()
        HelixCommandRegistry.get("goto_next_diag").shouldNotBeNull()
        HelixCommandRegistry.get("flip_selections").shouldNotBeNull()
        HelixCommandRegistry.get("ensure_selections_forward").shouldNotBeNull()
        HelixCommandRegistry.get("merge_selections").shouldNotBeNull()
        HelixCommandRegistry.get("merge_consecutive_selections").shouldNotBeNull()
        HelixCommandRegistry.get("copy_selection_on_prev_line").shouldNotBeNull()
        HelixCommandRegistry.get("delete_char_backward").shouldNotBeNull()
        HelixCommandRegistry.get("delete_char_forward").shouldNotBeNull()
        HelixCommandRegistry.get("insert_newline").shouldNotBeNull()
        HelixCommandRegistry.get("increment").shouldNotBeNull()
        HelixCommandRegistry.get("decrement").shouldNotBeNull()
    }

    fun testValidationDiagnosticsOnUnknownCommands() {
        val toml = """
            [keys.normal]
            "x" = "non_existent_command_xyz"
            "y" = "move_char_left"
            "q" = ["half_page_down", "bad_macro_command"]
        """.trimIndent()

        val validation = HelixConfigLoader.validateConfig(toml)
        validation.errors.size shouldBe 2
        validation.errors.any { it.contains("non_existent_command_xyz") }.shouldBeTrue()
        validation.errors.any { it.contains("bad_macro_command") }.shouldBeTrue()
        validation.bindingCount shouldBe 3
    }

    fun testTargetConfigFileDefaultsToIntellijToml() {
        val target = HelixConfigLoader.getTargetConfigFile()
        target.name shouldBe "intellij.toml"
        HelixConfigLoader.isCliConfigFile(target).shouldBeFalse()
        HelixConfigLoader.isCliConfigFile(File("/path/to/config.toml")).shouldBeTrue()
        HelixConfigLoader.isCliConfigFile(File("/path/to/my-config.toml")).shouldBeFalse()
    }

    fun testConfigWatcherMatchesBothTomlFiles() {
        val userHome = System.getProperty("user.home")
        if (!userHome.isNullOrEmpty()) {
            val intellijPath = "$userHome/.config/helix/intellij.toml"
            val configPath = "$userHome/.config/helix/config.toml"
            val randomPath = "$userHome/.config/helix/other.toml"

            HelixConfigWatcher.isMatchingConfigPath(intellijPath).shouldBeTrue()
            HelixConfigWatcher.isMatchingConfigPath(configPath).shouldBeTrue()
            HelixConfigWatcher.isMatchingConfigPath(randomPath).shouldBeFalse()
        }
    }

    fun testStarterConfigTemplatePointsToIntellijToml() {
        val template = HelixConfigLoader.getStarterConfigTemplate()
        template.contains("intellij.toml").shouldBeTrue()
        template.contains(":action ActivateTerminalToolWindow").shouldBeTrue()
    }

    fun testDefaultKeymapsHaveZeroShadowedConflicts() {
        val conflicts = HelixKeymapConflictDetector.detectAllActiveConflicts()
        conflicts.isEmpty().shouldBeTrue()
    }

    fun testConflictDetectorIdentifiesShadowedLeaf() {
        val trie = HelixKeymapTrie()
        trie.bind(listOf(HelixKeyStroke.fromChar('g'), HelixKeyStroke.fromChar('d')), "goto_definition")
        trie.bind(listOf(HelixKeyStroke.fromChar('g')), "move_char_left")

        val conflicts = HelixKeymapConflictDetector.detectConflicts(HelixMode.NORMAL, trie)
        conflicts.size shouldBe 1
        val conflict = conflicts.first()
        conflict.rawSequence shouldBe "g"
        conflict.action shouldBe "move_char_left"
        conflict.childChords.contains("g + d").shouldBeTrue()
        conflict.description.contains("shadowed").shouldBeTrue()
    }

    fun testValidateConfigReportsShadowedConflictDiagnostics() {
        val toml = """
            [keys.normal]
            g = "move_char_left"
        """.trimIndent()

        val result = HelixConfigLoader.validateConfig(toml)
        result.errors.isNotEmpty().shouldBeTrue()
        result.errors.any { it.contains("Conflict:") && it.contains("shadowed") }.shouldBeTrue()
    }
}
