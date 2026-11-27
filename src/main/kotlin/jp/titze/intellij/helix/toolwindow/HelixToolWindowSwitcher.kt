// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import jp.titze.intellij.helix.HelixBundle
import jp.titze.intellij.helix.settings.HelixSettings
import jp.titze.intellij.helix.state.HelixStateManager
import jp.titze.intellij.helix.ui.HelixWhichKeyPopup
import jp.titze.intellij.helix.ui.WhichKeyItem
import java.awt.event.KeyEvent
import javax.swing.JComponent

/**
 * Handles the `Space` tool window switcher chord (from tool windows, editor `Space+W`, or actions)
 * and the `Ctrl+w` focus Which-Key menu inside eligible tool windows.
 */
object HelixToolWindowSwitcher {

    const val PREFIX = "tw:space"
    const val EDITOR_PREFIX = "tw:editor"
    const val WINDOW_PREFIX = "tw:C-w"

    private const val FOCUS_EDITOR_CMD = "focus_editor"
    private const val FOCUS_EDITOR_ACTION = "FocusEditor"
    private const val FOCUS_EDITOR_LABEL = "Focus editor"

    @Volatile
    internal var switchExecutor: ((actionId: String, toolWindowId: String?, component: JComponent) -> Boolean)? = null

    @Volatile
    private var activeTargets: List<SwitchTarget>? = null

    data class SwitchTarget(
        val key: Char,
        val keyDisplay: String,
        val label: String,
        val helixCommand: String,
        val intelliJAction: String,
        val toolWindowId: String?,
    ) {
        fun toWhichKeyItem(): WhichKeyItem = WhichKeyItem(
            key = keyDisplay,
            label = label,
            helixCommand = helixCommand,
            intelliJAction = intelliJAction,
        )
    }

    private val CORE_TARGETS = listOf(
        SwitchTarget('e', "e", "Project", "toolwindow_project", "ActivateProjectToolWindow", "Project"),
        SwitchTarget(
            'g',
            "g",
            "Version Control",
            "toolwindow_vcs",
            "ActivateVersionControlToolWindow",
            "Version Control",
        ),
        SwitchTarget('c', "c", "Commit", "toolwindow_commit", "ActivateCommitToolWindow", "Commit"),
        SwitchTarget(
            'D',
            "D",
            "Problems View",
            "toolwindow_problems",
            "ActivateProblemsViewToolWindow",
            "Problems View",
        ),
        SwitchTarget('f', "f", "Find", "toolwindow_find", "ActivateFindToolWindow", "Find"),
        SwitchTarget('s', "s", "Structure", "toolwindow_structure", "ActivateStructureToolWindow", "Structure"),
        SwitchTarget('S', "S", "Services", "toolwindow_services", "ActivateServicesToolWindow", "Services"),
        SwitchTarget('r', "r", "Run", "toolwindow_run", "ActivateRunToolWindow", "Run"),
        SwitchTarget('d', "d", "Debug", "toolwindow_debug", "ActivateDebugToolWindow", "Debug"),
        SwitchTarget('b', "b", "Build", "toolwindow_build", "ActivateBuildToolWindow", "Build"),
        SwitchTarget('G', "G", "Gradle", "toolwindow_gradle", "ActivateGradleToolWindow", "Gradle"),
        SwitchTarget('t', "t", "Terminal", "toolwindow_terminal", "ActivateTerminalToolWindow", "Terminal"),
    )

    private val OPTIONAL_KNOWN_TARGETS = listOf(
        SwitchTarget('B', "B", "Database", "toolwindow_database", "ActivateDatabaseToolWindow", "DatabaseView"),
        SwitchTarget('m', "m", "Maven", "toolwindow_maven", "ActivateMavenToolWindow", "Maven"),
        SwitchTarget('M', "M", "Bookmarks", "toolwindow_bookmarks", "ActivateBookmarksToolWindow", "Bookmarks"),
        SwitchTarget('T', "T", "TODO", "toolwindow_todo", "ActivateTODOToolWindow", "TODO"),
        SwitchTarget(
            'p',
            "p",
            "Pull Requests",
            "toolwindow_pull_requests",
            "ActivatePullRequestsToolWindow",
            "Pull Requests",
        ),
        SwitchTarget('h', "h", "Hierarchy", "toolwindow_hierarchy", "ActivateHierarchyToolWindow", "Hierarchy"),
        SwitchTarget(
            'n',
            "n",
            "Notifications",
            "toolwindow_notifications",
            "ActivateNotificationsToolWindow",
            "Notifications",
        ),
    )

    private val EDITOR_TARGETS = listOf(
        SwitchTarget('w', "w", FOCUS_EDITOR_LABEL, FOCUS_EDITOR_CMD, FOCUS_EDITOR_ACTION, null),
        SwitchTarget(' ', "Space", FOCUS_EDITOR_LABEL, FOCUS_EDITOR_CMD, FOCUS_EDITOR_ACTION, null),
    )

    private val DEFAULT_TARGETS: List<SwitchTarget> = CORE_TARGETS + EDITOR_TARGETS
    private val ALL_CURATED_TARGETS: List<SwitchTarget> = CORE_TARGETS + OPTIONAL_KNOWN_TARGETS

    val menuItems: List<WhichKeyItem> = DEFAULT_TARGETS.map { it.toWhichKeyItem() }
    val editorMenuItems: List<WhichKeyItem> = CORE_TARGETS.map { it.toWhichKeyItem() }

    val windowMenuItems: List<WhichKeyItem> = listOf(
        WhichKeyItem("h", "Focus left", "jump_view_left", "PrevSplitter"),
        WhichKeyItem("j", "Focus below", "jump_view_down", "NextSplitter"),
        WhichKeyItem("k", "Focus above", "jump_view_up", "PrevSplitter"),
        WhichKeyItem("l", "Focus right", "jump_view_right", "NextSplitter"),
        WhichKeyItem("w", FOCUS_EDITOR_LABEL, FOCUS_EDITOR_CMD, FOCUS_EDITOR_ACTION),
    )

    fun isEligibleForSpaceSwitcher(
        component: JComponent,
        toolWindowId: String,
        settings: HelixSettings = HelixSettings.instance,
    ): Boolean {
        if (!settings.toolWindowSpaceSwitcher) return false
        if (HelixCommitToolWindowActions.isCommitToolWindow(toolWindowId)) {
            return settings.toolWindowCommitSpaceSwitcher
        }
        return !HelixCommitToolWindowActions.isChangesTreeWithCheckboxes(component)
    }

    fun startSpaceMenu(component: JComponent, toolWindowId: String, includeEditorTargets: Boolean = true): Boolean {
        val targets = resolveTargets(component, includeEditorTargets = includeEditorTargets)
        activeTargets = targets
        if (toolWindowId.isNotEmpty() && toolWindowId != "Terminal") {
            HelixToolWindowState.setPendingSequence(PREFIX)
        }
        HelixWhichKeyPopup.showForComponent(
            anchorComponent = component,
            prefix = PREFIX,
            title = HelixBundle.message("whichKey.menu.toolWindowSpace"),
            items = targets.map { it.toWhichKeyItem() },
            onSelectChar = { ch -> handleSelectionChar(ch, component, toolWindowId, includeEditorTargets) },
            onCancel = {
                clearActiveTargets()
                if (HelixToolWindowState.getPendingSequence() == PREFIX) {
                    HelixToolWindowState.clearPendingSequence()
                }
            },
        )
        return true
    }

    fun startFromEditor(editor: Editor): Boolean {
        if (editor.isDisposed) return false
        val anchor = editor.contentComponent
        val targets = resolveTargets(anchor, projectOverride = editor.project, includeEditorTargets = false)
        activeTargets = targets
        val state = HelixStateManager.getOrCreate(editor)
        state.setPendingSequence(EDITOR_PREFIX)
        HelixWhichKeyPopup.showForComponent(
            anchorComponent = anchor,
            prefix = EDITOR_PREFIX,
            title = HelixBundle.message("whichKey.menu.toolWindowSpace"),
            items = targets.map { it.toWhichKeyItem() },
            onSelectChar = { ch -> handleEditorSelectionChar(ch, editor) },
            onCancel = {
                clearActiveTargets()
                if (state.pendingSequence == EDITOR_PREFIX) {
                    state.clearPendingSequence()
                }
            },
        )
        return true
    }

    fun showWindowMenu(component: JComponent, toolWindowId: String) {
        HelixWhichKeyPopup.showForComponent(
            anchorComponent = component,
            prefix = WINDOW_PREFIX,
            title = HelixBundle.message("whichKey.menu.window"),
            items = windowMenuItems,
            onSelectChar = { ch -> handleWindowSelectionChar(ch, component, toolWindowId) },
            onCancel = {
                if (HelixToolWindowState.getPendingSequence() == "C-w") {
                    HelixToolWindowState.clearPendingSequence()
                }
            },
        )
    }

    fun clearActiveTargets() {
        activeTargets = null
    }

    fun handlePendingKey(
        keyCode: Int,
        effectiveChar: Char,
        isShift: Boolean,
        component: JComponent,
        currentToolWindowId: String,
    ): Boolean {
        HelixToolWindowState.clearPendingSequence()
        HelixWhichKeyPopup.hide()

        val resolvedChar = resolveTargetChar(keyCode, effectiveChar, isShift)
        if (resolvedChar != null) {
            handleSelectionChar(resolvedChar, component, currentToolWindowId)
        } else {
            clearActiveTargets()
            component.requestFocusInWindow()
        }
        return true
    }

    fun handleSelectionChar(
        ch: Char,
        component: JComponent,
        currentToolWindowId: String,
        includeEditorTargets: Boolean = true,
    ): Boolean {
        if (HelixToolWindowState.getPendingSequence() == PREFIX) {
            HelixToolWindowState.clearPendingSequence()
        }
        val targets = activeTargets ?: resolveTargets(component, includeEditorTargets = includeEditorTargets)
        clearActiveTargets()

        val target = targets.firstOrNull { it.key == ch }
        if (target == null) {
            component.requestFocusInWindow()
            return false
        }

        return activateTarget(target, component, currentToolWindowId)
    }

    fun handleEditorSelectionChar(ch: Char, editor: Editor): Boolean {
        val state = HelixStateManager.getOrCreate(editor)
        if (state.pendingSequence == EDITOR_PREFIX) {
            state.clearPendingSequence()
        }
        val anchor = editor.contentComponent
        val targets = activeTargets ?: resolveTargets(
            anchor,
            projectOverride = editor.project,
            includeEditorTargets = false,
        )
        clearActiveTargets()

        val target = targets.firstOrNull { it.key == ch }
        if (target == null) {
            if (!editor.isDisposed) {
                anchor.requestFocusInWindow()
            }
            return false
        }

        return activateTarget(target, anchor, currentToolWindowId = "", projectOverride = editor.project)
    }

    private fun handleWindowSelectionChar(ch: Char, component: JComponent, currentToolWindowId: String): Boolean {
        if (HelixToolWindowState.getPendingSequence() == "C-w") {
            HelixToolWindowState.clearPendingSequence()
        }
        return when (ch) {
            'h' -> HelixWindowFocusNavigator.jumpFromToolWindow(FocusDirection.LEFT, component, currentToolWindowId)
            'j' -> HelixWindowFocusNavigator.jumpFromToolWindow(FocusDirection.DOWN, component, currentToolWindowId)
            'k' -> HelixWindowFocusNavigator.jumpFromToolWindow(FocusDirection.UP, component, currentToolWindowId)
            'l' -> HelixWindowFocusNavigator.jumpFromToolWindow(FocusDirection.RIGHT, component, currentToolWindowId)
            'w' -> HelixWindowFocusNavigator.returnToEditorFromToolWindow(component)
            else -> true
        }
    }

    internal fun resolveTargets(
        component: JComponent,
        projectOverride: Project? = null,
        includeEditorTargets: Boolean = true,
    ): List<SwitchTarget> {
        val project = projectOverride?.takeIf { !it.isDisposed }
            ?: HelixToolWindowGuard.findProjectForComponent(component)
        if (project == null || project.isDisposed) {
            return if (includeEditorTargets) DEFAULT_TARGETS else CORE_TARGETS
        }

        val twManager = ToolWindowManager.getInstance(project)
        val availableWindows = twManager.toolWindowIds.mapNotNull { id ->
            val tw = twManager.getToolWindow(id)
            if (tw != null && (tw.isAvailable || tw.isVisible)) {
                val title = try {
                    tw.stripeTitle.ifBlank { id }
                } catch (_: Throwable) {
                    id
                }
                id to title
            } else {
                null
            }
        }
        return buildTargetsFromAvailable(availableWindows, includeEditorTargets)
    }

    internal fun buildTargetsFromAvailable(
        availableWindows: List<Pair<String, String>>,
        includeEditorTargets: Boolean = true,
    ): List<SwitchTarget> {
        if (availableWindows.isEmpty()) {
            return if (includeEditorTargets) DEFAULT_TARGETS else CORE_TARGETS
        }

        val availableMap = availableWindows.toMap(linkedMapOf())
        val resolved = mutableListOf<SwitchTarget>()
        val usedKeys = mutableSetOf('w', ' ')
        val coveredIds = mutableSetOf<String>()

        for (target in ALL_CURATED_TARGETS) {
            val id = target.toolWindowId ?: continue
            if (availableMap.containsKey(id) && usedKeys.add(target.key)) {
                resolved.add(target)
                coveredIds.add(id)
            }
        }

        for ((id, title) in availableMap) {
            if (id !in coveredIds) {
                val key = pickDynamicKey(title, id, usedKeys)
                if (key != null) {
                    usedKeys.add(key)
                    val slug = id.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    val actionId = "Activate${id.replace(Regex("[^A-Za-z0-9]"), "")}ToolWindow"
                    resolved.add(SwitchTarget(key, key.toString(), title, "toolwindow_$slug", actionId, id))
                }
            }
        }

        return if (includeEditorTargets) resolved + EDITOR_TARGETS else resolved
    }

    private fun pickDynamicKey(title: String, id: String, usedKeys: Set<Char>): Char? {
        val words = "$title $id".split(Regex("[\\s_-]+")).filter { it.isNotEmpty() }
        val candidates = linkedSetOf<Char>()
        for (word in words) {
            val first = word.first()
            if (first.isLetter()) {
                candidates.add(first.lowercaseChar())
                candidates.add(first.uppercaseChar())
            }
        }
        for (ch in title) {
            if (ch.isLetter()) {
                candidates.add(ch.lowercaseChar())
                candidates.add(ch.uppercaseChar())
            }
        }
        for (digit in '1'..'9') {
            candidates.add(digit)
        }
        for (ch in 'a'..'z') {
            candidates.add(ch)
        }
        return candidates.firstOrNull { it !in usedKeys }
    }

    private fun resolveTargetChar(keyCode: Int, effectiveChar: Char, isShift: Boolean): Char? {
        if (!isShift && keyCode == KeyEvent.VK_SPACE) return ' '
        if (effectiveChar != KeyEvent.CHAR_UNDEFINED) return effectiveChar
        return HelixToolWindowState.keyCharFromCode(keyCode, isShift)
    }

    private fun activateTarget(
        target: SwitchTarget,
        component: JComponent,
        currentToolWindowId: String,
        projectOverride: Project? = null,
    ): Boolean {
        switchExecutor?.let { return it(target.intelliJAction, target.toolWindowId, component) }

        val project = projectOverride?.takeIf { !it.isDisposed }
            ?: HelixToolWindowGuard.findProjectForComponent(component)
        if (target.toolWindowId == null) {
            if (project != null && !project.isDisposed) {
                ToolWindowManager.getInstance(project).activateEditorComponent()
            }
            return true
        }

        if (target.toolWindowId == currentToolWindowId) {
            component.requestFocusInWindow()
            return true
        }

        if (project != null && !project.isDisposed) {
            val tw = ToolWindowManager.getInstance(project).getToolWindow(target.toolWindowId)
            if (tw != null) {
                tw.activate(null, true)
                return true
            }
        }

        return HelixCommitToolWindowActions.executeToolWindowAction(target.intelliJAction, component)
    }
}
