package org.open.file.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/**
 * App-icon tint — Material Blue 400. Reads cleanly on both dark and
 * light OS chrome (taskbars, title bars, tray shelves).
 *
 * Centralising the constant keeps the sidebar chip, Tray icon, and
 * Window icon from drifting in tone as we tweak the colour.
 */
val AppIconTint: Color = Color(0xFF42A5F5)

/**
 * Intrinsic size we report for the app-icon painter, in Compose
 * pixels. Material vectors have a 24×24 native intrinsic; reporting
 * a much larger size tells the host (AWT Window / Tray) to scale
 * the painter *up* by calling `draw()` with the larger target size.
 * That target is what reaches the vector's rasteriser, so the glyph
 * is rendered crisply at taskbar / tray resolution instead of being
 * a pixel-doubled 24×24 blur.
 *
 * 256 is the sweet spot: high enough that Windows high-DPI taskbars
 * and macOS retina tray slots get a sharp down-sample, low enough
 * that we're not allocating silly raster buffers.
 */
private const val APP_ICON_INTRINSIC_DP: Float = 256f

/**
 * Returns a [Painter] that renders the material Folder icon tinted
 * with [AppIconTint] (or a caller-supplied override), sized for OS
 * chrome surfaces.
 *
 * The stock `rememberVectorPainter(Icons.Default.Folder)` has two
 * problems for Window / Tray use:
 *  1. Material vectors render in their native black — invisible on
 *     a dark taskbar background.
 *  2. Their intrinsic size is 24×24, which AWT treats as a hint to
 *     upscale a tiny bitmap when the actual taskbar / tray slot is
 *     32px or 48px — the resulting icon looks small and soft.
 *
 * This wrapper fixes both: it re-paints through a
 * [ColorFilter.tint] *and* reports a 256×256 intrinsic size, and
 * forwards the host-provided draw size through so the vector is
 * re-rasterised at whatever resolution the host actually needs.
 *
 * Used by:
 *  - `Window(icon = rememberAppIconPainter())` — title-bar / taskbar
 *    / Alt-Tab on all platforms.
 *  - `Tray(icon = rememberAppIconPainter())` — system tray /
 *    notification area.
 *
 * In-app uses (sidebar chip, dialogs) can keep using `Icon(...)`
 * directly since they set their own tint + size.
 */
@Composable
fun rememberAppIconPainter(tint: Color = AppIconTint): Painter {
    val base = rememberVectorPainter(Icons.Default.Folder)
    // Wrap the vector painter so we can redraw it through a
    // ColorFilter.tint and at a larger effective size.
    // VectorPainter's own `tintColor` / intrinsic-size fields aren't
    // exposed as public API we can set post-construction, so the
    // wrapper-Painter approach is the least-magic option.
    return remember(base, tint) {
        object : Painter() {
            override val intrinsicSize: Size =
                Size(APP_ICON_INTRINSIC_DP, APP_ICON_INTRINSIC_DP)

            override fun DrawScope.onDraw() {
                with(base) {
                    // Forward the host-provided DrawScope.size (not
                    // the base vector's tiny intrinsicSize) so the
                    // vector rasterises into the full icon slot.
                    // AWT's icon plumbing calls us with the target
                    // surface's actual dimensions, so this guarantees
                    // the folder fills the slot rather than sitting
                    // as a pinprick in the middle.
                    draw(size = size, colorFilter = ColorFilter.tint(tint))
                }
            }
        }
    }
}
