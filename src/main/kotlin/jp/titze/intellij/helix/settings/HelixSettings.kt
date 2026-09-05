package jp.titze.intellij.helix.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class HelixSearchUiMode(val displayName: String) {
    STOCK_HELIX("Stock Helix (Inline bottom bar with live search/select-as-you-type)"),
    POPUP("Popup Window (Floating dialog)")
}

class HelixSettingsState {
    var searchUiMode: String = HelixSearchUiMode.STOCK_HELIX.name
}

@Service(Service.Level.APP)
@State(
    name = "jp.titze.intellij.helix.settings.HelixSettings",
    storages = [Storage("helix_settings.xml")]
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

    override fun getState(): HelixSettingsState = myState

    override fun loadState(state: HelixSettingsState) {
        myState = state
    }

    companion object {
        val instance: HelixSettings
            get() {
                val app = ApplicationManager.getApplication()
                return app?.getService(HelixSettings::class.java) ?: HelixSettings()
            }
    }
}
