package jp.titze.intellij.helix.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service

/**
 * Application-level service managing plugin lifecycle and dynamic unloading.
 *
 * When the plugin is unloaded without restarting the IDE, [dispose] restores
 * original editor action handlers, raw typed action handler, event dispatchers,
 * and deactivates any active Helix editor states.
 */
@Service(Service.Level.APP)
class HelixPluginLifecycle : Disposable {

    override fun dispose() {
        HelixEventDispatcher.uninstall()
        HelixTypedActionHandler.uninstall()
        HelixEditorActionHandler.uninstall()
        HelixEditorEligibility.deactivateAll()
    }
}
