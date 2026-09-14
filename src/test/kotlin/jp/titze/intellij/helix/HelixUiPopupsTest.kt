package jp.titze.intellij.helix

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jp.titze.intellij.helix.command.HelixCommandPopup
import jp.titze.intellij.helix.keymap.HelixKeyHandler
import jp.titze.intellij.helix.settings.HelixSearchUiMode
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixPromptBar
import jp.titze.intellij.helix.ui.HelixPromptType
import jp.titze.intellij.helix.ui.HelixWhichKeyMenus
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup

class HelixUiPopupsTest : BasePlatformTestCase() {

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

            // Space + 'e' (project tree)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('e', editor).shouldBeTrue()
            executedActions.contains("ActivateProjectToolWindow").shouldBeTrue()

            // Space + '.' (select in project)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('.', editor).shouldBeTrue()
            executedActions.contains("SelectInProjectView").shouldBeTrue()

            // Space + 'g' (git changes)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('g', editor).shouldBeTrue()

            // Space + ''' (last picker)
            jp.titze.intellij.helix.keymap.HelixSpaceKeymap.handle('\'', editor).shouldBeTrue()
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

        // Ensure all commands follow Helix snake_case convention
        val regex = Regex("^[a-z0-9_]+( <[a-z0-9_]+>)*$")
        for (item in spaceItems) {
            if (item.helixCommand.isNotEmpty()) {
                val matches = regex.matches(item.helixCommand)
                matches.shouldBeTrue()
            }
        }
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
            (panel2Cols.preferredSize.width in 500..700).shouldBeTrue()

            settings.whichKeyColumnLayout = jp.titze.intellij.helix.settings.WhichKeyColumnLayout.THREE_COLUMNS
            val panel3Cols = HelixWhichKeyPopup.createWhichKeyPanel(spaceTitle, spaceItems, editor)
            (panel3Cols.preferredSize.width >= 700).shouldBeTrue()
        } finally {
            settings.whichKeyColumnLayout = original
        }
    }
}
