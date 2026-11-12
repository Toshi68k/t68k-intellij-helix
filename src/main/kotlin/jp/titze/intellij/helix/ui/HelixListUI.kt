// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.ui

import com.intellij.ui.components.JBList
import java.awt.Color
import javax.swing.plaf.basic.BasicListUI

/**
 * Custom ListUI for [JBList] instances within Helix floating card popups
 * (Command Palette, File Picker, Jumplist, Registers, Macros).
 *
 * Background:
 * By default, [JBList.setUI] detects standard [BasicListUI] and replaces it with
 * `com.intellij.ui.components.WideSelectionListUI`. In `WideSelectionListUI.paintCell`,
 * the entire bounding rectangle of any selected row is filled with the active IDE theme's
 * selection background (`RenderingUtil.getSelectionBackground(list)` - which is light gray / white
 * on Light IDE themes) before invoking the cell renderer.
 *
 * When the Helix custom row renderer renders its rounded selection pill ([java.awt.Graphics2D.fillRoundRect]),
 * the sharp 90-degree corners of the underlying `WideSelectionListUI` fill peek out past the 6px rounded
 * corners, creating four unwanted white / light dot artifacts.
 *
 * Subclassing [BasicListUI] bypasses `JBList`'s `WideSelectionListUI` substitution because
 * `ui.javaClass != BasicListUI::class.java`. Standard [BasicListUI] does not paint an opaque
 * rectangular background behind the renderer, allowing the renderer's own smooth rounded selection
 * pill to render cleanly with zero corner artifacts—using 100% public Java SE Swing API and no
 * internal JetBrains APIs.
 */
class HelixListUI : BasicListUI() {

    companion object {
        val TRANSPARENT: Color = Color(0, 0, 0, 0)

        fun install(list: JBList<*>) {
            list.setUI(HelixListUI())
            list.selectionBackground = TRANSPARENT
        }
    }
}
