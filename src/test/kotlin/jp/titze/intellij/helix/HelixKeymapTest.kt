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
}
