// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

@file:Suppress("UseJBColor")

package jp.titze.intellij.helix.ui

import com.intellij.ui.JBColor
import jp.titze.intellij.helix.settings.HelixColorTheme
import jp.titze.intellij.helix.settings.HelixSettings
import java.awt.Color
import java.util.function.Supplier

class HelixColor(val light: Color, val dark: Color) :
    JBColor(
        Supplier { if (HelixTheme.isDark) dark else light },
    )

object HelixTheme {
    val isDark: Boolean
        get() = when (HelixSettings.instance.colorTheme) {
            HelixColorTheme.DARK -> true
            HelixColorTheme.LIGHT -> false
            HelixColorTheme.SYNC -> !JBColor.isBright()
        }

    val TRANSPARENT: JBColor = HelixColor(Color(0, 0, 0, 0), Color(0, 0, 0, 0))

    fun color(lightRgb: Int, darkRgb: Int): JBColor = HelixColor(Color(lightRgb), Color(darkRgb))

    fun color(light: Color, dark: Color): JBColor = HelixColor(light, dark)

    val CARD_BG = color(0xFAFAFC, 0x151622)
    val CARD_BORDER = color(0xD8DCEA, 0x2B2E46)
    val DIVIDER_COLOR = color(0xEAEDF5, 0x23263A)
    val TITLE_COLOR = color(0x4338CA, 0xA5B4FC)
    val CANCEL_COLOR = color(0x8A90A2, 0x646C8E)

    val KEYCAP_BG = color(0xEEF2FC, 0x23263E)
    val KEYCAP_CURRENT_BG = color(0xDCE7FE, 0x2D3760)
    val KEYCAP_BORDER = color(0xCFD7EE, 0x383D62)
    val KEYCAP_FG = color(0x3B4790, 0xA5B4FC)
    val KEYCAP_CURRENT_FG = color(0x2563EB, 0x60A5FA)

    val ITEM_TEXT_COLOR = color(0x222430, 0xDFE2EE)
    val ITEM_DESC_COLOR = color(0x8E94A8, 0x6A7294)
    val ITEM_PATH_COLOR = color(0x626880, 0x949BB7)
    val ITEM_SNIPPET_COLOR = color(0x8087A0, 0x717896)

    val HOVER_BG = color(0xF0F3FA, 0x202338)
    val INPUT_BG = color(0xF0F3FA, 0x1B1D2E)
    val INPUT_BORDER = color(0xD0D7E8, 0x323754)
    val INPUT_FOCUS_BORDER = color(0x6366F1, 0x818CF8)

    val HINT_FG = color(0x8E94A8, 0x6A7294)
    val MATCH_SUCCESS_FG = color(0x15803D, 0x34D399)
    val MATCH_WARN_FG = color(0xB91C1C, 0xF87171)

    val YANK_FLASH_BG = color(Color(0x81, 0x8C, 0xF8, 0x80), Color(0x63, 0x66, 0xF1, 0x90))

    // ScrollBar Thumb Colors
    val SCROLLBAR_THUMB_DEFAULT = color(Color(0, 0, 0, 90), Color(255, 255, 255, 75))
    val SCROLLBAR_THUMB_ROLLOVER = color(Color(0, 0, 0, 140), Color(255, 255, 255, 130))
    val SCROLLBAR_THUMB_DRAGGING = color(Color(0, 0, 0, 180), Color(255, 255, 255, 160))

    // Mode Pill & Cursor Colors (color-modes = true)
    val MODE_NOR_BG = color(0x4338CA, 0x6366F1)
    val MODE_NOR_FG = color(0xFFFFFF, 0xFFFFFF)
    val MODE_NOR_CARET = color(0x4338CA, 0x818CF8)

    val MODE_INS_BG = color(0x059669, 0x10B981)
    val MODE_INS_FG = color(0xFFFFFF, 0xFFFFFF)
    val MODE_INS_CARET = color(0x059669, 0x34D399)

    val MODE_SEL_BG = color(0x7C3AED, 0x8B5CF6)
    val MODE_SEL_FG = color(0xFFFFFF, 0xFFFFFF)
    val MODE_SEL_CARET = color(0x7C3AED, 0xA78BFA)
}
