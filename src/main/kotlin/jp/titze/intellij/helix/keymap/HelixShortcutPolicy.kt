package jp.titze.intellij.helix.keymap

import com.intellij.openapi.util.SystemInfo
import jp.titze.intellij.helix.settings.HelixSettings
import java.awt.event.KeyEvent

/** Mode group a Ctrl chord is evaluated in. SELECT shares the NORMAL policy. */
enum class HelixKeyContext { NORMAL, INSERT }

/** Who receives a Ctrl chord inside a Helix-active editor. */
enum class HelixKeyOwner { HELIX, IDE }

/**
 * A Ctrl chord Helix can claim.
 *
 * @param letter key letter (also used as persistence id, e.g. `INSERT:A`).
 * @param normalBundleKey HelixBundle key describing the Normal/Select behaviour, or `null` if unused there.
 * @param insertBundleKey HelixBundle key describing the Insert behaviour, or `null` if unused there.
 * @param insertIdeOnWinLinux whether Insert mode defaults to the IDE on Windows/Linux, where Ctrl is the primary
 *   IDE modifier (Select All, Cut, Save, Duplicate, ...).
 */
data class HelixCtrlKey(
    val letter: Char,
    val normalBundleKey: String?,
    val insertBundleKey: String?,
    val insertIdeOnWinLinux: Boolean = true,
) {
    val keyCode: Int get() = KeyEvent.getExtendedKeyCodeForChar(letter.code)

    fun supports(context: HelixKeyContext): Boolean = when (context) {
        HelixKeyContext.NORMAL -> normalBundleKey != null
        HelixKeyContext.INSERT -> insertBundleKey != null
    }
}

/**
 * Decides whether a Ctrl chord in a Helix-active editor is handled by Helix or passed through to the IDE keymap.
 *
 * Defaults:
 * - macOS: Helix everywhere (IDE shortcuts use Cmd, so nothing clashes).
 * - Windows/Linux, Normal/Select: Helix (including Ctrl-c comment and Ctrl-s save jump).
 * - Windows/Linux, Insert: IDE for keys flagged [HelixCtrlKey.insertIdeOnWinLinux].
 *
 * Overrides are stored in [HelixSettings.ctrlKeyOverrides].
 */
object HelixShortcutPolicy {

    val KEYS: List<HelixCtrlKey> = listOf(
        HelixCtrlKey('A', "ctrlKey.A.normal", "ctrlKey.A.insert"),
        HelixCtrlKey('B', "ctrlKey.B.normal", null),
        HelixCtrlKey('C', "ctrlKey.C.normal", null),
        HelixCtrlKey('D', "ctrlKey.D.normal", "ctrlKey.D.insert"),
        HelixCtrlKey('E', null, "ctrlKey.E.insert"),
        HelixCtrlKey('F', "ctrlKey.F.normal", null),
        HelixCtrlKey('H', null, "ctrlKey.H.insert"),
        HelixCtrlKey('I', "ctrlKey.I.normal", null),
        HelixCtrlKey('K', null, "ctrlKey.K.insert"),
        HelixCtrlKey('O', "ctrlKey.O.normal", null),
        HelixCtrlKey('P', null, "ctrlKey.P.insert", insertIdeOnWinLinux = false),
        HelixCtrlKey('R', null, "ctrlKey.R.insert"),
        HelixCtrlKey('S', "ctrlKey.S.normal", "ctrlKey.S.insert"),
        HelixCtrlKey('U', "ctrlKey.U.normal", "ctrlKey.U.insert"),
        HelixCtrlKey('W', "ctrlKey.W.normal", "ctrlKey.W.insert"),
        HelixCtrlKey('X', "ctrlKey.X.normal", "ctrlKey.X.insert"),
    )

    /** Test hook to simulate a platform; `null` uses the running OS. */
    @Volatile
    var macOverride: Boolean? = null

    private val isMac: Boolean get() = macOverride ?: SystemInfo.isMac

    fun overrideKey(context: HelixKeyContext, letter: Char): String = "${context.name}:$letter"

    fun defaultOwner(key: HelixCtrlKey, context: HelixKeyContext): HelixKeyOwner = when {
        isMac -> HelixKeyOwner.HELIX
        context == HelixKeyContext.INSERT && key.insertIdeOnWinLinux -> HelixKeyOwner.IDE
        else -> HelixKeyOwner.HELIX
    }

    fun owner(
        key: HelixCtrlKey,
        context: HelixKeyContext,
        overrides: Map<String, String> = HelixSettings.instance.ctrlKeyOverrides,
    ): HelixKeyOwner {
        val stored = overrides[overrideKey(context, key.letter)]
        return HelixKeyOwner.entries.firstOrNull { it.name == stored } ?: defaultOwner(key, context)
    }

    /**
     * Returns `true` when Helix should handle the Ctrl chord with [keyCode] in [context].
     */
    fun isHelixOwned(keyCode: Int, context: HelixKeyContext): Boolean {
        val key = KEYS.firstOrNull { it.keyCode == keyCode && it.supports(context) } ?: return false
        return owner(key, context) == HelixKeyOwner.HELIX
    }

    /** Drops overrides equal to the platform default so persisted state stays minimal. */
    fun normalize(overrides: Map<String, String>): Map<String, String> = overrides.filter { (id, value) ->
        val context = HelixKeyContext.entries.firstOrNull { id.startsWith("${it.name}:") } ?: return@filter false
        val key = KEYS.firstOrNull { overrideKey(context, it.letter) == id } ?: return@filter false
        value != defaultOwner(key, context).name
    }
}
