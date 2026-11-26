// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.toolwindow

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import jp.titze.intellij.helix.action.HelixActionDelegate
import jp.titze.intellij.helix.settings.HelixSettings
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.SwingUtilities
import kotlin.math.abs

enum class FocusDirection {
    LEFT,
    DOWN,
    UP,
    RIGHT,
    ;

    fun toAnchor(): ToolWindowAnchor = when (this) {
        LEFT -> ToolWindowAnchor.LEFT
        RIGHT -> ToolWindowAnchor.RIGHT
        DOWN -> ToolWindowAnchor.BOTTOM
        UP -> ToolWindowAnchor.TOP
    }

    fun fallbackSplitterAction(): String = when (this) {
        LEFT, UP -> "PrevSplitter"
        RIGHT, DOWN -> "NextSplitter"
    }
}

internal enum class PaneKind {
    EDITOR,
    TOOL_WINDOW,
}

internal data class SpatialPane(val kind: PaneKind, val bounds: Rectangle)

/**
 * Resolves 2D spatial focus navigation across editor splits and docked tool windows.
 */
object HelixWindowFocusNavigator {

    private const val ADJACENT_BORDER_SLACK_PX = 64

    @Volatile
    internal var toolWindowJumpHook: ((direction: FocusDirection, targetId: String) -> Boolean)? = null

    private sealed interface JumpTarget {
        val pane: SpatialPane

        data class EditorSplit(val window: EditorWindow, override val pane: SpatialPane) : JumpTarget

        data class EditorArea(override val pane: SpatialPane) : JumpTarget

        data class ToolWindowPane(val toolWindow: ToolWindow, override val pane: SpatialPane) : JumpTarget
    }

    fun jumpFromEditor(direction: FocusDirection, editor: Editor): Boolean {
        if (HelixActionDelegate.actionExecutor != null) {
            return HelixActionDelegate.executeAction(direction.fallbackSplitterAction(), editor)
        }

        val project = editor.project
        if (project == null || project.isDisposed) {
            return HelixActionDelegate.executeAction(direction.fallbackSplitterAction(), editor)
        }

        if (focusSpatialEditorNeighbor(project, editor, direction)) {
            return true
        }

        if (shouldCrossEditorBoundary()) {
            val currentRect = getEditorOrWindowBounds(project, editor)
            val targetTw = findBoundaryToolWindow(project, direction, currentRect)
            if (targetTw != null) {
                if (toolWindowJumpHook?.invoke(direction, targetTw.id) == true) {
                    return true
                }
                targetTw.activate(null, true)
                return true
            }
        }

        return HelixActionDelegate.executeAction(direction.fallbackSplitterAction(), editor)
    }

    fun jumpFromToolWindow(direction: FocusDirection, component: JComponent, toolWindowId: String): Boolean {
        if (!HelixSettings.instance.toolWindowSeamlessFocusJump) return false

        val hook = toolWindowJumpHook
        if (hook != null && hook(direction, toolWindowId)) {
            HelixToolWindowState.reset()
            return true
        }

        val project = HelixToolWindowGuard.findProjectForComponent(component) ?: return false
        if (project.isDisposed) return false

        val twManager = ToolWindowManager.getInstance(project)
        val currentTw = twManager.getToolWindow(toolWindowId)
        val currentRect = currentTw?.let { getToolWindowBounds(it) } ?: getComponentScreenBounds(component)
        val anchor = currentTw?.anchor

        if (currentRect != null) {
            val candidates = collectJumpTargetsFromToolWindow(project, twManager, toolWindowId)
            val bestIdx = findBestPaneIndex(
                current = SpatialPane(PaneKind.TOOL_WINDOW, currentRect),
                candidates = candidates.map { it.pane },
                direction = direction,
                currentAnchor = anchor,
            )
            if (bestIdx != null) {
                activateJumpTarget(project, twManager, candidates[bestIdx])
                HelixToolWindowState.reset()
                return true
            }
        }

        if (anchor != null && direction == anchorDirectionTowardEditor(anchor)) {
            focusEditorFromToolWindow(project, twManager, currentRect, direction)
            HelixToolWindowState.reset()
            return true
        }

        HelixToolWindowState.clearPendingSequence()
        return true
    }

    fun returnToEditorFromToolWindow(component: JComponent): Boolean {
        if (!HelixSettings.instance.toolWindowSeamlessFocusJump) return false

        val project = HelixToolWindowGuard.findProjectForComponent(component) ?: return false
        if (project.isDisposed) return false

        ToolWindowManager.getInstance(project).activateEditorComponent()
        HelixToolWindowState.reset()
        return true
    }

    private fun shouldCrossEditorBoundary(): Boolean {
        val settings = HelixSettings.instance
        return settings.toolWindowNavigationEnabled && settings.toolWindowSeamlessFocusJump
    }

    private fun focusSpatialEditorNeighbor(project: Project, editor: Editor, direction: FocusDirection): Boolean {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val windows = manager.windows
        if (windows.size <= 1) return false

        val currentWindow = resolveCurrentEditorWindow(manager, editor) ?: return false
        val currentRect = getEditorWindowBounds(currentWindow)
            ?: getComponentScreenBounds(editor.component)
            ?: return false

        val candidates = windows.mapNotNull { win ->
            if (win === currentWindow) return@mapNotNull null
            val rect = getEditorWindowBounds(win) ?: return@mapNotNull null
            win to rect
        }
        val bestIdx = findBestNeighborIndex(currentRect, candidates.map { it.second }, direction) ?: return false
        val targetWindow = candidates[bestIdx].first
        manager.currentWindow = targetWindow
        targetWindow.requestFocus(true)
        return true
    }

    private fun focusEditorFromToolWindow(
        project: Project,
        twManager: ToolWindowManager,
        toolWindowRect: Rectangle?,
        direction: FocusDirection,
    ) {
        if (toolWindowRect != null) {
            val manager = FileEditorManagerEx.getInstanceEx(project)
            val candidates = manager.windows.mapNotNull { win ->
                val rect = getEditorWindowBounds(win) ?: return@mapNotNull null
                win to rect
            }
            val bestIdx = findBestNeighborIndex(toolWindowRect, candidates.map { it.second }, direction)
            if (bestIdx != null) {
                val targetWindow = candidates[bestIdx].first
                manager.currentWindow = targetWindow
                targetWindow.requestFocus(true)
                return
            }
        }
        twManager.activateEditorComponent()
    }

    private fun collectJumpTargetsFromToolWindow(
        project: Project,
        twManager: ToolWindowManager,
        currentToolWindowId: String,
    ): List<JumpTarget> {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val editorSplits = manager.windows.mapNotNull { win ->
            val rect = getEditorWindowBounds(win) ?: return@mapNotNull null
            JumpTarget.EditorSplit(win, SpatialPane(PaneKind.EDITOR, rect))
        }
        val editorTargets: List<JumpTarget> = editorSplits.ifEmpty {
            val areaRect = manager.component?.let { getComponentScreenBounds(it) }
            if (areaRect != null) {
                listOf(JumpTarget.EditorArea(SpatialPane(PaneKind.EDITOR, areaRect)))
            } else {
                emptyList()
            }
        }
        val toolWindowTargets = twManager.toolWindowIds.mapNotNull { id ->
            val tw = if (id == currentToolWindowId) null else twManager.getToolWindow(id)
            val rect = if (tw != null && isToolWindowJumpTargetEligible(tw)) getToolWindowBounds(tw) else null
            if (tw != null && rect != null) {
                JumpTarget.ToolWindowPane(tw, SpatialPane(PaneKind.TOOL_WINDOW, rect))
            } else {
                null
            }
        }
        return editorTargets + toolWindowTargets
    }

    private fun activateJumpTarget(project: Project, twManager: ToolWindowManager, target: JumpTarget) {
        when (target) {
            is JumpTarget.EditorSplit -> {
                val manager = FileEditorManagerEx.getInstanceEx(project)
                manager.currentWindow = target.window
                target.window.requestFocus(true)
            }

            is JumpTarget.EditorArea -> {
                twManager.activateEditorComponent()
            }

            is JumpTarget.ToolWindowPane -> {
                target.toolWindow.activate(null, true)
            }
        }
    }

    private fun findBoundaryToolWindow(
        project: Project,
        direction: FocusDirection,
        currentEditorRect: Rectangle?,
    ): ToolWindow? {
        val twManager = ToolWindowManager.getInstance(project)
        val targetAnchor = direction.toAnchor()
        val matching = twManager.toolWindowIds.mapNotNull { id ->
            val tw = twManager.getToolWindow(id) ?: return@mapNotNull null
            if (isEdgeToolWindowEligible(tw, targetAnchor)) tw else null
        }
        if (matching.isEmpty()) return null
        if (matching.size == 1) return matching.first()

        if (currentEditorRect != null) {
            val withBounds = matching.mapNotNull { tw ->
                val rect = getToolWindowBounds(tw) ?: return@mapNotNull null
                tw to rect
            }
            val idx = findBestNeighborIndex(currentEditorRect, withBounds.map { it.second }, direction)
            if (idx != null) return withBounds[idx].first
        }

        val activeId = twManager.activeToolWindowId
        val preferredTargets = HelixSettings.instance.toolWindowEnabledTargets
        return matching.firstOrNull { it.id == activeId }
            ?: matching.firstOrNull { it.id in preferredTargets }
            ?: matching.first()
    }

    private fun isEdgeToolWindowEligible(tw: ToolWindow, anchor: ToolWindowAnchor): Boolean =
        tw.anchor == anchor && isToolWindowJumpTargetEligible(tw)

    private fun isToolWindowJumpTargetEligible(tw: ToolWindow): Boolean {
        if (!isDockedOrSlidingVisible(tw)) return false
        val rootComponent = resolveSelectedToolWindowComponent(tw)
        return isToolWindowTargetEligible(tw.id, rootComponent)
    }

    internal fun isToolWindowTargetEligible(
        toolWindowId: String,
        rootComponent: Component?,
        settings: HelixSettings = HelixSettings.instance,
    ): Boolean {
        if (toolWindowId == "Terminal") return false
        val isAllowed = settings.toolWindowEnabledTargets.contains(toolWindowId) || settings.toolWindowOtherEnabled
        if (!isAllowed) return false
        return rootComponent != null && hasVisibleNavigableWidget(rootComponent)
    }

    private fun resolveSelectedToolWindowComponent(tw: ToolWindow): Component? {
        val selectedContentComp = try {
            tw.contentManagerIfCreated?.selectedContent?.component
        } catch (_: Throwable) {
            null
        }
        if (selectedContentComp != null) return selectedContentComp
        return try {
            tw.component
        } catch (_: Throwable) {
            null
        }
    }

    private fun hasVisibleNavigableWidget(root: Component): Boolean {
        val queue = ArrayDeque<Component>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.isVisible) {
                if (isNavigableTreeTableOrList(current)) return true
                enqueueChildren(current, queue)
            }
        }
        return false
    }

    private fun isNavigableTreeTableOrList(component: Component): Boolean =
        component is JTree || component is JTable || component is JList<*>

    private fun enqueueChildren(component: Component, queue: ArrayDeque<Component>) {
        if (component is Container) {
            for (i in 0 until component.componentCount) {
                queue.addLast(component.getComponent(i))
            }
        }
    }

    private fun isDockedOrSlidingVisible(tw: ToolWindow): Boolean =
        tw.isVisible && (tw.type == ToolWindowType.DOCKED || tw.type == ToolWindowType.SLIDING)

    private fun anchorDirectionTowardEditor(anchor: ToolWindowAnchor): FocusDirection = when (anchor) {
        ToolWindowAnchor.LEFT -> FocusDirection.RIGHT
        ToolWindowAnchor.RIGHT -> FocusDirection.LEFT
        ToolWindowAnchor.BOTTOM -> FocusDirection.UP
        ToolWindowAnchor.TOP -> FocusDirection.DOWN
        else -> FocusDirection.RIGHT
    }

    private fun resolveCurrentEditorWindow(manager: FileEditorManagerEx, editor: Editor): EditorWindow? {
        val editorComp = editor.component
        for (window in manager.windows) {
            val selectedComp = window.selectedComposite?.selectedEditor?.component ?: continue
            if (selectedComp == editorComp || SwingUtilities.isDescendingFrom(editorComp, selectedComp)) {
                return window
            }
        }
        return manager.currentWindow
    }

    private fun getEditorOrWindowBounds(project: Project, editor: Editor): Rectangle? {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val currentWin = resolveCurrentEditorWindow(manager, editor)
        return currentWin?.let { getEditorWindowBounds(it) } ?: getComponentScreenBounds(editor.component)
    }

    private fun getEditorWindowBounds(window: EditorWindow): Rectangle? {
        val tabbedComp = try {
            window.tabbedPane.component
        } catch (_: Throwable) {
            null
        }
        val tabbedBounds = tabbedComp?.let { getComponentScreenBounds(it) }
        if (tabbedBounds != null) return tabbedBounds

        val comp = window.selectedComposite?.selectedEditor?.component
            ?: window.selectedComposite?.preferredFocusedComponent
            ?: return null
        return getComponentScreenBounds(comp)
    }

    private fun getToolWindowBounds(tw: ToolWindow): Rectangle? {
        val comp = try {
            tw.component
        } catch (_: Throwable) {
            null
        } ?: return null
        return getComponentScreenBounds(comp)
    }

    private fun getComponentScreenBounds(component: Component): Rectangle? {
        if (!component.isShowing || component.width <= 0 || component.height <= 0) return null
        return try {
            val loc = Point(0, 0)
            SwingUtilities.convertPointToScreen(loc, component)
            Rectangle(loc.x, loc.y, component.width, component.height)
        } catch (_: Throwable) {
            null
        }
    }

    private data class NeighborScore(
        val index: Int,
        val overlapsPerp: Boolean,
        val effectiveGap: Int,
        val preferEditor: Boolean,
        val perpCenterDist: Double,
        val primaryCenterDist: Double,
    ) : Comparable<NeighborScore> {
        override fun compareTo(other: NeighborScore): Int {
            if (overlapsPerp != other.overlapsPerp) {
                return if (overlapsPerp) -1 else 1
            }
            if (effectiveGap != other.effectiveGap) {
                return effectiveGap.compareTo(other.effectiveGap)
            }
            if (preferEditor != other.preferEditor) {
                return if (preferEditor) -1 else 1
            }
            val perpCmp = perpCenterDist.compareTo(other.perpCenterDist)
            if (perpCmp != 0) return perpCmp
            return primaryCenterDist.compareTo(other.primaryCenterDist)
        }
    }

    internal fun findBestNeighborIndex(
        current: Rectangle,
        candidates: List<Rectangle>,
        direction: FocusDirection,
    ): Int? = findBestPaneIndex(
        current = SpatialPane(PaneKind.EDITOR, current),
        candidates = candidates.map { SpatialPane(PaneKind.EDITOR, it) },
        direction = direction,
        currentAnchor = null,
    )

    internal fun findBestPaneIndex(
        current: SpatialPane,
        candidates: List<SpatialPane>,
        direction: FocusDirection,
        currentAnchor: ToolWindowAnchor? = null,
    ): Int? {
        val scores = candidates.mapIndexedNotNull { idx, cand ->
            scorePaneCandidate(idx, current, cand, direction, currentAnchor)
        }
        return scores.minOrNull()?.index
    }

    private fun scorePaneCandidate(
        index: Int,
        current: SpatialPane,
        cand: SpatialPane,
        direction: FocusDirection,
        currentAnchor: ToolWindowAnchor?,
    ): NeighborScore? {
        if (!hasValidBounds(cand.bounds) || cand.bounds == current.bounds) return null
        if (!isCandidateInDirection(current.bounds, cand.bounds, direction)) return null

        val overlapsPerp = hasPerpendicularOverlap(current.bounds, cand.bounds, direction)
        val rawGap = computePrimaryGap(current.bounds, cand.bounds, direction)
        val towardEditor = currentAnchor != null && direction == anchorDirectionTowardEditor(currentAnchor)
        val isAdjacentBand = towardEditor && overlapsPerp && rawGap <= ADJACENT_BORDER_SLACK_PX
        val effectiveGap = if (isAdjacentBand) 0 else rawGap
        val preferEditor = isAdjacentBand && cand.kind == PaneKind.EDITOR
        val perpCenterDist = computePerpCenterDistance(current.bounds, cand.bounds, direction)
        val primaryCenterDist = computePrimaryCenterDistance(current.bounds, cand.bounds, direction)
        return NeighborScore(index, overlapsPerp, effectiveGap, preferEditor, perpCenterDist, primaryCenterDist)
    }

    private fun hasValidBounds(bounds: Rectangle): Boolean = bounds.width > 0 && bounds.height > 0

    private fun isCandidateInDirection(current: Rectangle, cand: Rectangle, direction: FocusDirection): Boolean {
        val curCenterX = current.x + current.width / 2.0
        val curCenterY = current.y + current.height / 2.0
        val candCenterX = cand.x + cand.width / 2.0
        val candCenterY = cand.y + cand.height / 2.0
        return when (direction) {
            FocusDirection.LEFT -> candCenterX < curCenterX - 1.0 && cand.x < current.x

            FocusDirection.RIGHT ->
                candCenterX > curCenterX + 1.0 &&
                    (cand.x + cand.width) > (current.x + current.width)

            FocusDirection.UP -> candCenterY < curCenterY - 1.0 && cand.y < current.y

            FocusDirection.DOWN ->
                candCenterY > curCenterY + 1.0 &&
                    (cand.y + cand.height) > (current.y + current.height)
        }
    }

    private fun hasPerpendicularOverlap(current: Rectangle, cand: Rectangle, direction: FocusDirection): Boolean =
        when (direction) {
            FocusDirection.LEFT, FocusDirection.RIGHT -> {
                minOf(current.y + current.height, cand.y + cand.height) - maxOf(current.y, cand.y) > 0
            }

            FocusDirection.UP, FocusDirection.DOWN -> {
                minOf(current.x + current.width, cand.x + cand.width) - maxOf(current.x, cand.x) > 0
            }
        }

    private fun computePrimaryGap(current: Rectangle, cand: Rectangle, direction: FocusDirection): Int =
        when (direction) {
            FocusDirection.LEFT -> maxOf(0, current.x - (cand.x + cand.width))
            FocusDirection.RIGHT -> maxOf(0, cand.x - (current.x + current.width))
            FocusDirection.UP -> maxOf(0, current.y - (cand.y + cand.height))
            FocusDirection.DOWN -> maxOf(0, cand.y - (current.y + current.height))
        }

    private fun computePerpCenterDistance(current: Rectangle, cand: Rectangle, direction: FocusDirection): Double =
        when (direction) {
            FocusDirection.LEFT, FocusDirection.RIGHT -> {
                abs((cand.y + cand.height / 2.0) - (current.y + current.height / 2.0))
            }

            FocusDirection.UP, FocusDirection.DOWN -> {
                abs((cand.x + cand.width / 2.0) - (current.x + current.width / 2.0))
            }
        }

    private fun computePrimaryCenterDistance(current: Rectangle, cand: Rectangle, direction: FocusDirection): Double =
        when (direction) {
            FocusDirection.LEFT, FocusDirection.RIGHT -> {
                abs((cand.x + cand.width / 2.0) - (current.x + current.width / 2.0))
            }

            FocusDirection.UP, FocusDirection.DOWN -> {
                abs((cand.y + cand.height / 2.0) - (current.y + current.height / 2.0))
            }
        }
}
