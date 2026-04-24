package org.open.file.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A full palette — one of these is applied to [AppColors] at a time. The
 * object is deliberately flat (no inheritance) so the Theme dialog can
 * render it as a grid of swatches without reflection.
 *
 * Users can layer a custom [AppColors.accent] on top of any base preset
 * via [applyAccent]; the rest of the palette stays stable so foreground
 * readability doesn't collapse just because they picked a funky accent.
 */
data class AppPalette(
    val name: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val borderLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accentLight: Color,
) {
    fun apply() {
        AppColors.background = background
        AppColors.surface = surface
        AppColors.surfaceVariant = surfaceVariant
        AppColors.border = border
        AppColors.borderLight = borderLight
        AppColors.textPrimary = textPrimary
        AppColors.textSecondary = textSecondary
        AppColors.textMuted = textMuted
        AppColors.textDim = textDim
        AppColors.textFaint = textFaint
        applyAccent(accent, accentLight)
    }
}

/**
 * Swap just the accent pair, leaving the rest of the palette alone.
 * Derived alpha shades (`accentBg`, `accentBorder`) are recomputed so
 * surfaces that key off them stay on-brand.
 */
fun applyAccent(accent: Color, accentLight: Color) {
    AppColors.accent = accent
    AppColors.accentLight = accentLight
    AppColors.accentBg = accent.copy(alpha = 0.10f)
    AppColors.accentBorder = accent.copy(alpha = 0.27f)
}

// ──────────────────────────────────────────────
// Built-in palettes
// ──────────────────────────────────────────────

/** Original dark purple — the app's default. */
val PaletteMidnight = AppPalette(
    name = "Midnight",
    background = Color(0xFF0C0E14),
    surface = Color(0xFF10121A),
    surfaceVariant = Color(0xFF151823),
    border = Color.White.copy(alpha = 0.05f),
    borderLight = Color.White.copy(alpha = 0.08f),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFFE2E8F0),
    textMuted = Color(0xFF8892A4),
    textDim = Color(0xFF555D6E),
    textFaint = Color(0xFF3F4553),
    accent = Color(0xFF7C3AED),
    accentLight = Color(0xFFC4B5FD),
)

/** Warmer dark palette — slightly brown-tinted surfaces, amber accent. */
val PaletteEmber = AppPalette(
    name = "Ember",
    background = Color(0xFF12100F),
    surface = Color(0xFF1A1615),
    surfaceVariant = Color(0xFF221C1A),
    border = Color.White.copy(alpha = 0.05f),
    borderLight = Color.White.copy(alpha = 0.08f),
    textPrimary = Color(0xFFF5F0EC),
    textSecondary = Color(0xFFE7DED6),
    textMuted = Color(0xFFA08E85),
    textDim = Color(0xFF6A5C55),
    textFaint = Color(0xFF4A4039),
    accent = Color(0xFFF59E0B),
    accentLight = Color(0xFFFBBF24),
)

/** Cool slate / cyan — feels like a terminal emulator theme. */
val PaletteOcean = AppPalette(
    name = "Ocean",
    background = Color(0xFF0B1220),
    surface = Color(0xFF0F1A2E),
    surfaceVariant = Color(0xFF152538),
    border = Color.White.copy(alpha = 0.05f),
    borderLight = Color.White.copy(alpha = 0.08f),
    textPrimary = Color(0xFFE8F1FA),
    textSecondary = Color(0xFFCFE0F0),
    textMuted = Color(0xFF7B99B8),
    textDim = Color(0xFF4E6C8A),
    textFaint = Color(0xFF2F4663),
    accent = Color(0xFF22D3EE),
    accentLight = Color(0xFF67E8F9),
)

/** Forest green over near-black. */
val PaletteForest = AppPalette(
    name = "Forest",
    background = Color(0xFF0B1410),
    surface = Color(0xFF0F1A15),
    surfaceVariant = Color(0xFF15241E),
    border = Color.White.copy(alpha = 0.05f),
    borderLight = Color.White.copy(alpha = 0.08f),
    textPrimary = Color(0xFFEAF5EF),
    textSecondary = Color(0xFFCDE6D9),
    textMuted = Color(0xFF7FA898),
    textDim = Color(0xFF4E7666),
    textFaint = Color(0xFF2F4A3F),
    accent = Color(0xFF10B981),
    accentLight = Color(0xFF6EE7B7),
)

/** Light — much brighter surfaces; still usable, not a Material-default knock-off. */
val PaletteParchment = AppPalette(
    name = "Parchment",
    background = Color(0xFFF6F3EC),
    surface = Color(0xFFFBF8F1),
    surfaceVariant = Color(0xFFEFEADB),
    border = Color.Black.copy(alpha = 0.07f),
    borderLight = Color.Black.copy(alpha = 0.10f),
    textPrimary = Color(0xFF1B1A16),
    textSecondary = Color(0xFF2E2B24),
    textMuted = Color(0xFF5E5648),
    textDim = Color(0xFF8A7F6B),
    textFaint = Color(0xFFAFA793),
    accent = Color(0xFF7C3AED),
    accentLight = Color(0xFF5B21B6),
)

/** All built-in palettes, in display order. */
val builtInPalettes = listOf(
    PaletteMidnight,
    PaletteEmber,
    PaletteOcean,
    PaletteForest,
    PaletteParchment,
)

/**
 * Accent colour choice — applied on top of whichever base palette is
 * active, so users can mix-and-match (e.g. "Midnight base + cyan accent").
 * `light` is the pastel used for active-state text and icon tinting.
 */
data class AccentChoice(
    val name: String,
    val accent: Color,
    val light: Color,
)

val builtInAccents = listOf(
    AccentChoice("Purple", Color(0xFF7C3AED), Color(0xFFC4B5FD)),
    AccentChoice("Indigo", Color(0xFF6366F1), Color(0xFFA5B4FC)),
    AccentChoice("Cyan",   Color(0xFF06B6D4), Color(0xFF67E8F9)),
    AccentChoice("Emerald", Color(0xFF10B981), Color(0xFF6EE7B7)),
    AccentChoice("Amber",  Color(0xFFF59E0B), Color(0xFFFBBF24)),
    AccentChoice("Rose",   Color(0xFFF43F5E), Color(0xFFFDA4AF)),
)
