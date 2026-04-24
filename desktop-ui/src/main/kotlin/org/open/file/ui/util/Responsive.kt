package org.open.file.ui.util

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Three-tier width breakpoint used by the app layout.
 *
 *  - [COMPACT]: `< 780dp` — sidebar collapses to icons only, detail panels
 *    narrow, dialog widths shrink. Typical for half-screen / tablet-sized
 *    windows.
 *  - [MEDIUM]: `780..1099dp` — default layout with slightly trimmed
 *    sidebar width.
 *  - [EXPANDED]: `>= 1100dp` — generous spacing, full nav labels, full
 *    dialog widths.
 *
 * Breakpoints are deliberately coarse: Compose Desktop windows can be
 * resized live, and the layout doesn't need pixel-precise adaptivity.
 */
enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

/** Classify a width in dp into one of the [WindowSizeClass] tiers. */
fun classifyWidth(width: Dp): WindowSizeClass = when {
    width < 780.dp -> WindowSizeClass.COMPACT
    width < 1100.dp -> WindowSizeClass.MEDIUM
    else -> WindowSizeClass.EXPANDED
}

/**
 * Layout constants the screens read through [LocalLayoutMetrics]. Bundling
 * them in one immutable container keeps the breakpoint logic centralised —
 * when we want to tune the sidebar's narrow width, every consumer picks it
 * up without touching a dozen files.
 */
@Immutable
data class LayoutMetrics(
    val sizeClass: WindowSizeClass,
    /** Width of the sidebar. Compact mode hides labels; only the icon column stays. */
    val sidebarWidth: Dp,
    /** Whether the sidebar should render labels or just the icons. */
    val sidebarShowLabels: Boolean,
    /** Width of the right-hand detail panel on screens that use one. */
    val detailPanelWidth: Dp,
    /**
     * Upper bound for modal dialog width. Dialogs aim for half the
     * window width but cap at this value so they don't sprawl across
     * a 4K monitor — and coerce up to a floor (~520dp) so form fields
     * don't get crushed even when half-window is tiny.
     */
    val dialogMaxWidth: Dp,
    /**
     * Raw window width in dp. Dialogs use this to compute a half-
     * window target that tracks the parent window's live size — when
     * the user resizes the main window, the next dialog they open
     * sizes itself to the new dimensions.
     */
    val windowWidthDp: Dp,
) {
    /**
     * Suggested width for an [AppDialog]. Half the window, clamped
     * to a sensible min / max so:
     *  - Ultra-wide monitors don't produce 1500dp modals nobody can
     *    scan without rotating their neck.
     *  - Compact windows don't crush the inner form below the
     *    minimum usable width (fields + buttons need ~520dp to
     *    breathe without text wrapping awkwardly).
     */
    val dialogWidth: Dp
        get() = (windowWidthDp / 2).coerceIn(520.dp, dialogMaxWidth)

    companion object {
        fun forWidth(width: Dp): LayoutMetrics {
            val clazz = classifyWidth(width)
            return when (clazz) {
                WindowSizeClass.COMPACT -> LayoutMetrics(
                    sizeClass = clazz,
                    sidebarWidth = 60.dp,
                    sidebarShowLabels = false,
                    detailPanelWidth = 300.dp,
                    // Cap raised from 380 → 560 so even a compact
                    // window gets a readable-width dialog. The
                    // `dialogWidth` getter coerces to a 520dp floor,
                    // so in practice compact dialogs will usually
                    // render at that floor rather than this cap.
                    dialogMaxWidth = 560.dp,
                    windowWidthDp = width,
                )
                WindowSizeClass.MEDIUM -> LayoutMetrics(
                    sizeClass = clazz,
                    sidebarWidth = 210.dp,
                    sidebarShowLabels = true,
                    detailPanelWidth = 340.dp,
                    // Bumped from 460 → 720 so the Create Backup
                    // dialog (which carries source / target /
                    // schedule toggle / cron input) has enough
                    // horizontal space for the cron preset chips
                    // without horizontal-scrolling.
                    dialogMaxWidth = 720.dp,
                    windowWidthDp = width,
                )
                WindowSizeClass.EXPANDED -> LayoutMetrics(
                    sizeClass = clazz,
                    sidebarWidth = 230.dp,
                    sidebarShowLabels = true,
                    detailPanelWidth = 360.dp,
                    // Upper cap for wide monitors. Dialogs aim for
                    // half-window, so at 2000dp the dialog is 1000dp
                    // clamped to this 900dp ceiling — wide enough for
                    // multi-column forms, not so wide that headers
                    // and help text strand in the middle.
                    dialogMaxWidth = 900.dp,
                    windowWidthDp = width,
                )
            }
        }
    }
}

/**
 * CompositionLocal carrying the current [LayoutMetrics]. Default value is
 * the expanded tier so composables that render outside the app root (e.g.
 * isolated previews) look right instead of defaulting to compact.
 */
val LocalLayoutMetrics = compositionLocalOf { LayoutMetrics.forWidth(1200.dp) }
