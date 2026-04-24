package org.open.file.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * Show the OS "hand" (pointing-finger) cursor while this modifier's
 * region is hovered. Centralised here so the app has one consistent
 * cue for "this is clickable" across buttons, icon buttons, nav
 * rows, inline clickable glyphs, etc.
 *
 * Pass [enabled] = false to opt out without branching the modifier
 * chain at the call site — useful when a surface is conditionally
 * clickable (e.g. a disabled button should keep the default arrow
 * so the cursor shape reinforces the non-interactive state).
 *
 * Placement: apply *after* `Modifier.clickable(...)` so the hover
 * region matches exactly what's interactive. Placing it earlier in
 * the chain still works (Compose applies hover icons from inside
 * out) but the intent reads more clearly when the cursor modifier
 * sits next to the click handler.
 *
 * Outside any hoverable ancestor this is a no-op, so it's safe to
 * pepper through shared primitives without worrying about mobile
 * touch targets or server-rendered surfaces.
 */
fun Modifier.handOnHover(enabled: Boolean = true): Modifier =
    if (enabled) this.pointerHoverIcon(PointerIcon.Hand) else this
