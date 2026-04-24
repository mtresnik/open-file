package org.open.file.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// --- Colors ---
//
// Previously plain `val` constants; now the ones users can customise via
// the Theme dialog are backed by `mutableStateOf` so Compose tracks reads
// and recomposes affected surfaces when the theme changes. Accessors look
// identical to callers (e.g. `AppColors.background`) — only the underlying
// storage differs. `ThemePreferences.apply()` is the only place that
// writes these; doing it that way keeps the read paths stable without
// introducing a CompositionLocal refactor across every screen.
object AppColors {
    var background by mutableStateOf(Color(0xFF0C0E14))
        internal set
    var surface by mutableStateOf(Color(0xFF10121A))
        internal set
    var surfaceVariant by mutableStateOf(Color(0xFF151823))
        internal set
    var border by mutableStateOf(Color.White.copy(alpha = 0.05f))
        internal set
    var borderLight by mutableStateOf(Color.White.copy(alpha = 0.08f))
        internal set

    var textPrimary by mutableStateOf(Color(0xFFF1F5F9))
        internal set
    var textSecondary by mutableStateOf(Color(0xFFE2E8F0))
        internal set
    var textMuted by mutableStateOf(Color(0xFF8892A4))
        internal set
    var textDim by mutableStateOf(Color(0xFF555D6E))
        internal set
    var textFaint by mutableStateOf(Color(0xFF3F4553))
        internal set

    var accent by mutableStateOf(Color(0xFF7C3AED))
        internal set
    var accentLight by mutableStateOf(Color(0xFFC4B5FD))
        internal set
    // Derived accent shades live as state too so they stay in sync when
    // the user picks a new accent — ThemePreferences.apply() recomputes
    // them from the chosen base.
    var accentBg by mutableStateOf(Color(0xFF7C3AED).copy(alpha = 0.10f))
        internal set
    var accentBorder by mutableStateOf(Color(0xFF7C3AED).copy(alpha = 0.27f))
        internal set

    val success = Color(0xFF4ADE80)
    val warning = Color(0xFFFBBF24)
    val error = Color(0xFFF87171)
    val errorBg = Color(0xFFEF4444).copy(alpha = 0.25f)

    // Tech colors — not user-customisable, stay as plain vals.
    val kotlin = Color(0xFF7F52FF)
    val react = Color(0xFF61DAFB)
    val spring = Color(0xFF6DB33F)
    val python = Color(0xFF3776AB)
    val rust = Color(0xFFDEA584)
    val go = Color(0xFF00ADD8)
    val node = Color(0xFF339933)
    val swift = Color(0xFFF05138)
    val docker = Color(0xFF2496ED)
}

data class TechColor(
    val bg: Color,
    val fg: Color,
    val accent: Color
)

val techColors = mapOf(
    "kotlin" to TechColor(AppColors.kotlin.copy(alpha = 0.12f), Color(0xFFA78BFA), AppColors.kotlin),
    "react" to TechColor(AppColors.react.copy(alpha = 0.10f), Color(0xFF67E8F9), AppColors.react),
    "spring" to TechColor(AppColors.spring.copy(alpha = 0.12f), Color(0xFF86EFAC), AppColors.spring),
    "python" to TechColor(AppColors.python.copy(alpha = 0.12f), Color(0xFF93C5FD), AppColors.python),
    "rust" to TechColor(AppColors.rust.copy(alpha = 0.12f), Color(0xFFFDBA74), AppColors.rust),
    "go" to TechColor(AppColors.go.copy(alpha = 0.12f), Color(0xFF67E8F9), AppColors.go),
    "ktor" to TechColor(AppColors.kotlin.copy(alpha = 0.12f), Color(0xFFC4B5FD), AppColors.kotlin),
    "node" to TechColor(AppColors.node.copy(alpha = 0.12f), Color(0xFF86EFAC), AppColors.node),
    "swift" to TechColor(AppColors.swift.copy(alpha = 0.12f), Color(0xFFFCA5A5), AppColors.swift),
    "docker" to TechColor(AppColors.docker.copy(alpha = 0.12f), Color(0xFF93C5FD), AppColors.docker),
    "generic" to TechColor(Color.White.copy(alpha = 0.05f), Color(0xFF8892A4), Color(0xFF555555)),
)

fun techColorFor(type: String): TechColor = techColors[type] ?: techColors["generic"]!!

// --- Fonts ---
//
// Split into a state-backed `primary` (the font the user picks for prose +
// UI chrome) and a fixed `mono` (we always want monospace for code / paths /
// ids — swapping those to a proportional font is worse than useless). The
// `primary` backing is mutableStateOf so [ThemePreferences.apply] can write
// it and every consumer of [AppTypography] recomposes automatically.
object AppFonts {
    /** User-facing font. Defaults to the system's sans-serif (looks native on every OS). */
    var primary by mutableStateOf<FontFamily>(FontFamily.Default)
        internal set

    /** Code / path / id font. Always monospace; no user knob. */
    val mono: FontFamily = FontFamily.Monospace
}

/**
 * Supported font choices in the Settings → Appearance picker.
 *
 * We deliberately stick to Compose's built-in generic families —
 * bundling a custom TTF means dealing with licenses, binary-size
 * inflation, and per-OS rendering differences. The generics cover the
 * four categories users typically mean by "font": system default,
 * sans-serif, serif, and monospace.
 *
 * [storageKey] is what we persist; parsing back via [byKey] is
 * case-insensitive so handwritten edits to the prefs file survive.
 */
enum class FontChoice(val storageKey: String, val displayName: String, val family: FontFamily) {
    SYSTEM("system", "System default", FontFamily.Default),
    SANS("sans", "Sans-serif", FontFamily.SansSerif),
    SERIF("serif", "Serif", FontFamily.Serif),
    MONO("mono", "Monospace", FontFamily.Monospace);

    companion object {
        fun byKey(key: String?): FontChoice =
            values().firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}

// --- Typography ---
//
// Every style that references an AppColors value is declared as a `get()`
// property rather than a plain `val`. `val` captures the colour at object-
// init time, which means a theme swap would leave `rowTitle.color` etc.
// stuck on whichever value was live the first time AppTypography was
// touched — the whole point of making AppColors state-backed was to
// avoid exactly that. With custom getters, each read rebuilds a fresh
// TextStyle from the current AppColors, and Compose's snapshot system
// tracks the underlying state reads so callers recompose on theme change.
//
// The same trick applies to `AppFonts.primary`: reads inside a getter
// propagate the font choice without needing every call site to know.
object AppTypography {
    /** Back-compat alias for callers that reference the mono family directly. */
    val mono: FontFamily get() = AppFonts.mono

    val pageTitle: TextStyle
        get() = TextStyle(fontSize = 20.sp, fontFamily = AppFonts.primary, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, letterSpacing = (-0.02).sp)
    val subtitle: TextStyle
        get() = TextStyle(fontSize = 12.5.sp, fontFamily = AppFonts.primary, color = AppColors.textDim)
    val navItem: TextStyle
        get() = TextStyle(fontSize = 13.5.sp, fontFamily = AppFonts.primary, color = AppColors.textMuted)
    val navItemActive: TextStyle
        get() = TextStyle(fontSize = 13.5.sp, fontFamily = AppFonts.primary, fontWeight = FontWeight.Medium, color = AppColors.accentLight)
    val label: TextStyle
        get() = TextStyle(fontSize = 10.sp, fontFamily = AppFonts.primary, fontWeight = FontWeight.SemiBold, color = AppColors.textFaint, letterSpacing = 0.6.sp)
    val body: TextStyle
        get() = TextStyle(fontSize = 13.5.sp, fontFamily = AppFonts.primary, color = AppColors.textSecondary)
    val bodySmall: TextStyle
        get() = TextStyle(fontSize = 12.sp, fontFamily = AppFonts.primary, color = AppColors.textDim)
    val code: TextStyle
        get() = TextStyle(fontSize = 12.sp, fontFamily = AppFonts.mono, color = AppColors.accentLight)
    val codeSmall: TextStyle
        get() = TextStyle(fontSize = 11.sp, fontFamily = AppFonts.mono, color = AppColors.textMuted)
    val rowTitle: TextStyle
        get() = TextStyle(fontSize = 14.sp, fontFamily = AppFonts.primary, fontWeight = FontWeight.Medium, color = AppColors.textPrimary)
    // `chip` has no color — callers apply one via `.copy(color = …)`.
    // Kept as a getter now (was a plain val) so the mono family goes
    // through AppFonts uniformly and future tweaks to the mono source
    // pick up automatically.
    val chip: TextStyle
        get() = TextStyle(fontSize = 10.5.sp, fontFamily = AppFonts.mono, fontWeight = FontWeight.SemiBold)
    val badge: TextStyle
        get() = TextStyle(fontSize = 10.5.sp, fontFamily = AppFonts.mono, fontWeight = FontWeight.SemiBold, color = AppColors.textDim)
}

// --- Material3 color scheme ---
//
// Recomputed on every recomposition so a live theme change (user picking a
// new preset or accent) rebuilds the Material scheme from the freshly-set
// AppColors values. Cheap — darkColorScheme is just a data-class copy.
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = AppColors.accent,
        onPrimary = Color.White,
        secondary = AppColors.accentLight,
        background = AppColors.background,
        surface = AppColors.surface,
        surfaceVariant = AppColors.surfaceVariant,
        onBackground = AppColors.textPrimary,
        onSurface = AppColors.textSecondary,
        error = AppColors.error,
    )
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
