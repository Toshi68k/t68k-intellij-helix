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
import jp.titze.intellij.helix.config.HelixConfigModifier
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

    fun testShiftKeyStrokeMatching() {
        val strokeQ = HelixKeyStroke.fromString("Q")
        val strokeShiftQ = HelixKeyStroke.fromString("S-q")
        val strokeLowerQ = HelixKeyStroke.fromString("q")

        strokeQ.matches(strokeShiftQ).shouldBeTrue()
        strokeShiftQ.matches(strokeQ).shouldBeTrue()
        strokeLowerQ.matches(strokeQ).shouldBeFalse()
        strokeQ.matches(strokeLowerQ).shouldBeFalse()

        val label = javax.swing.JLabel()
        val keyEventShiftQ = KeyEvent(
            label,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.SHIFT_DOWN_MASK,
            KeyEvent.VK_Q,
            'Q',
        )
        val strokeFromEvent = HelixKeyStroke.fromKeyEvent(keyEventShiftQ)
        strokeFromEvent.matches(strokeQ).shouldBeTrue()
        strokeFromEvent.matches(strokeShiftQ).shouldBeTrue()
        strokeFromEvent.matches(strokeLowerQ).shouldBeFalse()
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
                "space x",
                ":action ActivateTerminalToolWindow",
                "Terminal",
            )

            // Step 1: type space
            HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
            state.pendingSequence shouldBe " "

            // Step 2: type x
            HelixKeyHandler.handleKey('x', editor).shouldBeTrue()
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
            "space x",
            ":action ActivateTerminalToolWindow",
            "Terminal",
        )

        val menu = HelixWhichKeyMenus.getMenu(" ", HelixMode.NORMAL)
        menu.shouldNotBeNull()
        val itemX = menu.second.firstOrNull { it.key == "x" }
        itemX.shouldNotBeNull()
        itemX.label shouldBe "Terminal"
        itemX.intelliJAction shouldBe "ActivateTerminalToolWindow"
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
            "X" = "extend_line"
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
                "space"."x" = ":action ActivateTerminalToolWindow"
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
                // Test space x
                HelixKeyHandler.handleKey(' ', editor).shouldBeTrue()
                HelixKeyHandler.handleKey('x', editor).shouldBeTrue()
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
            val events = com.intellij.testFramework.VfsTestUtil.getEvents {
                com.intellij.openapi.application.runWriteAction {
                    vf.setBinaryContent(
                        """
                        [keys.normal]
                        "x" = ":action Copy"
                        """.trimIndent().toByteArray(),
                    )
                }
            }

            HelixConfigWatcher.lastReloadTime = 0L
            val watcher = HelixConfigWatcher()
            watcher.after(events)

            reloadedResult.shouldNotBeNull()
            reloadedResult.bindingCount shouldBe 1

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
        shiftTab.keyCode shouldBe KeyEvent.VK_TAB

        val metaF = HelixKeyStroke.fromString("meta-f")
        metaF.isMeta.shouldBeTrue()
        metaF.keyChar shouldBe 'f'

        val ins = HelixKeyStroke.fromString("ins")
        ins.keyCode shouldBe KeyEvent.VK_INSERT

        val insert = HelixKeyStroke.fromString("insert")
        insert.keyCode shouldBe KeyEvent.VK_INSERT
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
        HelixCommandRegistry.get("move_parent_node_start").shouldNotBeNull()
        HelixCommandRegistry.get("move_parent_node_end").shouldNotBeNull()
        HelixCommandRegistry.get("select_all_siblings").shouldNotBeNull()
        HelixCommandRegistry.get("select_all_children").shouldNotBeNull()
        HelixCommandRegistry.get("generate").shouldNotBeNull()
        HelixCommandRegistry.get("safe_delete").shouldNotBeNull()
        HelixCommandRegistry.get("pull_up").shouldNotBeNull()
        HelixCommandRegistry.get("push_down").shouldNotBeNull()
        HelixCommandRegistry.get("cleanup").shouldNotBeNull()
    }

    fun testRefactorChordBindings() {
        val space = HelixKeyStroke.fromString("space")
        val m = HelixKeyStroke.fromString("m")

        val resG = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(space, m, HelixKeyStroke.fromString("g")))
        resG.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        resG.command shouldBe "generate"

        val resD = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(space, m, HelixKeyStroke.fromString("d")))
        resD.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        resD.command shouldBe "safe_delete"

        val resU = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(space, m, HelixKeyStroke.fromString("u")))
        resU.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        resU.command shouldBe "pull_up"

        val resPushDown = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(space, m, HelixKeyStroke.fromString("D")))
        resPushDown.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        resPushDown.command shouldBe "push_down"
    }

    fun testAstMotionDefaultKeybindings() {
        val strokeAb = HelixKeyStroke.fromString("A-b")
        val resAb = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(strokeAb))
        resAb.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        resAb.command shouldBe "move_parent_node_start"

        val strokeAe = HelixKeyStroke.fromString("A-e")
        val resAe = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(strokeAe))
        resAe.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        resAe.command shouldBe "move_parent_node_end"

        val strokeAa = HelixKeyStroke.fromString("A-a")
        val resAa = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(strokeAa))
        resAa.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        resAa.command shouldBe "select_all_siblings"

        val strokeAi = HelixKeyStroke.fromString("A-I")
        val resAi = HelixKeymapManager.resolve(HelixMode.NORMAL, listOf(strokeAi))
        resAi.shouldBeInstanceOf<HelixKeymapResult.Leaf>()
        resAi.command shouldBe "select_all_children"
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

    fun testHelixConfigModifierSetNewBinding() {
        val initialToml = """
            [keys.normal]
            "C-s" = ":w"
        """.trimIndent()

        val updated = HelixConfigModifier.updateBindingInContent(
            initialToml,
            HelixMode.NORMAL,
            "space f",
            ":action GotoFile",
        )
        updated.contains("\"space\".\"f\" = \":action GotoFile\"").shouldBeTrue()
        val parsed = HelixTomlParser.parse(updated)
        parsed.bindings.size shouldBe 2
        parsed.bindings.any { it.sequence == "space f" && it.action == ":action GotoFile" }.shouldBeTrue()
    }

    fun testHelixConfigModifierUpdateExistingBinding() {
        val initialToml = """
            [keys.normal]
            "C-s" = ":w"
            "space"."f" = "file_picker"
        """.trimIndent()

        val updated = HelixConfigModifier.updateBindingInContent(
            initialToml,
            HelixMode.NORMAL,
            "space f",
            ":action GotoFile",
        )
        updated.contains("\"space\".\"f\" = \":action GotoFile\"").shouldBeTrue()
        val parsed = HelixTomlParser.parse(updated)
        parsed.bindings.size shouldBe 2
        parsed.bindings.first { it.sequence == "space f" }.action shouldBe ":action GotoFile"
    }

    fun testHelixConfigModifierRemoveBinding() {
        val initialToml = """
            [keys.normal]
            "C-s" = ":w"
            "space"."f" = "file_picker"
        """.trimIndent()

        val updated = HelixConfigModifier.removeBindingFromContent(
            initialToml,
            HelixMode.NORMAL,
            "space f",
        )
        val parsed = HelixTomlParser.parse(updated)
        parsed.bindings.size shouldBe 1
        parsed.bindings.none { it.sequence == "space f" }.shouldBeTrue()
    }

    fun testHelixConfigModifierSetNoOpAndMacroList() {
        val initialToml = """
            [keys.insert]
            "C-h" = "delete_char_backward"
        """.trimIndent()

        val withNoOp = HelixConfigModifier.updateBindingInContent(
            initialToml,
            HelixMode.INSERT,
            "C-h",
            "no_op",
        )
        val parsedNoOp = HelixTomlParser.parse(withNoOp)
        parsedNoOp.bindings.first { it.sequence == "C-h" }.action shouldBe "no_op"

        val withMacro = HelixConfigModifier.updateBindingInContent(
            initialToml,
            HelixMode.NORMAL,
            "C-d",
            listOf("half_page_down", "align_view_center"),
        )
        val parsedMacro = HelixTomlParser.parse(withMacro)
        parsedMacro.bindings.any { it.sequence == "C-d" }.shouldBeTrue()
    }

    fun testHelixConfigModifierHandlesDotAndHashKeys() {
        val initialToml = """
            [keys.normal]
            "C-s" = ":w"
            "#" = "toggle_comments"
            "." = "repeat_last_motion"
        """.trimIndent()

        val parsedInitial = HelixTomlParser.parse(initialToml)
        parsedInitial.bindings.size shouldBe 3
        parsedInitial.bindings.any { it.sequence == "." }.shouldBeTrue()
        parsedInitial.bindings.any { it.sequence == "#" }.shouldBeTrue()

        // Update dot key
        val updatedDot = HelixConfigModifier.updateBindingInContent(
            initialToml,
            HelixMode.NORMAL,
            ".",
            "align_view_center",
        )
        val parsedDot = HelixTomlParser.parse(updatedDot)
        parsedDot.bindings.first { it.sequence == "." }.action shouldBe "align_view_center"

        // Remove hash key
        val removedHash = HelixConfigModifier.removeBindingFromContent(
            updatedDot,
            HelixMode.NORMAL,
            "#",
        )
        val parsedRemoved = HelixTomlParser.parse(removedHash)
        parsedRemoved.bindings.none { it.sequence == "#" }.shouldBeTrue()
        parsedRemoved.bindings.any { it.sequence == "." }.shouldBeTrue()
    }

    fun testHelixConfigModifierHandlesRawMacroArrayString() {
        val initialToml = """
            [keys.normal]
            "C-s" = ":w"
        """.trimIndent()

        val rawMacroString = "[\"half_page_down\", \"align_view_center\"]"
        val updated = HelixConfigModifier.updateBindingInContent(
            initialToml,
            HelixMode.NORMAL,
            "C-d",
            rawMacroString,
        )
        val parsed = HelixTomlParser.parse(updated)
        val binding = parsed.bindings.firstOrNull { it.sequence == "C-d" }
        binding.shouldNotBeNull()
        binding.action.shouldBeInstanceOf<List<String>>()
        (binding.action as List<*>).size shouldBe 2
    }

    fun testDeepSubKeymapWithMacroBindings() {
        val toml = """
            [keys.normal.m.n]
            "\"" = "@f\"lmi\""
            "'" = "@f'lmi'"
            "(" = "@f(lmi("
            "[" = "@f[lmi["
            "{" = "@f{lmi{"
            ")" = "@f)hmi)"
            "]" = "@f]hmi]"
            "}" = "@f}hmi}"

            [keys.normal.m.p]
            "\"" = "@F\"hmi\""
            "'" = "@F'hmi'"
            "(" = "@F(lmi("
            "[" = "@F[lmi["
            "{" = "@F{lmi{"
            ")" = "@F)hmi)"
            "]" = "@F]hmi]"
            "}" = "@F}hmi}"
        """.trimIndent()

        val validated = HelixConfigLoader.validateConfig(toml)
        validated.errors.isEmpty().shouldBeTrue()

        val parsed = HelixTomlParser.parse(toml)
        parsed.bindings.size shouldBe 16

        for ((mode, sequence, action) in parsed.bindings) {
            HelixKeymapManager.bind(mode, sequence, action as String)
        }

        // Test next quote: mn"
        myFixture.configureByText("test.txt", "val data = \"sample text\"")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(0)

        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('n', editor)
        HelixKeyHandler.handleKey('"', editor)

        editor.selectionModel.selectedText shouldBe "sample text"

        // Test previous quote: mp"
        editor.caretModel.moveToOffset(editor.document.textLength)
        HelixKeyHandler.handleKey('m', editor)
        HelixKeyHandler.handleKey('p', editor)
        HelixKeyHandler.handleKey('"', editor)

        editor.selectionModel.selectedText shouldBe "sample text"

        // Test next bracket: mn(
        myFixture.configureByText("test2.txt", "val num = (10 + 20)")
        val editor2 = myFixture.editor
        editor2.caretModel.moveToOffset(0)

        HelixKeyHandler.handleKey('m', editor2)
        HelixKeyHandler.handleKey('n', editor2)
        HelixKeyHandler.handleKey('(', editor2)

        editor2.selectionModel.selectedText shouldBe "10 + 20"

        // Test next quote with line break: mn"
        myFixture.configureByText("test3.txt", "\"aaa\" = \"bbb\"\n\"ccc\" = \"ddd\"")
        val editor3 = myFixture.editor
        editor3.caretModel.moveToOffset(10) // inside "bbb"

        HelixKeyHandler.handleKey('m', editor3)
        HelixKeyHandler.handleKey('n', editor3)
        HelixKeyHandler.handleKey('"', editor3)

        editor3.selectionModel.selectedText shouldBe "ccc"

        editor3.caretModel.moveToOffset(2) // inside "aaa"
        HelixKeyHandler.handleKey('m', editor3)
        HelixKeyHandler.handleKey('n', editor3)
        HelixKeyHandler.handleKey('"', editor3)

        editor3.selectionModel.selectedText shouldBe " = "
    }
}
