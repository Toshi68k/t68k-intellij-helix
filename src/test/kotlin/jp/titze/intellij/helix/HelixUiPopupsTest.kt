// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.action.HelixActions
import jp.titze.intellij.helix.action.HelixCaretUtils
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.command.HelixCommands
import jp.titze.intellij.helix.editor.HelixEventDispatcher
import jp.titze.intellij.helix.editor.HelixInsertTracker
import jp.titze.intellij.helix.keymap.HelixDebugKeymap
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.keymap.HelixKeymapManager
import jp.titze.intellij.helix.motion.HelixMotionHistory
import jp.titze.intellij.helix.register.HelixRegisterManager
import jp.titze.intellij.helix.settings.HelixLineNavigationMode
import jp.titze.intellij.helix.settings.HelixScratchSplitMode
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixMode
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixDirectoryFilePickerPopup
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixPromptType
import jp.titze.intellij.helix.ui.HelixSearchPopup
import jp.titze.intellij.helix.ui.HelixSelectRegexPopup
import jp.titze.intellij.helix.ui.HelixStatusBarWidget
import jp.titze.intellij.helix.ui.HelixStatusBarWidgetFactory
import jp.titze.intellij.helix.ui.HelixWhichKeyMenus
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

class HelixUiPopupsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        HelixRegisterManager.clear()
        HelixMotionHistory.lastMotion = null
        HelixInsertTracker.reset()
        HelixKeymapManager.resetToDefaults()
    }

    override fun tearDown() {
        HelixRegisterManager.clear()
        HelixMotionHistory.lastMotion = null
        HelixInsertTracker.reset()
        HelixKeymapManager.resetToDefaults()
        super.tearDown()
    }

    fun testSpaceMenuChords() {
        myFixture.configureByText("test.txt", "sample text for space testing")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        // Press ' ' begins chord
        HelixKeyHandler.handleKey(' ', editor)
        state.pendingSequence shouldBe " "

        // Second key clears chord and executes handler
        HelixKeyHandler.handleKey('y', editor)
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testWhichKeyChordsSequenceReset() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        HelixKeyHandler.handleKey('g', editor)
        state.pendingSequence shouldBe "g"

        HelixKeyHandler.handleKey('h', editor) // move line start
        state.pendingSequence.isEmpty().shouldBeTrue()
    }

    fun testHelixCommandPopupMatchingAndExecution() {
        myFixture.configureByText("test.txt", "line 1\nline 2")
        val editor = myFixture.editor

        // Verify command item matching
        val writeCmd = HelixCommandPopup.COMMANDS.first { it.name == "write" }
        writeCmd.matches("w").shouldBeTrue()
        writeCmd.matches("write").shouldBeTrue()
        writeCmd.matches("unknown_xyz").shouldBeFalse()

        // Verify colon triggering HelixCommandPopup.show in tests without throwing
        HelixKeyHandler.handleKey(':', editor)
        // Execute command directly
        HelixCommandPopup.executeCommand("format", editor)
    }

    fun testHelixCommandFilterRelevanceRanking() {
        // "reg" should rank registers (alias "reg") above fold (alias "collapse-region")
        val regMatches = HelixCommands.filterCommands("reg")
        regMatches.isNotEmpty().shouldBeTrue()
        regMatches.first().name shouldBe "registers"
        regMatches.first().aliases.contains("reg").shouldBeTrue()

        // ":reg" with colon prefix should also rank registers first
        val colonRegMatches = HelixCommands.filterCommands(":reg")
        colonRegMatches.first().name shouldBe "registers"

        // "w" should rank write (alias "w") first
        val wMatches = HelixCommands.filterCommands("w")
        wMatches.first().name shouldBe "write"

        // "q" should rank quit (alias "q") first
        val qMatches = HelixCommands.filterCommands("q")
        qMatches.first().name shouldBe "quit"

        // Exact name "jumps" should rank jumps first
        val jumpsMatches = HelixCommands.filterCommands("jumps")
        jumpsMatches.first().name shouldBe "jumps"
    }

    fun testHelixSettingsSearchUiMode() {
        val settings = HelixSettings.instance
        val original = settings.searchUiMode

        try {
            settings.searchUiMode = HelixSearchUiMode.STOCK_HELIX
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX

            settings.searchUiMode = HelixSearchUiMode.POPUP
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP
        } finally {
            settings.searchUiMode = original
        }
    }

    fun testHelixCommandToggleSearchUi() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor
        val settings = HelixSettings.instance
        val original = settings.searchUiMode

        try {
            settings.searchUiMode = HelixSearchUiMode.STOCK_HELIX
            HelixCommandPopup.executeCommand("toggle-search-ui", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP

            HelixCommandPopup.executeCommand("toggle-search-ui", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX

            HelixCommandPopup.executeCommand("set search-ui=popup", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.POPUP

            HelixCommandPopup.executeCommand("set search-ui=inline", editor)
            settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX
        } finally {
            settings.searchUiMode = original
        }
    }

    fun testHelixCommandLineNavigationMode() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor
        val settings = HelixSettings.instance
        val original = settings.lineNavigationMode

        try {
            settings.lineNavigationMode = HelixLineNavigationMode.HELIX_STANDARD
            HelixCommandPopup.executeCommand("toggle-line-nav", editor)
            settings.lineNavigationMode shouldBe HelixLineNavigationMode.VIM_STANDARD

            HelixCommandPopup.executeCommand("toggle-line-nav", editor)
            settings.lineNavigationMode shouldBe HelixLineNavigationMode.HELIX_STANDARD

            HelixCommandPopup.executeCommand("set line-nav=vim", editor)
            settings.lineNavigationMode shouldBe HelixLineNavigationMode.VIM_STANDARD

            HelixCommandPopup.executeCommand("set line-nav=helix", editor)
            settings.lineNavigationMode shouldBe HelixLineNavigationMode.HELIX_STANDARD

            HelixCommandPopup.executeCommand("set-line-nav-vim", editor)
            settings.lineNavigationMode shouldBe HelixLineNavigationMode.VIM_STANDARD

            HelixCommandPopup.executeCommand("set-line-nav-helix", editor)
            settings.lineNavigationMode shouldBe HelixLineNavigationMode.HELIX_STANDARD
        } finally {
            settings.lineNavigationMode = original
        }
    }

    fun testWhichKeyGotoMenuReflectsLineNavigationMode() {
        val settings = HelixSettings.instance
        val original = settings.lineNavigationMode

        try {
            settings.lineNavigationMode = HelixLineNavigationMode.HELIX_STANDARD
            val (_, helixItems) = HelixWhichKeyMenus.getMenu("g") ?: error("g menu not found")
            val helixJ = helixItems.first { it.key == "j" }
            val helixK = helixItems.first { it.key == "k" }
            helixJ.label shouldBe "Move down line"
            helixJ.helixCommand shouldBe "move_line_down"
            helixK.label shouldBe "Move up line"
            helixK.helixCommand shouldBe "move_line_up"

            settings.lineNavigationMode = HelixLineNavigationMode.VIM_STANDARD
            val (_, vimItems) = HelixWhichKeyMenus.getMenu("g") ?: error("g menu not found")
            val vimJ = vimItems.first { it.key == "j" }
            val vimK = vimItems.first { it.key == "k" }
            vimJ.label shouldBe "Move down visual line"
            vimJ.helixCommand shouldBe "move_visual_line_down"
            vimK.label shouldBe "Move up visual line"
            vimK.helixCommand shouldBe "move_visual_line_up"
        } finally {
            settings.lineNavigationMode = original
        }
    }

    fun testPromptBarComponentCreation() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor

        val bar = HelixPromptBar.getOrCreate(editor)
        bar.shouldNotBeNull()
        HelixPromptBar.getOrCreate(editor) shouldBeSameInstanceAs bar

        bar.show(HelixPromptType.SEARCH)
        bar.isVisible.shouldBeTrue()

        bar.cancelAndClose()
        bar.isVisible.shouldBeFalse()
    }

    fun testHelixWhichKeyPopupPositionCalculation() {
        // Normal dimensions: 1000x800 viewport, 300x200 popup, margin 20
        val pos = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 1000,
            viewHeight = 800,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 0,
            visibleY = 0,
            marginX = 20,
            marginY = 20,
        )
        pos.x shouldBe 680 // 1000 - 300 - 20
        pos.y shouldBe 580 // 800 - 200 - 20

        // With viewport offset: visibleX = 50, visibleY = 100
        val posWithOffset = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 1000,
            viewHeight = 800,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 50,
            visibleY = 100,
            marginX = 20,
            marginY = 20,
        )
        posWithOffset.x shouldBe 730 // 50 + 680
        posWithOffset.y shouldBe 680 // 100 + 580

        // Very small viewport (smaller than popup) clamps to 0 offset
        val posClamped = HelixWhichKeyPopup.calculatePopupPosition(
            viewWidth = 200,
            viewHeight = 150,
            prefWidth = 300,
            prefHeight = 200,
            visibleX = 0,
            visibleY = 0,
            marginX = 20,
            marginY = 20,
        )
        posClamped.x shouldBe 0
        posClamped.y shouldBe 0
    }

    fun testHelixWhichKeyPopupTriggerAndHide() {
        myFixture.configureByText("test.txt", "line 1\nline 2\n")
        val editor = myFixture.editor

        // Typing space or g should trigger which-key show safely without throwing
        HelixWhichKeyPopup.show(editor, " ")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "g")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "m")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "[")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "]")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "z")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "Z")
        HelixWhichKeyPopup.hide()

        HelixWhichKeyPopup.show(editor, "C-w")
        HelixWhichKeyPopup.hide()
        HelixWhichKeyPopup.isShowing() shouldBe false
    }

    fun testTerminalAndShCommandExecution() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor
        var executedAction: String? = null
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor

        try {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
                executedAction = actionId
                true
            }

            HelixCommandPopup.executeCommand("terminal", editor)
            executedAction shouldBe "ActivateTerminalToolWindow"

            executedAction = null
            HelixCommandPopup.executeCommand("sh", editor)
            executedAction shouldBe "ActivateTerminalToolWindow"

            val terminalCmd = HelixCommandPopup.COMMANDS.first { it.name == "terminal" }
            terminalCmd.aliases.contains("sh").shouldBeTrue()
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }
    }

    fun testDirectoryCommandsCdAndPwd() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor

        jp.titze.intellij.helix.command.HelixDirectoryManager.reset()
        val initialDir = jp.titze.intellij.helix.command.HelixDirectoryManager.getCurrentDirectory(editor)

        val pwdMsg = jp.titze.intellij.helix.command.HelixDirectoryManager.printWorkingDirectory(editor)
        pwdMsg shouldBe "Working directory: $initialDir"

        val tempDir = java.io.File(System.getProperty("java.io.tmpdir")).canonicalPath
        val cdMsg = jp.titze.intellij.helix.command.HelixDirectoryManager.changeDirectory(tempDir, editor)
        cdMsg shouldBe "Working directory changed to: $tempDir"
        jp.titze.intellij.helix.command.HelixDirectoryManager.getCurrentDirectory(editor) shouldBe tempDir

        // Test cd - returns to previous directory
        val cdBackMsg = jp.titze.intellij.helix.command.HelixDirectoryManager.changeDirectory("-", editor)
        cdBackMsg shouldBe "Working directory changed to: $initialDir"
        jp.titze.intellij.helix.command.HelixDirectoryManager.getCurrentDirectory(editor) shouldBe initialDir

        // Test non-existent directory
        val invalidMsg = jp.titze.intellij.helix.command.HelixDirectoryManager
            .changeDirectory("/non_existent_path_xyz_123", editor)
        invalidMsg shouldBe "Directory not found: /non_existent_path_xyz_123"

        jp.titze.intellij.helix.command.HelixDirectoryManager.reset()
    }

    fun testSpaceMenuNewParityChords() {
        myFixture.configureByText("test.txt", "test content")
        val editor = myFixture.editor

        val executedActions = mutableListOf<String>()
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            // Space + 'c' (toggle comment)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('c', editor).shouldBeTrue()
            executedActions.contains("CommentByLineComment").shouldBeTrue()

            // Space + 'C' (toggle block comment)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('C', editor).shouldBeTrue()
            executedActions.contains("CommentByBlockComment").shouldBeTrue()

            // Space + 'h' (usages)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('h', editor).shouldBeTrue()

            // Space + 'k' (hover documentation)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('k', editor).shouldBeTrue()
            executedActions.contains("QuickJavaDoc").shouldBeTrue()

            // Space + 'e' (project tree)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('e', editor).shouldBeTrue()
            executedActions.contains("ActivateProjectToolWindow").shouldBeTrue()

            // Space + '.' (select in project)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('.', editor).shouldBeTrue()
            executedActions.contains("SelectInProjectView").shouldBeTrue()

            // Space + 'g' (git changes)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('g', editor).shouldBeTrue()

            // Space + 'f' (file picker)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('f', editor).shouldBeTrue()
            executedActions.contains("GotoFile").shouldBeTrue()
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.lastPickerChar shouldBe 'f'

            // Space + 'F' (file picker in current directory)
            HelixDirectoryFilePickerPopup.isShowing = false
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('F', editor).shouldBeTrue()
            HelixDirectoryFilePickerPopup.isShowing.shouldBeTrue()
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.lastPickerChar shouldBe 'F'

            // Space + ''' (last picker - repeats 'F')
            HelixDirectoryFilePickerPopup.isShowing = false
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('\'', editor).shouldBeTrue()
            HelixDirectoryFilePickerPopup.isShowing.shouldBeTrue()

            // Hover command palette execution
            executedActions.clear()
            HelixCommands.execute("hover", editor)
            executedActions.contains("QuickJavaDoc").shouldBeTrue()
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = null
        }
    }

    fun testWhichKeyMultiColumnLayoutForLargeMenus() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor

        val (spaceTitle, spaceItems) = HelixWhichKeyMenus.getMenu(" ") ?: return
        val panel = HelixWhichKeyPopup.createWhichKeyPanel(spaceTitle, spaceItems, editor, maxHeight = 400)
        panel.shouldNotBeNull()
        (panel.preferredSize.width >= 500).shouldBeTrue()
    }

    fun testWhichKeySingleColumnLayoutForSmallMenus() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor

        val (matchTitle, matchItems) = HelixWhichKeyMenus.getMenu("m") ?: return
        val panel = HelixWhichKeyPopup.createWhichKeyPanel(matchTitle, matchItems, editor, maxHeight = 600)
        panel.shouldNotBeNull()
        (panel.preferredSize.width < 500).shouldBeTrue()
    }

    fun testWhichKeyKeyboardScrolling() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor

        val (spaceTitle, spaceItems) = HelixWhichKeyMenus.getMenu(" ") ?: return
        val panel = HelixWhichKeyPopup.createWhichKeyPanel(spaceTitle, spaceItems, editor, maxHeight = 200)

        val scrollPane = panel.components.filterIsInstance<com.intellij.ui.components.JBScrollPane>().first()
        val scrollBar = scrollPane.verticalScrollBar

        val keyListener = panel.keyListeners.first()

        // Down arrow
        val downEvent = java.awt.event.KeyEvent(
            panel,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            java.awt.event.KeyEvent.VK_DOWN,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )
        keyListener.keyPressed(downEvent)
        downEvent.isConsumed.shouldBeTrue()

        // Page down
        val pageDownEvent = java.awt.event.KeyEvent(
            panel,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            java.awt.event.KeyEvent.VK_PAGE_DOWN,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )
        keyListener.keyPressed(pageDownEvent)
        pageDownEvent.isConsumed.shouldBeTrue()

        // Home
        val homeEvent = java.awt.event.KeyEvent(
            panel,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            java.awt.event.KeyEvent.VK_HOME,
            java.awt.event.KeyEvent.CHAR_UNDEFINED,
        )
        keyListener.keyPressed(homeEvent)
        homeEvent.isConsumed.shouldBeTrue()
        scrollBar.value shouldBe 0
    }

    fun testWhichKeyItemDataModelAndSnakeCaseCommands() {
        val (spaceTitle, spaceItems) = HelixWhichKeyMenus.getMenu(" ") ?: error("Missing space menu")
        spaceTitle shouldBe "SPACE MENU"
        val filePicker = spaceItems.first { it.key == "f" }
        filePicker.label shouldBe "File picker"
        filePicker.helixCommand shouldBe "file_picker"
        filePicker.intelliJAction shouldBe "GotoFile"
        filePicker.description shouldBe "file_picker"

        val filePickerCurDir = spaceItems.first { it.key == "F" }
        filePickerCurDir.label shouldBe "Current dir file picker"
        filePickerCurDir.helixCommand shouldBe "file_picker_in_current_directory"
        filePickerCurDir.intelliJAction shouldBe ""
        filePickerCurDir.description shouldBe "file_picker_in_current_directory"

        val yankToClipboard = spaceItems.first { it.key == "y" }
        yankToClipboard.helixCommand shouldBe "yank_to_clipboard"

        val yankMain = spaceItems.first { it.key == "Y" }
        yankMain.helixCommand shouldBe "yank_main_selection_to_clipboard"

        // Ensure all commands follow Helix snake_case convention
        val regex = Regex("^[a-z0-9_]+( <[a-z0-9_]+>)*$")
        for (item in spaceItems) {
            if (item.helixCommand.isNotEmpty()) {
                val matches = regex.matches(item.helixCommand)
                matches.shouldBeTrue()
            }
        }
    }

    fun testGotoWhichKeyMenuContainsDeclarationAndDefinition() {
        val (title, items) = HelixWhichKeyMenus.getMenu("g") ?: error("Missing goto menu")
        title shouldBe "GOTO MENU"
        val defItem = items.first { it.key == "d" }
        defItem.label shouldBe "Goto definition"
        defItem.helixCommand shouldBe "goto_definition"
        defItem.intelliJAction shouldBe "GotoDeclaration"

        val declItem = items.first { it.key == "D" }
        declItem.label shouldBe "Goto declaration"
        declItem.helixCommand shouldBe "goto_declaration"
        declItem.intelliJAction shouldBe "GotoDeclarationOnly"
    }

    fun testHelixSettingsWhichKeyOptions() {
        val settings = HelixSettings.instance
        val originalEnabled = settings.enableWhichKeyPopups
        val originalMode = settings.whichKeyHintMode

        try {
            settings.enableWhichKeyPopups = false
            settings.enableWhichKeyPopups.shouldBeFalse()
            settings.enableWhichKeyPopups = true
            settings.enableWhichKeyPopups.shouldBeTrue()

            settings.whichKeyHintMode = jp.titze.intellij.helix.settings.WhichKeyHintMode.INTELLIJ_ACTION
            settings.whichKeyHintMode shouldBe jp.titze.intellij.helix.settings.WhichKeyHintMode.INTELLIJ_ACTION

            settings.whichKeyHintMode = jp.titze.intellij.helix.settings.WhichKeyHintMode.HELIX_COMMAND
            settings.whichKeyHintMode shouldBe jp.titze.intellij.helix.settings.WhichKeyHintMode.HELIX_COMMAND
        } finally {
            settings.enableWhichKeyPopups = originalEnabled
            settings.whichKeyHintMode = originalMode
        }
    }

    fun testWhichKeyPanelTabKeyTogglesHintMode() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor

        val (spaceTitle, spaceItems) = HelixWhichKeyMenus.getMenu(" ") ?: return
        val panel = HelixWhichKeyPopup.createWhichKeyPanel(spaceTitle, spaceItems, editor)

        val keyListener = panel.keyListeners.first()
        val tabEvent = java.awt.event.KeyEvent(
            panel,
            java.awt.event.KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            java.awt.event.KeyEvent.VK_TAB,
            '\t',
        )
        keyListener.keyPressed(tabEvent)
        tabEvent.isConsumed.shouldBeTrue()
    }

    fun testHelixWhichKeyDisabledSetting() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor
        val settings = HelixSettings.instance
        val original = settings.enableWhichKeyPopups

        try {
            settings.enableWhichKeyPopups = false
            HelixWhichKeyPopup.show(editor, " ")
            HelixWhichKeyPopup.isShowing().shouldBeFalse()
        } finally {
            settings.enableWhichKeyPopups = original
        }
    }

    fun testHelixWhichKeyPopupToggleHintModeMethod() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor

        val (spaceTitle, spaceItems) = HelixWhichKeyMenus.getMenu(" ") ?: return
        val panel = HelixWhichKeyPopup.createWhichKeyPanel(spaceTitle, spaceItems, editor)
        panel.shouldNotBeNull()

        // Calling toggleHintMode invokes the registered action
        HelixWhichKeyPopup.toggleHintMode().shouldBeTrue()
        HelixWhichKeyPopup.toggleHintMode().shouldBeTrue()
    }

    fun testWhichKeyColumnLayoutSettingAndPanelDimensions() {
        myFixture.configureByText("test.txt", "test")
        val editor = myFixture.editor
        val settings = HelixSettings.instance
        val original = settings.whichKeyColumnLayout

        try {
            settings.whichKeyColumnLayout = jp.titze.intellij.helix.settings.WhichKeyColumnLayout.TWO_COLUMNS
            val (spaceTitle, spaceItems) = HelixWhichKeyMenus.getMenu(" ") ?: return
            val panel2Cols = HelixWhichKeyPopup.createWhichKeyPanel(spaceTitle, spaceItems, editor)
            (panel2Cols.preferredSize.width >= 500).shouldBeTrue()

            settings.whichKeyColumnLayout = jp.titze.intellij.helix.settings.WhichKeyColumnLayout.THREE_COLUMNS
            val panel3Cols = HelixWhichKeyPopup.createWhichKeyPanel(spaceTitle, spaceItems, editor)
            (panel3Cols.preferredSize.width >= panel2Cols.preferredSize.width).shouldBeTrue()
        } finally {
            settings.whichKeyColumnLayout = original
        }
    }

    fun testOpenAndEditCommands() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor

        try {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
                executedActions.add(actionId)
                true
            }

            HelixCommandPopup.executeCommand("open", editor)
            executedActions.last() shouldBe "GotoFile"

            HelixCommandPopup.executeCommand("edit", editor)
            executedActions.last() shouldBe "GotoFile"

            HelixCommandPopup.executeCommand("e", editor)
            executedActions.last() shouldBe "GotoFile"

            HelixCommandPopup.executeCommand("file-picker", editor)
            executedActions.last() shouldBe "GotoFile"

            HelixCommandPopup.executeCommand("file_picker", editor)
            executedActions.last() shouldBe "GotoFile"

            HelixDirectoryFilePickerPopup.isShowing = false
            HelixCommandPopup.executeCommand("file-picker-in-current-directory", editor)
            HelixDirectoryFilePickerPopup.isShowing.shouldBeTrue()

            HelixDirectoryFilePickerPopup.isShowing = false
            HelixCommandPopup.executeCommand("file_picker_in_current_directory", editor)
            HelixDirectoryFilePickerPopup.isShowing.shouldBeTrue()

            val openCmd = HelixCommandPopup.COMMANDS.first { it.name == "open" }
            openCmd.matches("edit").shouldBeTrue()
            openCmd.matches("e").shouldBeTrue()
            openCmd.matches("file-picker").shouldBeTrue()
            openCmd.matches("file_picker").shouldBeTrue()
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }

        val testFile = java.io.File.createTempFile("opened_file", ".txt")
        testFile.writeText("hello opened")
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(testRootDisposable, testFile.canonicalPath)
        try {
            HelixCommandPopup.executeCommand("open ${testFile.absolutePath}", editor)
            testFile.exists().shouldBeTrue()
        } finally {
            testFile.delete()
        }
    }

    fun testBufferAndFindCommands() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor

        try {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
                executedActions.add(actionId)
                true
            }

            HelixCommandPopup.executeCommand("buffer", editor)
            executedActions.last() shouldBe "RecentFiles"

            HelixCommandPopup.executeCommand("b", editor)
            executedActions.last() shouldBe "RecentFiles"

            HelixCommandPopup.executeCommand("find", editor)
            executedActions.last() shouldBe "FindInPath"

            val bufCmd = HelixCommandPopup.COMMANDS.first { it.name == "buffer" }
            bufCmd.matches("b").shouldBeTrue()

            val findCmd = HelixCommandPopup.COMMANDS.first { it.name == "find" }
            findCmd.matches("find").shouldBeTrue()
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }
    }

    fun testBufferLifecycleCommands() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor

        try {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
                executedActions.add(actionId)
                true
            }

            HelixCommandPopup.executeCommand("buffer-close", editor)
            executedActions.last() shouldBe "CloseContent"
            HelixCommandPopup.executeCommand("bc", editor)
            executedActions.last() shouldBe "CloseContent"
            HelixCommandPopup.executeCommand("bclose", editor)
            executedActions.last() shouldBe "CloseContent"

            HelixCommandPopup.executeCommand("buffer-close-others", editor)
            executedActions.last() shouldBe "CloseAllEditorsButActive"
            HelixCommandPopup.executeCommand("bco", editor)
            executedActions.last() shouldBe "CloseAllEditorsButActive"

            HelixCommandPopup.executeCommand("buffer-close-all", editor)
            executedActions.last() shouldBe "CloseAllEditors"
            HelixCommandPopup.executeCommand("bca", editor)
            executedActions.last() shouldBe "CloseAllEditors"

            val preCountNext = executedActions.size
            HelixCommandPopup.executeCommand("bn 2", editor)
            executedActions.subList(preCountNext, executedActions.size) shouldBe listOf("NextTab", "NextTab")

            val preCountPrev = executedActions.size
            HelixCommandPopup.executeCommand("bp 2", editor)
            executedActions.subList(preCountPrev, executedActions.size) shouldBe listOf("PreviousTab", "PreviousTab")

            HelixCommandPopup.executeCommand("new", editor)
            executedActions.last() shouldBe "NewScratchFile"
            HelixCommandPopup.executeCommand("n", editor)
            executedActions.last() shouldBe "NewScratchFile"
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }
    }

    fun testStatusBarWidgetFactoryMetadata() {
        val factory = HelixStatusBarWidgetFactory()
        factory.id shouldBe HelixStatusBarWidget.WIDGET_ID
        factory.displayName shouldBe "Helix Mode"
        factory.isAvailable(project).shouldBeTrue()
        val widget = factory.createWidget(project)
        widget.ID() shouldBe HelixStatusBarWidget.WIDGET_ID
        (widget as HelixStatusBarWidget).component.shouldNotBeNull()
        factory.disposeWidget(widget)
    }

    fun testStatusBarWidgetSingleCaretNormalMode() {
        myFixture.configureByText("test.txt", "line1\nline2")
        val editor = myFixture.editor
        val widget = HelixStatusBarWidget(project)
        try {
            widget.attachEditor(editor)
            widget.getText() shouldBe "NOR"
            widget.getTooltipText() shouldBe "Helix Mode: Normal"
        } finally {
            widget.dispose()
        }
    }

    fun testStatusBarWidgetMultiCaretIndicator() {
        myFixture.configureByText("test.txt", "line1\nline2\nline3")
        val editor = myFixture.editor
        val caretModel = editor.caretModel
        val widget = HelixStatusBarWidget(project)
        try {
            widget.attachEditor(editor)
            widget.getText() shouldBe "NOR"

            caretModel.addCaret(editor.offsetToLogicalPosition(6), false)
            caretModel.caretCount shouldBe 2
            widget.getText() shouldBe "NOR 2 sel"
            widget.getTooltipText() shouldContain "2 selections"

            caretModel.addCaret(editor.offsetToLogicalPosition(12), false)
            caretModel.caretCount shouldBe 3
            widget.getText() shouldBe "NOR 3 sel"
            widget.getTooltipText() shouldContain "3 selections"

            HelixKeyHandler.handleKey(',', editor)
            caretModel.caretCount shouldBe 1
            widget.getText() shouldBe "NOR"
        } finally {
            widget.dispose()
        }
    }

    fun testStatusBarWidgetSelectedRegisterIndicator() {
        myFixture.configureByText("test.txt", "hello world")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        val widget = HelixStatusBarWidget(project)
        try {
            widget.attachEditor(editor)
            widget.getText() shouldBe "NOR"

            state.setSelectedRegister('a')
            widget.getText() shouldBe "NOR reg: a"
            widget.getTooltipText() shouldContain "Register: \"a"

            state.setSelectedRegister('_')
            widget.getText() shouldBe "NOR reg: _"

            state.clearSelectedRegister()
            widget.getText() shouldBe "NOR"
        } finally {
            widget.dispose()
        }
    }

    fun testStatusBarWidgetCombinedMultiCaretAndRegister() {
        myFixture.configureByText("test.txt", "line1\nline2\nline3")
        val editor = myFixture.editor
        val caretModel = editor.caretModel
        caretModel.addCaret(editor.offsetToLogicalPosition(6), false)
        caretModel.addCaret(editor.offsetToLogicalPosition(12), false)

        val state = HelixStateManager.getOrCreate(editor)
        val widget = HelixStatusBarWidget(project)
        try {
            widget.attachEditor(editor)
            state.setSelectedRegister('a')
            state.appendCountDigit('2')
            state.appendKey('g')

            widget.getText() shouldBe "NOR 3 sel reg: a 2 g-"
            widget.getTooltipText() shouldContain "3 selections"
            widget.getTooltipText() shouldContain "Register: \"a"
            widget.getTooltipText() shouldContain "Count: 2"
            widget.getTooltipText() shouldContain "Pending: g-"

            state.setMode(HelixMode.SELECT)
            widget.getText() shouldBe "SEL 3 sel"

            state.setMode(HelixMode.INSERT)
            widget.getText() shouldBe "INS 3 sel"
        } finally {
            widget.dispose()
        }
    }

    fun testStatusBarWidgetMacroRecordingIndicator() {
        myFixture.configureByText("test.txt", "hello")
        val editor = myFixture.editor
        val widget = HelixStatusBarWidget(project)
        try {
            widget.attachEditor(editor)
            widget.macroRecordingProvider = { true }
            widget.getText() shouldBe "NOR [REC]"
            widget.getTooltipText() shouldContain "Recording Macro"

            widget.macroRecordingProvider = { false }
            widget.getText() shouldBe "NOR"
        } finally {
            widget.dispose()
        }
    }

    fun testStatusBarWidgetClickConsumer() {
        myFixture.configureByText("test.txt", "hello")
        val editor = myFixture.editor
        val widget = HelixStatusBarWidget(project)
        try {
            widget.attachEditor(editor)
            val clickConsumer = widget.getClickConsumer()
            clickConsumer.shouldNotBeNull()

            val panel = widget.component
            val mouseListeners = panel.mouseListeners
            mouseListeners.shouldNotBeEmpty()

            val dummyEvent = MouseEvent(
                panel,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                10,
                10,
                1,
                false,
            )
            // Clicking opens command popup or registers popup safely without throwing
            clickConsumer.consume(dummyEvent)
            mouseListeners.forEach { it.mouseClicked(dummyEvent) }
        } finally {
            widget.dispose()
        }
    }

    fun testSpaceDebugMenuChordsAndExecution() {
        myFixture.configureByText("test.txt", "fun main() {\n    println(\"debug\")\n}")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        val executedActions = mutableListOf<String>()
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            // Space followed by G transitions to pending debug chord
            HelixKeyHandler.handleKey(' ', editor)
            state.pendingSequence shouldBe " "

            HelixKeyHandler.handleKey('G', editor)
            state.pendingSequence shouldBe " G"

            // b toggles line breakpoint
            HelixKeyHandler.handleKey('b', editor)
            state.pendingSequence.isEmpty().shouldBeTrue()
            executedActions.last() shouldBe "ToggleLineBreakpoint"

            // Test step in
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('s', editor)
            executedActions.last() shouldBe "StepInto"

            // Test step over (next)
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('n', editor)
            executedActions.last() shouldBe "StepOver"

            // Test step out
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('o', editor)
            executedActions.last() shouldBe "StepOut"

            // Test continue / resume
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('c', editor)
            executedActions.last() shouldBe "Resume"

            // Test terminate / stop
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('t', editor)
            executedActions.last() shouldBe "Stop"

            // Test restart / rerun
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('r', editor)
            executedActions.last() shouldBe "Rerun"

            // Test pause
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('p', editor)
            executedActions.last() shouldBe "Pause"

            // Test launch debug
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('l', editor)
            executedActions.last() shouldBe "Debug"

            // Test evaluate expression
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('k', editor)
            executedActions.last() shouldBe "EvaluateExpression"

            // Test edit breakpoint
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('e', editor)
            executedActions.last() shouldBe "EditBreakpoint"

            // Test view breakpoints
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('B', editor)
            executedActions.last() shouldBe "ViewBreakpoints"

            // Test variables / debug panel
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('G', editor)
            HelixKeyHandler.handleKey('v', editor)
            executedActions.last() shouldBe "ActivateDebugToolWindow"
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }
    }

    fun testDapCommandsExecution() {
        myFixture.configureByText("test.txt", "sample code")
        val editor = myFixture.editor

        val executedActions = mutableListOf<String>()
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            HelixCommandPopup.executeCommand("dap-toggle-breakpoint", editor)
            executedActions.last() shouldBe "ToggleLineBreakpoint"

            HelixCommandPopup.executeCommand("breakpoint", editor)
            executedActions.last() shouldBe "ToggleLineBreakpoint"

            HelixCommandPopup.executeCommand("dap-continue", editor)
            executedActions.last() shouldBe "Resume"

            HelixCommandPopup.executeCommand("continue", editor)
            executedActions.last() shouldBe "Resume"

            HelixCommandPopup.executeCommand("dap-step-in", editor)
            executedActions.last() shouldBe "StepInto"

            HelixCommandPopup.executeCommand("step-in", editor)
            executedActions.last() shouldBe "StepInto"

            HelixCommandPopup.executeCommand("dap-next", editor)
            executedActions.last() shouldBe "StepOver"

            HelixCommandPopup.executeCommand("step-over", editor)
            executedActions.last() shouldBe "StepOver"

            HelixCommandPopup.executeCommand("dap-step-out", editor)
            executedActions.last() shouldBe "StepOut"

            HelixCommandPopup.executeCommand("dap-terminate", editor)
            executedActions.last() shouldBe "Stop"

            HelixCommandPopup.executeCommand("dap-restart", editor)
            executedActions.last() shouldBe "Rerun"

            HelixCommandPopup.executeCommand("dap-pause", editor)
            executedActions.last() shouldBe "Pause"

            HelixCommandPopup.executeCommand("dap-launch", editor)
            executedActions.last() shouldBe "Debug"

            HelixCommandPopup.executeCommand("dap-variables", editor)
            executedActions.last() shouldBe "ActivateDebugToolWindow"

            HelixCommandPopup.executeCommand("dap-evaluate", editor)
            executedActions.last() shouldBe "EvaluateExpression"

            HelixCommandPopup.executeCommand("eval", editor)
            executedActions.last() shouldBe "EvaluateExpression"

            HelixCommandPopup.executeCommand("dap-edit-condition", editor)
            executedActions.last() shouldBe "EditBreakpoint"

            HelixCommandPopup.executeCommand("dap-view-breakpoints", editor)
            executedActions.last() shouldBe "ViewBreakpoints"

            HelixCommandPopup.executeCommand("breakpoints", editor)
            executedActions.last() shouldBe "ViewBreakpoints"

            HelixCommandPopup.executeCommand("signature-help", editor)
            executedActions.last() shouldBe "ParameterInfo"

            HelixCommandPopup.executeCommand("param-info", editor)
            executedActions.last() shouldBe "ParameterInfo"
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }
    }

    fun testProductivityCommandsExecution() {
        myFixture.configureByText("test.txt", "class Foo {}")
        val editor = myFixture.editor

        val executedActions = mutableListOf<String>()
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            HelixCommandPopup.executeCommand("optimize-imports", editor)
            executedActions.last() shouldBe "OptimizeImports"

            HelixCommandPopup.executeCommand("oi", editor)
            executedActions.last() shouldBe "OptimizeImports"

            HelixCommandPopup.executeCommand("goto-test", editor)
            executedActions.last() shouldBe "GotoTest"

            HelixCommandPopup.executeCommand("test", editor)
            executedActions.last() shouldBe "GotoTest"

            HelixCommandPopup.executeCommand("goto-declaration", editor)
            executedActions.last() shouldBe "GotoDeclarationOnly"

            HelixCommandPopup.executeCommand("declaration", editor)
            executedActions.last() shouldBe "GotoDeclarationOnly"

            HelixCommandPopup.executeCommand("goto-definition", editor)
            executedActions.last() shouldBe "GotoDeclaration"

            HelixCommandPopup.executeCommand("definition", editor)
            executedActions.last() shouldBe "GotoDeclaration"

            HelixCommandPopup.executeCommand("earlier", editor)
            executedActions.last() shouldBe "\$Undo"

            HelixCommandPopup.executeCommand("undo-earlier", editor)
            executedActions.last() shouldBe "\$Undo"

            HelixCommandPopup.executeCommand("later", editor)
            executedActions.last() shouldBe "\$Redo"

            HelixCommandPopup.executeCommand("redo-later", editor)
            executedActions.last() shouldBe "\$Redo"
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }
    }

    fun testWhichKeyDebugMenuRegistration() {
        val (title, items) = HelixWhichKeyMenus.getMenu(" G") ?: error("Debug menu not registered")
        title shouldBe "DEBUG (DAP) MENU"
        items.any { it.key == "b" && it.helixCommand == "dap_toggle_breakpoint" }.shouldBeTrue()
        items.any { it.key == "c" && it.helixCommand == "dap_continue" }.shouldBeTrue()
        items.any { it.key == "s" && it.helixCommand == "dap_step_in" }.shouldBeTrue()
        items.any { it.key == "n" && it.helixCommand == "dap_next" }.shouldBeTrue()
        items.any { it.key == "o" && it.helixCommand == "dap_step_out" }.shouldBeTrue()
        items.any { it.key == "p" && it.helixCommand == "dap_pause" }.shouldBeTrue()
        items.any { it.key == "l" && it.helixCommand == "dap_launch" }.shouldBeTrue()
        items.any { it.key == "r" && it.helixCommand == "dap_restart" }.shouldBeTrue()
        items.any { it.key == "t" && it.helixCommand == "dap_terminate" }.shouldBeTrue()
        items.any { it.key == "v" && it.helixCommand == "dap_variables" }.shouldBeTrue()
        items.any { it.key == "k" && it.helixCommand == "dap_evaluate" }.shouldBeTrue()
        items.any { it.key == "e" && it.helixCommand == "dap_edit_condition" }.shouldBeTrue()
        items.any { it.key == "B" && it.helixCommand == "dap_view_breakpoints" }.shouldBeTrue()
    }

    fun testViewFoldingChordsAndExecution() {
        myFixture.configureByText("test.txt", "fun test() {\n    val x = 1\n}")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        val executedActions = mutableListOf<String>()
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            // zc -> align_view_center (Helix standard)
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            state.pendingSequence shouldBe "z"
            HelixKeyHandler.handleKey('c', editor).shouldBeTrue()
            state.pendingSequence.isEmpty().shouldBeTrue()

            // zz -> align_view_center
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            state.pendingSequence.isEmpty().shouldBeTrue()

            // zt -> align_view_top
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('t', editor).shouldBeTrue()
            state.pendingSequence.isEmpty().shouldBeTrue()

            // zb -> align_view_bottom
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('b', editor).shouldBeTrue()
            state.pendingSequence.isEmpty().shouldBeTrue()

            // zm -> align_view_middle
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('m', editor).shouldBeTrue()
            state.pendingSequence.isEmpty().shouldBeTrue()

            // zj, zk -> scroll_down, scroll_up
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('j', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('k', editor).shouldBeTrue()

            // zd, zu -> half page
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('d', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('u', editor).shouldBeTrue()

            // zf, zF -> full page
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('f', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('z', editor).shouldBeTrue()
            HelixKeyHandler.handleKey('F', editor).shouldBeTrue()

            // Folding actions should NOT be invoked by View mode chords
            executedActions.isEmpty().shouldBeTrue()
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }
    }

    fun testFoldingCommandsExecution() {
        myFixture.configureByText("test.txt", "class Example {\n    fun method() {}\n}")
        val editor = myFixture.editor

        val executedActions = mutableListOf<String>()
        val originalExecutor = jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            HelixCommandPopup.executeCommand("fold", editor)
            executedActions.last() shouldBe "CollapseRegion"

            HelixCommandPopup.executeCommand("unfold", editor)
            executedActions.last() shouldBe "ExpandRegion"

            HelixCommandPopup.executeCommand("fold-all", editor)
            executedActions.last() shouldBe "CollapseAllRegions"

            HelixCommandPopup.executeCommand("fold_all", editor)
            executedActions.last() shouldBe "CollapseAllRegions"

            HelixCommandPopup.executeCommand("unfold-all", editor)
            executedActions.last() shouldBe "ExpandAllRegions"

            HelixCommandPopup.executeCommand("unfold_all", editor)
            executedActions.last() shouldBe "ExpandAllRegions"
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = originalExecutor
        }
    }

    fun testWhichKeyViewMenuRegistration() {
        val (title, items) = HelixWhichKeyMenus.getMenu("z") ?: error("View menu not registered")
        title shouldBe "VIEW MENU"
        items.any { it.key == "z" && it.helixCommand == "align_view_center" }.shouldBeTrue()
        items.any { it.key == "c" && it.helixCommand == "align_view_center" }.shouldBeTrue()
        items.any { it.key == "t" && it.helixCommand == "align_view_top" }.shouldBeTrue()
        items.any { it.key == "b" && it.helixCommand == "align_view_bottom" }.shouldBeTrue()
        items.any { it.key == "m" && it.helixCommand == "align_view_middle" }.shouldBeTrue()
        items.any { it.key == "j" && it.helixCommand == "scroll_down" }.shouldBeTrue()
        items.any { it.key == "k" && it.helixCommand == "scroll_up" }.shouldBeTrue()
        items.any { it.key == "d" && it.helixCommand == "page_cursor_half_down" }.shouldBeTrue()
        items.any { it.key == "u" && it.helixCommand == "page_cursor_half_up" }.shouldBeTrue()
        items.any { it.key == "f" && it.helixCommand == "page_down" }.shouldBeTrue()
        items.any { it.key == "F" && it.helixCommand == "page_up" }.shouldBeTrue()
    }

    fun testDirectoryFilePickerPopupScanFilterAndOpen() {
        myFixture.configureByText("test.txt", "sample")
        val rootDir = myFixture.tempDirFixture.findOrCreateDir("picker_test")
        val fileA = myFixture.addFileToProject("picker_test/alpha.kt", "fun a() {}").virtualFile
        myFixture.tempDirFixture.findOrCreateDir("picker_test/nested")
        val fileB = myFixture.addFileToProject("picker_test/nested/beta.kt", "fun b() {}").virtualFile
        myFixture.tempDirFixture.findOrCreateDir("picker_test/build")
        myFixture.addFileToProject("picker_test/build/ignored.txt", "repo")

        val items = HelixDirectoryFilePickerPopup.collectFiles(rootDir)
        items.any { it.fileName == "alpha.kt" }.shouldBeTrue()
        items.any { it.fileName == "beta.kt" }.shouldBeTrue()
        items.any { it.relativePath.contains("build") }.shouldBeFalse()

        val alphaItem = items.first { it.fileName == "alpha.kt" }
        alphaItem.matches("").shouldBeTrue()
        alphaItem.matches("alp").shouldBeTrue()
        alphaItem.matches("beta").shouldBeFalse()
        alphaItem.matches("ak").shouldBeTrue()

        val editor = myFixture.editor
        var openedFile: com.intellij.openapi.vfs.VirtualFile? = null
        HelixDirectoryFilePickerPopup.fileOpener = { _, f -> openedFile = f }
        try {
            HelixDirectoryFilePickerPopup.openSelectedFile(editor, fileA)
            openedFile shouldBe fileA
        } finally {
            HelixDirectoryFilePickerPopup.fileOpener = null
        }
    }

    fun testNewBuiltInHelixCommands() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = { id, _ ->
            executedActions.add(id)
            true
        }

        try {
            // :reset-diff-change and aliases
            executedActions.clear()
            HelixCommands.execute("reset-diff-change", editor)
            executedActions shouldBe listOf("Vcs.RollbackChangedLines")

            executedActions.clear()
            HelixCommands.execute("diffget", editor)
            executedActions shouldBe listOf("Vcs.RollbackChangedLines")

            executedActions.clear()
            HelixCommands.execute("diffg", editor)
            executedActions shouldBe listOf("Vcs.RollbackChangedLines")

            // :reflow and aliases
            executedActions.clear()
            HelixCommands.execute("reflow", editor)
            executedActions shouldBe listOf("FillParagraph")

            executedActions.clear()
            HelixCommands.execute("fill-paragraph", editor)
            executedActions shouldBe listOf("FillParagraph")

            // :file-explorer-buffer
            executedActions.clear()
            HelixCommands.execute("file-explorer-buffer", editor)
            executedActions shouldBe listOf("SelectInProjectView")

            // Space + 'E' chord
            executedActions.clear()
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('E', editor).shouldBeTrue()
            executedActions shouldBe listOf("SelectInProjectView")
        } finally {
            jp.titze.intellij.helix.action.HelixActionDelegate.actionExecutor = null
        }
    }

    fun testToggleHelixModeAndEligibility() {
        myFixture.configureByText("test.txt", "initial text")
        val editor = myFixture.editor
        val settings = HelixSettings.instance
        settings.enabled = true

        jp.titze.intellij.helix.editor.HelixEditorEligibility.isActive(editor).shouldBeTrue()

        // Toggle via command palette :toggle-helix
        HelixCommands.execute("toggle-helix", editor)
        settings.enabled.shouldBeFalse()
        jp.titze.intellij.helix.editor.HelixEditorEligibility.isActive(editor).shouldBeFalse()

        // Re-enable
        HelixCommands.execute("toggle-helix", editor)
        settings.enabled.shouldBeTrue()
        jp.titze.intellij.helix.editor.HelixEditorEligibility.isActive(editor).shouldBeTrue()
    }

    fun testUpdateAndExitCommands() {
        myFixture.configureByText("test.txt", "sample text")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { id, _ ->
            executedActions.add(id)
            true
        }
        try {
            executedActions.clear()
            HelixCommands.execute("update", editor)
            executedActions shouldBe listOf("SaveAll")

            executedActions.clear()
            HelixCommands.execute("u", editor)
            executedActions shouldBe listOf("SaveAll")

            executedActions.clear()
            HelixCommands.execute("exit", editor)
            executedActions shouldBe listOf("SaveAll", "CloseContent")

            executedActions.clear()
            HelixCommands.execute("xit", editor)
            executedActions shouldBe listOf("SaveAll", "CloseContent")
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testYankJoinCommand() {
        myFixture.configureByText("test.txt", "alpha beta gamma delta")
        val editor = myFixture.editor
        HelixRegisterManager.clear()

        // Create two carets selecting "alpha" and "gamma"
        HelixCaretUtils.applyCarets(editor, listOf(Pair(0, 5), Pair(11, 16)))
        editor.caretModel.caretCount shouldBe 2

        HelixCommands.execute("yank-join", editor)
        HelixRegisterManager.defaultRegister?.text shouldBe "alpha\ngamma"
    }

    fun testScratchSplitModeCommands() {
        myFixture.configureByText("test.txt", "sample text")
        val editor = myFixture.editor
        val settings = HelixSettings.instance
        settings.scratchSplitMode = HelixScratchSplitMode.INTERACTIVE

        HelixCommands.execute("set scratch-mode=buffer", editor)
        settings.scratchSplitMode shouldBe HelixScratchSplitMode.EMPTY_BUFFER

        HelixCommands.execute("set scratch-mode=interactive", editor)
        settings.scratchSplitMode shouldBe HelixScratchSplitMode.INTERACTIVE

        HelixCommands.execute("toggle-scratch-mode", editor)
        settings.scratchSplitMode shouldBe HelixScratchSplitMode.EMPTY_BUFFER

        HelixCommands.execute("toggle-scratch-mode", editor)
        settings.scratchSplitMode shouldBe HelixScratchSplitMode.INTERACTIVE
    }

    fun testSearchPopupCancellation() {
        myFixture.configureByText("test.txt", "line 1\nline 2\n")
        val editor = myFixture.editor

        HelixSearchPopup.show(editor)
        try {
            HelixSearchPopup.isShowing().shouldBeTrue()
            val dispatcher = HelixEventDispatcher()
            val ctrlC = KeyEvent(
                editor.contentComponent,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_C,
                'c',
            )
            val handled = dispatcher.dispatch(ctrlC)
            handled.shouldBeTrue()
            ctrlC.isConsumed.shouldBeTrue()
            HelixSearchPopup.isShowing().shouldBeFalse()
        } finally {
            HelixSearchPopup.cancelActive()
        }
    }

    fun testSelectRegexPopupCancellation() {
        myFixture.configureByText("test.txt", "line 1\nline 2\n")
        val editor = myFixture.editor

        HelixSelectRegexPopup.show(editor)
        try {
            HelixSelectRegexPopup.isShowing().shouldBeTrue()
            val dispatcher = HelixEventDispatcher()
            val ctrlC = KeyEvent(
                editor.contentComponent,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_C,
                'c',
            )
            val handled = dispatcher.dispatch(ctrlC)
            handled.shouldBeTrue()
            ctrlC.isConsumed.shouldBeTrue()
            HelixSelectRegexPopup.isShowing().shouldBeFalse()
        } finally {
            HelixSelectRegexPopup.cancelActive()
        }
    }

    fun testBuildAndRunWorkflowCommands() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { id, _ ->
            executedActions.add(id)
            true
        }

        try {
            executedActions.clear()
            HelixCommands.execute("run", editor)
            executedActions shouldBe listOf("RunClass")

            executedActions.clear()
            HelixCommands.execute("run-context", editor)
            executedActions shouldBe listOf("RunClass")

            executedActions.clear()
            HelixCommands.execute("run-target", editor)
            executedActions shouldBe listOf("Run")

            executedActions.clear()
            HelixCommands.execute("rerun", editor)
            executedActions shouldBe listOf("Rerun")

            executedActions.clear()
            HelixCommands.execute("build", editor)
            executedActions shouldBe listOf("CompileDirty")

            executedActions.clear()
            HelixCommands.execute("make", editor)
            executedActions shouldBe listOf("CompileDirty")

            executedActions.clear()
            HelixCommands.execute("stop", editor)
            executedActions shouldBe listOf("Stop")

            executedActions.clear()
            HelixDebugKeymap.handle('R', editor).shouldBeTrue()
            executedActions shouldBe listOf("RunClass")

            HelixWhichKeyMenus.debugItems.any { it.key == "R" && it.helixCommand == "run" }.shouldBeTrue()
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testGitAndVcsWorkflowCommands() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { id, _ ->
            executedActions.add(id)
            true
        }

        try {
            executedActions.clear()
            HelixCommands.execute("blame", editor)
            executedActions shouldBe listOf("Annotate")

            executedActions.clear()
            HelixCommands.execute("git-blame", editor)
            executedActions shouldBe listOf("Annotate")

            executedActions.clear()
            HelixCommands.execute("diff", editor)
            executedActions shouldBe listOf("Compare.SameVersion")

            executedActions.clear()
            HelixCommands.execute("show-diff", editor)
            executedActions shouldBe listOf("Compare.SameVersion")

            executedActions.clear()
            HelixCommands.execute("commit", editor)
            executedActions shouldBe listOf("CheckinProject")

            executedActions.clear()
            HelixCommands.execute("push", editor)
            executedActions shouldBe listOf("Vcs.Push")

            executedActions.clear()
            HelixCommands.execute("pull", editor)
            executedActions shouldBe listOf("Vcs.UpdateProject")

            executedActions.clear()
            HelixCommands.execute("vcs-log", editor)
            executedActions shouldBe listOf("Vcs.Show.Log")
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testRefactoringAndNavigationWorkflowCommands() {
        myFixture.configureByText("test.txt", "sample")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { id, _ ->
            executedActions.add(id)
            true
        }

        try {
            executedActions.clear()
            HelixCommands.execute("goto-implementation", editor)
            executedActions shouldBe listOf("GotoImplementation")

            executedActions.clear()
            HelixCommands.execute("goto-type-definition", editor)
            executedActions shouldBe listOf("GotoTypeDeclaration")

            executedActions.clear()
            HelixCommands.execute("goto-reference", editor)
            executedActions shouldBe listOf("FindUsages")

            executedActions.clear()
            HelixCommands.execute("goto-super", editor)
            executedActions shouldBe listOf("GotoSuperMethod")

            executedActions.clear()
            HelixCommands.execute("call-hierarchy", editor)
            executedActions shouldBe listOf("CallHierarchy")

            executedActions.clear()
            HelixCommands.execute("type-hierarchy", editor)
            executedActions shouldBe listOf("TypeHierarchy")

            executedActions.clear()
            HelixCommands.execute("file-structure", editor)
            executedActions shouldBe listOf("FileStructurePopup")

            executedActions.clear()
            HelixCommands.execute("extract-variable", editor)
            executedActions shouldBe listOf("IntroduceVariable")

            executedActions.clear()
            HelixCommands.execute("var", editor)
            executedActions shouldBe listOf("IntroduceVariable")

            executedActions.clear()
            HelixCommands.execute("extract-method", editor)
            executedActions shouldBe listOf("ExtractMethod")

            executedActions.clear()
            HelixCommands.execute("method", editor)
            executedActions shouldBe listOf("ExtractMethod")

            executedActions.clear()
            HelixCommands.execute("extract-constant", editor)
            executedActions shouldBe listOf("IntroduceConstant")

            executedActions.clear()
            HelixCommands.execute("const", editor)
            executedActions shouldBe listOf("IntroduceConstant")

            executedActions.clear()
            HelixCommands.execute("extract-field", editor)
            executedActions shouldBe listOf("IntroduceField")

            executedActions.clear()
            HelixCommands.execute("field", editor)
            executedActions shouldBe listOf("IntroduceField")

            executedActions.clear()
            HelixCommands.execute("extract-parameter", editor)
            executedActions shouldBe listOf("IntroduceParameter")

            executedActions.clear()
            HelixCommands.execute("param", editor)
            executedActions shouldBe listOf("IntroduceParameter")

            executedActions.clear()
            HelixCommands.execute("inline", editor)
            executedActions shouldBe listOf("Inline")

            executedActions.clear()
            HelixCommands.execute("change-signature", editor)
            executedActions shouldBe listOf("ChangeSignature")

            executedActions.clear()
            HelixCommands.execute("cs", editor)
            executedActions shouldBe listOf("ChangeSignature")

            executedActions.clear()
            HelixCommands.execute("move", editor)
            executedActions shouldBe listOf("Move")

            executedActions.clear()
            HelixCommands.execute("override", editor)
            executedActions shouldBe listOf("OverrideMethods")

            executedActions.clear()
            HelixCommands.execute("implement", editor)
            executedActions shouldBe listOf("ImplementMethods")

            executedActions.clear()
            HelixCommands.execute("surround-with", editor)
            executedActions shouldBe listOf("SurroundWith")

            executedActions.clear()
            HelixCommands.execute("surround", editor)
            executedActions shouldBe listOf("SurroundWith")

            executedActions.clear()
            HelixCommands.execute("refactor-this", editor)
            executedActions shouldBe listOf("Refactorings.QuickListPopupAction")

            executedActions.clear()
            HelixCommands.execute("refactor", editor)
            executedActions shouldBe listOf("Refactorings.QuickListPopupAction")

            executedActions.clear()
            HelixCommands.execute("generate", editor)
            executedActions shouldBe listOf("Generate")

            executedActions.clear()
            HelixCommands.execute("replace-in-path", editor)
            executedActions shouldBe listOf("ReplaceInPath")
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testToggleCommentsInSelectModeReturnsToNormalMode() {
        myFixture.configureByText("test.txt", "val a = 1")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.SELECT)

        var executedAction: String? = null
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedAction = actionId
            true
        }
        try {
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('c', editor)
            executedAction shouldBe "CommentByLineComment"
            state.mode shouldBe HelixMode.NORMAL
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testSortLinesInSelectModeReturnsToNormalMode() {
        myFixture.configureByText("test.txt", "banana\napple\ncherry")
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.setSelection(0, editor.document.textLength)
        val state = HelixStateManager.getOrCreate(editor)
        state.setMode(HelixMode.SELECT)

        HelixActions.sortLines(editor, reverse = false)
        editor.document.text shouldBe "apple\nbanana\ncherry"
        state.mode shouldBe HelixMode.NORMAL
    }

    fun testSpaceRefactorMenuChordsAndExecution() {
        myFixture.configureByText("test.txt", "val x = 42\nfun sample() = x")
        val editor = myFixture.editor
        val state = HelixStateManager.getOrCreate(editor)

        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            // Verify Which-Key space menu contains m with Refactor menu label
            val spaceMenu = HelixWhichKeyMenus.getMenu(" ")
            spaceMenu.shouldNotBeNull()
            val spaceItemM = spaceMenu.second.find { it.key == "m" }
            spaceItemM.shouldNotBeNull()
            spaceItemM.label shouldBe "Refactor menu"
            spaceItemM.helixCommand shouldBe "refactor_menu"

            // Verify Which-Key refactor menu metadata
            val menu = HelixWhichKeyMenus.getMenu(" m")
            menu.shouldNotBeNull()
            menu.first shouldBe "REFACTOR MENU"
            menu.second.shouldNotBeEmpty()
            menu.second.any { it.key == "v" && it.helixCommand == "extract_variable" }.shouldBeTrue()
            menu.second.any { it.key == "m" && it.helixCommand == "extract_method" }.shouldBeTrue()
            menu.second.any { it.key == "i" && it.helixCommand == "inline" }.shouldBeTrue()

            // Space followed by m transitions to pending refactor chord
            HelixKeyHandler.handleKey(' ', editor)
            state.pendingSequence shouldBe " "

            HelixKeyHandler.handleKey('m', editor)
            state.pendingSequence shouldBe " m"

            // Test extract variable
            HelixKeyHandler.handleKey('v', editor)
            state.pendingSequence.isEmpty().shouldBeTrue()
            executedActions.last() shouldBe "IntroduceVariable"

            // Test extract method
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('m', editor)
            executedActions.last() shouldBe "ExtractMethod"

            // Test extract constant
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('c', editor)
            executedActions.last() shouldBe "IntroduceConstant"

            // Test extract field
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('f', editor)
            executedActions.last() shouldBe "IntroduceField"

            // Test extract parameter
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('p', editor)
            executedActions.last() shouldBe "IntroduceParameter"

            // Test inline
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('i', editor)
            executedActions.last() shouldBe "Inline"

            // Test change signature
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('s', editor)
            executedActions.last() shouldBe "ChangeSignature"

            // Test rename symbol
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('r', editor)
            executedActions.last() shouldBe "RenameElement"

            // Test move
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('M', editor)
            executedActions.last() shouldBe "Move"

            // Test surround with
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('w', editor)
            executedActions.last() shouldBe "SurroundWith"

            // Test override methods
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('o', editor)
            executedActions.last() shouldBe "OverrideMethods"

            // Test implement methods
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('I', editor)
            executedActions.last() shouldBe "ImplementMethods"

            // Test refactor this popup
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('?', editor)
            executedActions.last() shouldBe "Refactorings.QuickListPopupAction"

            // Test space m in SELECT mode resets to NORMAL
            state.setMode(HelixMode.SELECT)
            HelixKeyHandler.handleKey(' ', editor)
            HelixKeyHandler.handleKey('m', editor)
            HelixKeyHandler.handleKey('v', editor)
            executedActions.last() shouldBe "IntroduceVariable"
            state.mode shouldBe HelixMode.NORMAL
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testVisualTogglePaletteCommands() {
        myFixture.configureByText("test.txt", "sample visual toggle text")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            HelixCommands.execute("toggle-soft-wrap", editor)
            executedActions.last() shouldBe "EditorToggleUseSoftWraps"

            HelixCommands.execute("soft-wrap", editor)
            executedActions.last() shouldBe "EditorToggleUseSoftWraps"

            HelixCommands.execute("wrap", editor)
            executedActions.last() shouldBe "EditorToggleUseSoftWraps"

            HelixCommands.execute("toggle-inlay-hints", editor)
            executedActions.last() shouldBe "ToggleInlayHintsGloballyAction"

            HelixCommands.execute("inlay-hints", editor)
            executedActions.last() shouldBe "ToggleInlayHintsGloballyAction"

            HelixCommands.execute("hints", editor)
            executedActions.last() shouldBe "ToggleInlayHintsGloballyAction"

            HelixCommands.execute("toggle-sticky-context", editor)
            executedActions.last() shouldBe "EditorGutterToggleGlobalStickyLines"

            HelixCommands.execute("sticky-context", editor)
            executedActions.last() shouldBe "EditorGutterToggleGlobalStickyLines"

            HelixCommands.execute("sticky-lines", editor)
            executedActions.last() shouldBe "EditorGutterToggleGlobalStickyLines"

            HelixCommands.execute("toggle-line-numbers", editor)
            executedActions.last() shouldBe "EditorToggleShowLineNumbers"

            HelixCommands.execute("line-numbers", editor)
            executedActions.last() shouldBe "EditorToggleShowLineNumbers"

            HelixCommands.execute("numbers", editor)
            executedActions.last() shouldBe "EditorToggleShowLineNumbers"

            HelixCommands.execute("toggle-render-whitespace", editor)
            executedActions.last() shouldBe "EditorToggleShowWhitespaces"

            HelixCommands.execute("whitespace", editor)
            executedActions.last() shouldBe "EditorToggleShowWhitespaces"

            HelixCommands.execute("whitespaces", editor)
            executedActions.last() shouldBe "EditorToggleShowWhitespaces"

            HelixCommands.execute("toggle-breadcrumbs", editor)
            executedActions.last() shouldBe "EditorToggleShowBreadcrumbs"

            HelixCommands.execute("breadcrumbs", editor)
            executedActions.last() shouldBe "EditorToggleShowBreadcrumbs"
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }

    fun testSetOptionAndToggleOptionCommands() {
        myFixture.configureByText("test.txt", "option testing buffer")
        val editor = myFixture.editor

        // Soft wrap: set and toggle
        HelixCommands.execute("set-option soft-wrap.enable true", editor)
        editor.settings.isUseSoftWraps.shouldBeTrue()

        HelixCommands.execute("set soft-wrap.enable false", editor)
        editor.settings.isUseSoftWraps.shouldBeFalse()

        HelixCommands.execute("set soft-wrap=on", editor)
        editor.settings.isUseSoftWraps.shouldBeTrue()

        HelixCommands.execute("set soft-wrap=off", editor)
        editor.settings.isUseSoftWraps.shouldBeFalse()

        HelixCommands.execute("toggle-option soft-wrap.enable", editor)
        editor.settings.isUseSoftWraps.shouldBeTrue()

        HelixCommands.execute("toggle soft-wrap", editor)
        editor.settings.isUseSoftWraps.shouldBeFalse()

        // Whitespace: set and toggle
        HelixCommands.execute("set-option whitespace.render all", editor)
        editor.settings.isWhitespacesShown.shouldBeTrue()

        HelixCommands.execute("set whitespace.render none", editor)
        editor.settings.isWhitespacesShown.shouldBeFalse()

        HelixCommands.execute("toggle-option whitespace.render", editor)
        editor.settings.isWhitespacesShown.shouldBeTrue()

        HelixCommands.execute("toggle whitespace", editor)
        editor.settings.isWhitespacesShown.shouldBeFalse()

        // Line numbers: set and toggle
        HelixCommands.execute("set-option line-number absolute", editor)
        editor.settings.isLineNumbersShown.shouldBeTrue()

        HelixCommands.execute("set line-numbers=none", editor)
        editor.settings.isLineNumbersShown.shouldBeFalse()

        HelixCommands.execute("toggle-option line-numbers", editor)
        editor.settings.isLineNumbersShown.shouldBeTrue()

        HelixCommands.execute("toggle line-number", editor)
        editor.settings.isLineNumbersShown.shouldBeFalse()

        // Breadcrumbs: set and toggle
        val ext = com.intellij.openapi.editor.ex.EditorSettingsExternalizable.getInstance()
        HelixCommands.execute("set-option breadcrumbs.enable true", editor)
        ext.isBreadcrumbsShown.shouldBeTrue()

        HelixCommands.execute("set breadcrumbs.enable false", editor)
        ext.isBreadcrumbsShown.shouldBeFalse()

        HelixCommands.execute("toggle-option breadcrumbs.enable", editor)
        ext.isBreadcrumbsShown.shouldBeTrue()

        HelixCommands.execute("toggle breadcrumbs", editor)
        ext.isBreadcrumbsShown.shouldBeFalse()

        // search-ui: set and toggle via canonical commands
        val settings = HelixSettings.instance
        HelixCommands.execute("set-option search-ui popup", editor)
        settings.searchUiMode shouldBe HelixSearchUiMode.POPUP

        HelixCommands.execute("set search-ui inline", editor)
        settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX

        HelixCommands.execute("toggle-option search-ui", editor)
        settings.searchUiMode shouldBe HelixSearchUiMode.POPUP

        HelixCommands.execute("toggle search-ui", editor)
        settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX

        // line-nav: set and toggle via canonical commands
        HelixCommands.execute("set-option line-nav vim", editor)
        settings.lineNavigationMode shouldBe HelixLineNavigationMode.VIM_STANDARD

        HelixCommands.execute("set line-nav helix", editor)
        settings.lineNavigationMode shouldBe HelixLineNavigationMode.HELIX_STANDARD

        HelixCommands.execute("toggle-option line-nav", editor)
        settings.lineNavigationMode shouldBe HelixLineNavigationMode.VIM_STANDARD

        HelixCommands.execute("toggle line-nav", editor)
        settings.lineNavigationMode shouldBe HelixLineNavigationMode.HELIX_STANDARD

        // scratch-mode: set and toggle via canonical commands
        HelixCommands.execute("set-option scratch-mode buffer", editor)
        settings.scratchSplitMode shouldBe HelixScratchSplitMode.EMPTY_BUFFER

        HelixCommands.execute("set scratch-mode interactive", editor)
        settings.scratchSplitMode shouldBe HelixScratchSplitMode.INTERACTIVE

        HelixCommands.execute("toggle-option scratch-mode", editor)
        settings.scratchSplitMode shouldBe HelixScratchSplitMode.EMPTY_BUFFER

        HelixCommands.execute("toggle scratch-mode", editor)
        settings.scratchSplitMode shouldBe HelixScratchSplitMode.INTERACTIVE
    }

    fun testHelixCommandRegistryVisualToggles() {
        myFixture.configureByText("test.txt", "registry test buffer")
        val editor = myFixture.editor
        val executedActions = mutableListOf<String>()
        HelixActionDelegate.actionExecutor = { actionId, _ ->
            executedActions.add(actionId)
            true
        }

        try {
            jp.titze.intellij.helix.keymap.HelixCommandRegistry.execute("toggle_soft_wrap", editor)
            executedActions.last() shouldBe "EditorToggleUseSoftWraps"

            jp.titze.intellij.helix.keymap.HelixCommandRegistry.execute("toggle_inlay_hints", editor)
            executedActions.last() shouldBe "ToggleInlayHintsGloballyAction"

            jp.titze.intellij.helix.keymap.HelixCommandRegistry.execute("toggle_sticky_context", editor)
            executedActions.last() shouldBe "EditorGutterToggleGlobalStickyLines"

            jp.titze.intellij.helix.keymap.HelixCommandRegistry.execute("toggle_line_numbers", editor)
            executedActions.last() shouldBe "EditorToggleShowLineNumbers"

            jp.titze.intellij.helix.keymap.HelixCommandRegistry.execute("toggle_whitespace", editor)
            executedActions.last() shouldBe "EditorToggleShowWhitespaces"

            jp.titze.intellij.helix.keymap.HelixCommandRegistry.execute("toggle_breadcrumbs", editor)
            executedActions.last() shouldBe "EditorToggleShowBreadcrumbs"
        } finally {
            HelixActionDelegate.actionExecutor = null
        }
    }
}
