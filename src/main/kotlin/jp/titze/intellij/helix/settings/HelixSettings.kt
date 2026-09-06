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

class HelixSettingsState {
    var searchUiMode: String = HelixSearchUiMode.STOCK_HELIX.name
    var jumpListMaxEntries: Int = HelixSettings.DEFAULT_JUMP_LIST_MAX_ENTRIES
    var colorTheme: String = HelixColorTheme.SYNC.name
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

    var colorTheme: HelixColorTheme
        get() = try {
            HelixColorTheme.valueOf(myState.colorTheme)
        } catch (e: Exception) {
            HelixColorTheme.SYNC
        }
        set(value) {
            myState.colorTheme = value.name
        }

    override fun getState(): HelixSettingsState = myState

    override fun loadState(state: HelixSettingsState) {
        myState = state
    }

    companion object {
        const val DEFAULT_JUMP_LIST_MAX_ENTRIES = 100
        const val MIN_JUMP_LIST_ENTRIES = 10
        const val MAX_JUMP_LIST_ENTRIES = 1000

        val instance: HelixSettings
            get() {
                val app = ApplicationManager.getApplication()
                return app?.getService(HelixSettings::class.java) ?: HelixSettings()
            }
    }
}
