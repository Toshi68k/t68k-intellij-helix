package jp.titze.intellij.helix.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import jp.titze.intellij.helix.jumplist.HelixJumpListService
import jp.titze.intellij.helix.ui.HelixTheme

class HelixSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        // Reset settings to defaults
        val settings = HelixSettings.instance
        settings.searchUiMode = HelixSearchUiMode.STOCK_HELIX
        settings.jumpListMaxEntries = HelixSettings.DEFAULT_JUMP_LIST_MAX_ENTRIES
        settings.colorTheme = HelixColorTheme.SYNC
        super.tearDown()
    }

    fun testDefaultSettings() {
        val settings = HelixSettings.instance
        settings.searchUiMode shouldBe HelixSearchUiMode.STOCK_HELIX
        settings.jumpListMaxEntries shouldBe 100
        settings.colorTheme shouldBe HelixColorTheme.SYNC
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

        configurable.disposeUIResources()
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
}
