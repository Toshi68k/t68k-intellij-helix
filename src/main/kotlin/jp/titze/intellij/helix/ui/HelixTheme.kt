// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

@file:Suppress("UseJBColor")

package jp.titze.intellij.helix.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import jp.titze.intellij.helix.settings.HelixColorTheme
import jp.titze.intellij.helix.settings.HelixSettings
import java.awt.Color
import java.util.function.Supplier

class HelixColor(
    val light: Color,
    val dark: Color,
    private val darkNeutral: (() -> Color)? = null,
    private val syncDark: (() -> Color)? = null,
) : JBColor(
    Supplier {
        val theme = if (ApplicationManager.getApplication() != null) {
            HelixSettings.instance.colorTheme
        } else {
            HelixColorTheme.SYNC
        }
        when (theme) {
            HelixColorTheme.DARK -> dark

            HelixColorTheme.DARK_NEUTRAL -> darkNeutral?.invoke() ?: dark

            HelixColorTheme.LIGHT -> light

            HelixColorTheme.SYNC -> {
                if (isBright()) {
                    light
                } else {
                    syncDark?.invoke() ?: dark
                }
            }
        }
    },
)

internal data class DarkPalette(
    val key: Long,
    val cardBg: Color,
    val cardBorder: Color,
    val dividerColor: Color,
    val titleColor: Color,
    val cancelColor: Color,
    val keycapBg: Color,
    val keycapCurrentBg: Color,
    val keycapBorder: Color,
    val keycapFg: Color,
    val keycapCurrentFg: Color,
    val keycapSubmenuBg: Color,
    val keycapSubmenuBorder: Color,
    val keycapSubmenuFg: Color,
    val itemTextColor: Color,
    val itemDescColor: Color,
    val itemPathColor: Color,
    val itemSnippetColor: Color,
    val hoverBg: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val inputFocusBorder: Color,
)

object HelixTheme {
    val isDark: Boolean
        get() = when (HelixSettings.instance.colorTheme) {
            HelixColorTheme.DARK, HelixColorTheme.DARK_NEUTRAL -> true
            HelixColorTheme.LIGHT -> false
            HelixColorTheme.SYNC -> !JBColor.isBright()
        }

    val TRANSPARENT: JBColor = HelixColor(Color(0, 0, 0, 0), Color(0, 0, 0, 0))

    fun color(lightRgb: Int, darkRgb: Int): JBColor = HelixColor(Color(lightRgb), Color(darkRgb))

    fun color(light: Color, dark: Color): JBColor = HelixColor(light, dark)

    private fun syncColor(lightRgb: Int, darkRgb: Int, selectDark: (DarkPalette) -> Color): JBColor = HelixColor(
        light = Color(lightRgb),
        dark = Color(darkRgb),
        darkNeutral = { selectDark(DARK_NEUTRAL_PALETTE) },
        syncDark = { selectDark(currentSyncDarkPalette()) },
    )

    @Volatile
    private var cachedSyncPalette: DarkPalette? = null

    private val DARK_NEUTRAL_PALETTE = DarkPalette(
        key = 0L,
        cardBg = Color(0x26282E),
        cardBorder = Color(0x3E4148),
        dividerColor = Color(0x32353C),
        titleColor = Color(0x8AB4F8),
        cancelColor = Color(0x7A7E85),
        keycapBg = Color(0x30333B),
        keycapCurrentBg = Color(0x2B3B55),
        keycapBorder = Color(0x454954),
        keycapFg = Color(0xCBD5E1),
        keycapCurrentFg = Color(0x93C5FD),
        keycapSubmenuBg = Color(0x2B3648),
        keycapSubmenuBorder = Color(0x43577A),
        keycapSubmenuFg = Color(0x8AB4F8),
        itemTextColor = Color(0xDFE1E5),
        itemDescColor = Color(0x7A7E85),
        itemPathColor = Color(0x9DA2AB),
        itemSnippetColor = Color(0x8B909A),
        hoverBg = Color(0x343740),
        inputBg = Color(0x1E2025),
        inputBorder = Color(0x3E4148),
        inputFocusBorder = Color(0x6B9BFA),
    )

    internal fun currentSyncDarkPalette(): DarkPalette {
        val popupBg = concreteColor(JBUI.CurrentTheme.Popup.BACKGROUND)
        if (!ColorUtil.isDark(popupBg)) {
            return DARK_NEUTRAL_PALETTE
        }

        val editorBg = readEditorDarkBg()
        val labelFg = concreteColor(UIUtil.getLabelForeground())
        val helpFg = concreteColor(UIUtil.getContextHelpForeground())
        val linkFg = concreteColor(JBUI.CurrentTheme.Link.Foreground.ENABLED)
        val ideBorder = concreteColor(JBColor.border())

        val cacheKey = computeCacheKey(popupBg, editorBg, labelFg, helpFg, linkFg, ideBorder)
        val existing = cachedSyncPalette
        if (existing != null && existing.key == cacheKey) {
            return existing
        }

        val palette = deriveDarkPalette(cacheKey, popupBg, editorBg, labelFg, helpFg, linkFg, ideBorder)
        cachedSyncPalette = palette
        return palette
    }

    private fun concreteColor(color: Color): Color = Color(color.rgb, true)

    private fun readEditorDarkBg(): Color? = try {
        if (ApplicationManager.getApplication() == null) {
            null
        } else {
            val bg = EditorColorsManager.getInstance().globalScheme.defaultBackground
            val concrete = concreteColor(bg)
            if (ColorUtil.isDark(concrete)) concrete else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun computeCacheKey(
        popupBg: Color,
        editorBg: Color?,
        labelFg: Color,
        helpFg: Color,
        linkFg: Color,
        ideBorder: Color,
    ): Long {
        var hash = popupBg.rgb.toLong()
        hash = hash * 31L + (editorBg?.rgb?.toLong() ?: 0L)
        hash = hash * 31L + labelFg.rgb.toLong()
        hash = hash * 31L + helpFg.rgb.toLong()
        hash = hash * 31L + linkFg.rgb.toLong()
        hash = hash * 31L + ideBorder.rgb.toLong()
        return hash
    }

    internal fun deriveDarkPalette(
        key: Long,
        popupBg: Color,
        editorBg: Color?,
        labelFg: Color,
        helpFg: Color,
        linkFg: Color,
        ideBorder: Color,
    ): DarkPalette {
        val itemText = if (!ColorUtil.isDark(labelFg)) labelFg else Color(0xDFE1E5)
        val cardBg = deriveCardBg(popupBg, editorBg, itemText)
        val cardBorder = if (ColorUtil.isDark(ideBorder)) {
            ColorUtil.mix(ideBorder, itemText, 0.08)
        } else {
            ColorUtil.mix(cardBg, itemText, 0.16)
        }
        val divider = ColorUtil.mix(cardBg, itemText, 0.08)
        val rawDesc = if (!ColorUtil.isDark(helpFg)) helpFg else ColorUtil.mix(cardBg, itemText, 0.48)
        val itemDesc = ColorUtil.mix(rawDesc, itemText, 0.12)
        val accent = ColorUtil.mix(linkFg, itemText, 0.22)

        return DarkPalette(
            key = key,
            cardBg = cardBg,
            cardBorder = cardBorder,
            dividerColor = divider,
            titleColor = accent,
            cancelColor = itemDesc,
            keycapBg = ColorUtil.mix(cardBg, itemText, 0.07),
            keycapCurrentBg = ColorUtil.mix(cardBg, accent, 0.24),
            keycapBorder = ColorUtil.mix(cardBg, itemText, 0.17),
            keycapFg = ColorUtil.mix(itemText, accent, 0.12),
            keycapCurrentFg = ColorUtil.mix(accent, Color.WHITE, 0.22),
            keycapSubmenuBg = ColorUtil.mix(cardBg, accent, 0.16),
            keycapSubmenuBorder = ColorUtil.mix(cardBg, accent, 0.34),
            keycapSubmenuFg = ColorUtil.mix(accent, itemText, 0.20),
            itemTextColor = itemText,
            itemDescColor = itemDesc,
            itemPathColor = ColorUtil.mix(itemDesc, itemText, 0.45),
            itemSnippetColor = ColorUtil.mix(itemDesc, itemText, 0.25),
            hoverBg = ColorUtil.mix(cardBg, itemText, 0.08),
            inputBg = ColorUtil.mix(cardBg, Color.BLACK, 0.20),
            inputBorder = cardBorder,
            inputFocusBorder = accent,
        )
    }

    private fun deriveCardBg(popupBg: Color, editorBg: Color?, itemText: Color): Color = when {
        editorBg == null -> popupBg
        editorBg.rgb == popupBg.rgb -> ColorUtil.mix(popupBg, itemText, 0.05)
        else -> ColorUtil.mix(editorBg, popupBg, 0.72)
    }

    val CARD_BG = syncColor(0xFAFAFC, 0x151622) { it.cardBg }
    val CARD_BORDER = syncColor(0xD8DCEA, 0x2B2E46) { it.cardBorder }
    val DIVIDER_COLOR = syncColor(0xEAEDF5, 0x23263A) { it.dividerColor }
    val TITLE_COLOR = syncColor(0x4338CA, 0xA5B4FC) { it.titleColor }
    val CANCEL_COLOR = syncColor(0x8A90A2, 0x646C8E) { it.cancelColor }

    val KEYCAP_BG = syncColor(0xEEF2FC, 0x23263E) { it.keycapBg }
    val KEYCAP_CURRENT_BG = syncColor(0xDCE7FE, 0x2D3760) { it.keycapCurrentBg }
    val KEYCAP_BORDER = syncColor(0xCFD7EE, 0x383D62) { it.keycapBorder }
    val KEYCAP_FG = syncColor(0x3B4790, 0xA5B4FC) { it.keycapFg }
    val KEYCAP_CURRENT_FG = syncColor(0x2563EB, 0x60A5FA) { it.keycapCurrentFg }

    val KEYCAP_SUBMENU_BG = syncColor(0xE0E7FF, 0x2D3760) { it.keycapSubmenuBg }
    val KEYCAP_SUBMENU_BORDER = syncColor(0xBAC8F2, 0x4C5488) { it.keycapSubmenuBorder }
    val KEYCAP_SUBMENU_FG = syncColor(0x3730A3, 0xC7D2FE) { it.keycapSubmenuFg }

    val ITEM_TEXT_COLOR = syncColor(0x222430, 0xDFE2EE) { it.itemTextColor }
    val ITEM_DESC_COLOR = syncColor(0x8E94A8, 0x6A7294) { it.itemDescColor }
    val ITEM_PATH_COLOR = syncColor(0x626880, 0x949BB7) { it.itemPathColor }
    val ITEM_SNIPPET_COLOR = syncColor(0x8087A0, 0x717896) { it.itemSnippetColor }

    val HOVER_BG = syncColor(0xF0F3FA, 0x202338) { it.hoverBg }
    val INPUT_BG = syncColor(0xF0F3FA, 0x1B1D2E) { it.inputBg }
    val INPUT_BORDER = syncColor(0xD0D7E8, 0x323754) { it.inputBorder }
    val INPUT_FOCUS_BORDER = syncColor(0x6366F1, 0x818CF8) { it.inputFocusBorder }

    val HINT_FG = syncColor(0x8E94A8, 0x6A7294) { it.itemDescColor }
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
