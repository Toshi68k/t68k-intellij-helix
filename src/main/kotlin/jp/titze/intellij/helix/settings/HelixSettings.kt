package jp.titze.intellij.helix.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class HelixSearchUiMode(val displayName: String) {
    STOCK_HELIX("Stock Helix (Inline bottom bar with live search/select-as-you-type)"),
    POPUP("Popup Window (Floating dialog)"),
}

enum class HelixColorTheme(val displayName: String) {
    SYNC("Sync with IDE"),
    DARK("Dark"),
    LIGHT("Light"),
}

enum class WhichKeyHintMode(val displayName: String) {
    HELIX_COMMAND("Helix Command (snake_case)"),
    INTELLIJ_ACTION("IntelliJ Action ID (PascalCase)"),
}

enum class WhichKeyColumnLayout(val displayName: String, val maxColumns: Int) {
    THREE_COLUMNS("Up to 3 columns (Compact / Shorter)", 3),
    TWO_COLUMNS("Up to 2 columns (Classic / Narrower)", 2),
}

class HelixSettingsState {
    var searchUiMode: String = HelixSearchUiMode.STOCK_HELIX.name
    var jumpListMaxEntries: Int = HelixSettings.DEFAULT_JUMP_LIST_MAX_ENTRIES
    var promptHistoryMaxEntries: Int = HelixSettings.DEFAULT_PROMPT_HISTORY_MAX_ENTRIES
    var colorTheme: String = HelixColorTheme.SYNC.name
    var resetToNormalOnTabSwitch: Boolean = true
    var syncClipboardWithDefaultRegister: Boolean = true
    var enableWhichKeyPopups: Boolean = true
    var whichKeyHintMode: String = WhichKeyHintMode.HELIX_COMMAND.name
    var whichKeyColumnLayout: String = WhichKeyColumnLayout.THREE_COLUMNS.name
    var searchHistory: MutableList<String> = mutableListOf()
    var regexHistory: MutableList<String> = mutableListOf()
    var shellHistory: MutableList<String> = mutableListOf()
}

@Service(Service.Level.APP)
@State(
    name = "jp.titze.intellij.helix.settings.HelixSettings",
    storages = [Storage("helix_settings.xml")],
)
class HelixSettings : PersistentStateComponent<HelixSettingsState> {
    private var myState = HelixSettingsState()

    var searchUiMode: HelixSearchUiMode
        get() = try {
            HelixSearchUiMode.valueOf(myState.searchUiMode)
        } catch (e: Exception) {
            HelixSearchUiMode.STOCK_HELIX
        }
        set(value) {
            myState.searchUiMode = value.name
        }

    var jumpListMaxEntries: Int
        get() {
            val entries = myState.jumpListMaxEntries
            return if (entries <= 0) {
                DEFAULT_JUMP_LIST_MAX_ENTRIES
            } else {
                entries.coerceIn(MIN_JUMP_LIST_ENTRIES, MAX_JUMP_LIST_ENTRIES)
            }
        }
        set(value) {
            myState.jumpListMaxEntries = value.coerceIn(MIN_JUMP_LIST_ENTRIES, MAX_JUMP_LIST_ENTRIES)
        }

    var promptHistoryMaxEntries: Int
        get() {
            val entries = myState.promptHistoryMaxEntries
            return if (entries <= 0) {
                DEFAULT_PROMPT_HISTORY_MAX_ENTRIES
            } else {
                entries.coerceIn(MIN_PROMPT_HISTORY_ENTRIES, MAX_PROMPT_HISTORY_ENTRIES)
            }
        }
        set(value) {
            myState.promptHistoryMaxEntries =
                value.coerceIn(MIN_PROMPT_HISTORY_ENTRIES, MAX_PROMPT_HISTORY_ENTRIES)
        }

    fun getPromptHistoryList(category: jp.titze.intellij.helix.ui.HelixPromptCategory): MutableList<String> =
        when (category) {
            jp.titze.intellij.helix.ui.HelixPromptCategory.SEARCH -> myState.searchHistory
            jp.titze.intellij.helix.ui.HelixPromptCategory.REGEX -> myState.regexHistory
            jp.titze.intellij.helix.ui.HelixPromptCategory.SHELL -> myState.shellHistory
        }

    var colorTheme: HelixColorTheme
        get() = try {
            HelixColorTheme.valueOf(myState.colorTheme)
        } catch (e: Exception) {
            HelixColorTheme.SYNC
        }
        set(value) {
            myState.colorTheme = value.name
        }

    var resetToNormalOnTabSwitch: Boolean
        get() = myState.resetToNormalOnTabSwitch
        set(value) {
            myState.resetToNormalOnTabSwitch = value
        }

    var syncClipboardWithDefaultRegister: Boolean
        get() = myState.syncClipboardWithDefaultRegister
        set(value) {
            myState.syncClipboardWithDefaultRegister = value
        }

    var enableWhichKeyPopups: Boolean
        get() = myState.enableWhichKeyPopups
        set(value) {
            myState.enableWhichKeyPopups = value
        }

    var whichKeyHintMode: WhichKeyHintMode
        get() = try {
            WhichKeyHintMode.valueOf(myState.whichKeyHintMode)
        } catch (e: Exception) {
            WhichKeyHintMode.HELIX_COMMAND
        }
        set(value) {
            myState.whichKeyHintMode = value.name
        }

    var whichKeyColumnLayout: WhichKeyColumnLayout
        get() = try {
            WhichKeyColumnLayout.valueOf(myState.whichKeyColumnLayout)
        } catch (e: Exception) {
            WhichKeyColumnLayout.THREE_COLUMNS
        }
        set(value) {
            myState.whichKeyColumnLayout = value.name
        }

    override fun getState(): HelixSettingsState = myState

    override fun loadState(state: HelixSettingsState) {
        myState = state
    }

    companion object {
        const val DEFAULT_JUMP_LIST_MAX_ENTRIES = 100
        const val MIN_JUMP_LIST_ENTRIES = 10
        const val MAX_JUMP_LIST_ENTRIES = 1000

        const val DEFAULT_PROMPT_HISTORY_MAX_ENTRIES = 100
        const val MIN_PROMPT_HISTORY_ENTRIES = 10
        const val MAX_PROMPT_HISTORY_ENTRIES = 500

        val instance: HelixSettings
            get() {
                val app = ApplicationManager.getApplication()
                return app?.getService(HelixSettings::class.java) ?: HelixSettings()
            }
    }
}
