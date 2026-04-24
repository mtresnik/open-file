package org.open.file.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.state.AppState
import org.open.file.ui.state.Tab
import org.open.file.ui.theme.*
import org.open.file.ui.util.LocalLayoutMetrics
import org.open.file.ui.util.PathValidation
import org.open.file.ui.util.copyToClipboard
import org.open.file.ui.util.handOnHover

// ──────────────────────────────────────────────
// Sidebar
// ──────────────────────────────────────────────

data class NavItem(
    val tab: Tab,
    val icon: ImageVector,
    val count: Int? = null,
)

@Composable
fun Sidebar(
    state: AppState,
    navItems: List<NavItem>,
    modifier: Modifier = Modifier,
    /**
     * Opens the umbrella Settings dialog (gear icon in the footer). Wraps
     * theme, display, and any future preferences.
     */
    onOpenSettings: (() -> Unit)? = null,
    /**
     * Opens the language picker (globe icon in the footer). Separate from
     * Settings because language is a high-traffic toggle users should be
     * able to reach without drilling into a sub-dialog.
     */
    onOpenLanguage: (() -> Unit)? = null,
) {
    val metrics = LocalLayoutMetrics.current
    val strings = LocalStrings.current
    val showLabels = metrics.sidebarShowLabels

    Column(
        modifier = modifier
            .width(metrics.sidebarWidth)
            .fillMaxHeight()
            .background(AppColors.surface)
            .border(width = 1.dp, color = AppColors.border, shape = RoundedCornerShape(0.dp))
    ) {
        // Logo — compact windows show only the gradient chip, full
        // windows add the "OpenFile" wordmark + subtitle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (showLabels) 18.dp else 12.dp, vertical = 20.dp)
                .border(width = 0.dp, color = Color.Transparent)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(AppColors.accent, Color(0xFF6366F1))
                        )
                    )
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            if (showLabels) {
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("OpenFile", style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.textSecondary, letterSpacing = (-0.3).sp))
                    Text("filesystem snapshots", style = TextStyle(fontSize = 10.sp, color = AppColors.textDim))
                }
            }
        }

        Divider(color = AppColors.border, thickness = 1.dp)

        // Nav
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp).weight(1f)) {
            if (showLabels) {
                Text(strings.sidebarManage, style = AppTypography.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp).padding(bottom = 8.dp))
            }

            navItems.forEach { item ->
                val isActive = state.activeTab == item.tab
                // Label falls back to enum name in the default/English
                // path; translated labels come through the strings bundle.
                val label = when (item.tab) {
                    Tab.SNAPSHOTS -> strings.tabSnapshots
                    Tab.TEMPLATES -> strings.tabTemplates
                    Tab.BACKUPS -> strings.tabBackups
                }
                // Hover tracking for the left-side indicator bar. We
                // provide our own InteractionSource and disable
                // clickable's default Indication so the built-in
                // Material hover/ripple highlight doesn't tint the
                // whole row — the bar is the sole visual cue.
                val hoverSource = remember { MutableInteractionSource() }
                val isHovered by hoverSource.collectIsHoveredAsState()
                val barColor = when {
                    isActive -> AppColors.accent
                    isHovered -> AppColors.accent.copy(alpha = 0.4f)
                    else -> Color.Transparent
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (showLabels) Arrangement.Start else Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        // `clickable(interactionSource = …)` already
                        // emits HoverInteraction.Enter / Exit on the
                        // shared source — calling `.hoverable(source)`
                        // on top of it double-tracks hover and produced
                        // a visible flicker as the pointer moved.
                        // `indication = null` turns off Material's
                        // default hover / press tint; our drawBehind
                        // bar is the sole cue.
                        .clickable(
                            interactionSource = hoverSource,
                            indication = null,
                            onClick = { state.switchTab(item.tab) },
                        )
                        .handOnHover()
                        // drawBehind paints at the composable's final
                        // rendered height — no `fillMaxHeight` gotchas
                        // with unbounded parent constraints (the root
                        // cause of the earlier "bar spans the whole
                        // sidebar" bug).
                        .drawBehind {
                            if (barColor != Color.Transparent) {
                                drawRect(
                                    color = barColor,
                                    topLeft = Offset.Zero,
                                    size = Size(3.dp.toPx(), size.height),
                                )
                            }
                        }
                        .padding(
                            horizontal = if (showLabels) 11.dp else 8.dp,
                            vertical = 9.dp,
                        )
                ) {
                    Icon(
                        item.icon,
                        contentDescription = label,
                        tint = if (isActive) AppColors.accentLight else AppColors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    if (showLabels) {
                        Spacer(Modifier.width(9.dp))
                        Text(
                            label,
                            style = if (isActive) AppTypography.navItemActive else AppTypography.navItem,
                            modifier = Modifier.weight(1f)
                        )
                        item.count?.let { count ->
                            Text(
                                "$count",
                                style = AppTypography.badge.copy(
                                    color = if (isActive) AppColors.accentLight else AppColors.textDim
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) AppColors.accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }

        // Footer — settings gear + language globe. User asked for the
        // gear icon in the bottom-left; the globe sits next to it as
        // the dedicated i18n trigger. In compact sidebar mode they
        // stack vertically so each icon still has a comfortable tap
        // target.
        if (onOpenSettings != null || onOpenLanguage != null) {
            Divider(color = AppColors.border, thickness = 1.dp)
            if (showLabels) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    onOpenSettings?.let { trigger ->
                        // Tooltip is belt-and-braces with the label —
                        // when the label collapses to icon-only on a
                        // narrow sidebar, the tooltip becomes the
                        // primary affordance for "what does this
                        // icon do?". Always wrap so both modes get
                        // the same hover behaviour.
                        AppTooltip(
                            text = strings.sidebarSettings,
                            modifier = Modifier.weight(1f),
                        ) {
                            FooterIconButton(
                                icon = Icons.Default.Settings,
                                label = strings.sidebarSettings,
                                onClick = trigger,
                            )
                        }
                    }
                    onOpenLanguage?.let { trigger ->
                        AppTooltip(
                            text = strings.sidebarLanguage,
                            modifier = Modifier.weight(1f),
                        ) {
                            FooterIconButton(
                                icon = Icons.Default.Language,
                                label = strings.sidebarLanguage,
                                onClick = trigger,
                            )
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                ) {
                    onOpenSettings?.let { trigger ->
                        AppTooltip(text = strings.sidebarSettings) {
                            FooterIconOnly(
                                icon = Icons.Default.Settings,
                                contentDescription = strings.sidebarSettings,
                                onClick = trigger,
                            )
                        }
                    }
                    onOpenLanguage?.let { trigger ->
                        AppTooltip(text = strings.sidebarLanguage) {
                            FooterIconOnly(
                                icon = Icons.Default.Language,
                                contentDescription = strings.sidebarLanguage,
                                onClick = trigger,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FooterIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // Measure the label's intrinsic width up-front so we can decide
    // whether it fits in the weighted cell without wrapping. Previous
    // behaviour was "render Text with modifier.weight(1f)" which let
    // long translations ("Préférences", "言語設定") wrap onto a
    // second line. We'd rather collapse to icon-only than wrap.
    //
    // The receiver used to be `RowScope` so this composable owned
    // the `.weight(1f)` on itself. It now fillMaxWidth-s inside
    // whatever parent it's embedded in (e.g. AppTooltip), so the
    // parent takes the weight and passes a bounded width down.
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = AppTypography.navItem
    val labelWidthPx = remember(label, style) {
        textMeasurer.measure(label, style).size.width
    }

    // BoxWithConstraints exposes the cell's max width (post-weight)
    // so we can compare pixels to pixels. The Row inside adds its own
    // padding + icon + spacer, so subtract that chrome budget before
    // asking "does the label still fit?".
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        // Icon (16dp) + spacer (8dp) + horizontal padding (10dp each
        // side = 20dp) — the non-text chrome inside the button.
        val chromePx = with(density) { (16.dp + 8.dp + 20.dp).toPx().toInt() }
        val labelFits = labelWidthPx <= constraints.maxWidth - chromePx

        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Center the icon when we've dropped the label — otherwise
            // a left-aligned icon in an otherwise-empty weighted cell
            // reads as "misaligned" rather than "compact".
            horizontalArrangement = if (labelFits) Arrangement.Start else Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onClick)
                .handOnHover()
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = AppColors.textMuted,
                modifier = Modifier.size(16.dp),
            )
            if (labelFits) {
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = style,
                    maxLines = 1,
                    // softWrap=false so a layout pass that somehow
                    // squeezes us (e.g. mid-resize) prefers clipping
                    // over wrapping — the decision to hide entirely
                    // is driven by the measurement above, but this
                    // belt-and-braces guard stops one-frame wraps.
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun FooterIconOnly(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .handOnHover(),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = AppColors.textMuted, modifier = Modifier.size(18.dp))
    }
}

// ──────────────────────────────────────────────
// Row delete action
// ──────────────────────────────────────────────

/**
 * Small trash IconButton intended for [ListRow.actions]. Clicking it
 * opens a [ConfirmDeleteDialog] keyed to [itemName] — confirmation
 * routes to [onConfirm] and the dialog dismisses itself; cancel
 * dismisses without firing.
 *
 * The button consumes its own click, so putting it inside a row whose
 * body is itself clickable (e.g. every [ListRow]) won't trigger the
 * row's onClick. Disabled state (e.g. for backup-pinned snapshots
 * that can't be deleted without removing the backup first) paints the
 * icon dim and suppresses both the click and the dialog.
 *
 * Tooltip on hover spells out the verb — the bare trash glyph is
 * obvious enough visually but the tooltip adds belt-and-braces
 * for users who haven't encountered the pattern.
 */
@Composable
fun RowDeleteIcon(
    itemLabel: String,
    itemName: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledHint: String? = null,
) {
    var showConfirm by remember(itemName) { mutableStateOf(false) }
    val tint = when {
        !enabled -> AppColors.textDim
        else -> AppColors.textMuted
    }

    AppTooltip(
        text = if (enabled) "Delete $itemLabel" else (disabledHint ?: "Can't delete"),
        modifier = modifier,
    ) {
        IconButton(
            onClick = { if (enabled) showConfirm = true },
            enabled = enabled,
            modifier = Modifier.size(28.dp).handOnHover(enabled),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete $itemLabel",
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    ConfirmDeleteDialog(
        visible = showConfirm,
        itemLabel = itemLabel,
        itemName = itemName,
        onConfirm = {
            showConfirm = false
            onConfirm()
        },
        onDismiss = { showConfirm = false },
    )
}

// ──────────────────────────────────────────────
// Single-line text helpers
// ──────────────────────────────────────────────

/**
 * [Text] that never wraps and ellipsizes on overflow.
 *
 * Exists because Compose's default Text wraps on word boundaries
 * *and* within-word when the Row squeezes it below its intrinsic
 * width — producing the "5 backups" → vertical letter-stack
 * pathology in tight list rows and detail panes.
 *
 * Use for any text that sits inside a horizontal Row where the row
 * can narrow: chip labels, hash / id displays, count captions,
 * status pills. If the text genuinely needs to wrap (help text,
 * description paragraphs in a Column) use plain [Text] instead.
 */
@Composable
fun SingleLineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = overflow,
        modifier = modifier,
    )
}

// ──────────────────────────────────────────────
// Tooltip
// ──────────────────────────────────────────────

/**
 * Hover-tooltip wrapper used by icon-only controls that otherwise
 * wouldn't announce their purpose — sidebar footer, header bar's
 * secondary action, etc.
 *
 * Wraps Compose Desktop's [androidx.compose.foundation.TooltipArea]
 * with app-consistent styling (surface-variant background, thin
 * border, body-small font). Delay of 400ms is short enough to feel
 * responsive but long enough that casual mouse-overs don't flash the
 * tooltip on every sweep across the sidebar.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AppColors.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.borderLight),
                shadowElevation = 4.dp,
            ) {
                Text(
                    text,
                    style = TextStyle(
                        fontSize = 11.5.sp,
                        color = AppColors.textSecondary,
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        // 400ms hover feels responsive without flashing the tooltip
        // on every mouse sweep over the sidebar.
        delayMillis = 400,
        // Offset the tooltip from the cursor so it doesn't overlap
        // the button the user is aiming at.
        tooltipPlacement = androidx.compose.foundation.TooltipPlacement.CursorPoint(
            offset = androidx.compose.ui.unit.DpOffset(14.dp, 14.dp),
        ),
        modifier = modifier,
    ) {
        content()
    }
}

// ──────────────────────────────────────────────
// Top Header Bar
// ──────────────────────────────────────────────

@Composable
fun HeaderBar(
    title: String,
    subtitle: String,
    filterText: String,
    onFilterChange: (String) -> Unit,
    filterPlaceholder: String = "Search...",
    onCreateClick: (() -> Unit)? = null,
    createLabel: String = "New",
    /**
     * Optional secondary action — rendered as an icon-only button to the
     * left of the primary Create button. Handy for page-scoped affordances
     * (e.g. "Schedules" on the Backups tab) that shouldn't shove the
     * primary Create out of the way.
     */
    onSecondaryClick: (() -> Unit)? = null,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    secondaryLabel: String? = null,
) {
    val metrics = LocalLayoutMetrics.current
    val isCompact = metrics.sizeClass == org.open.file.ui.util.WindowSizeClass.COMPACT
    // Shrink padding + search width + hide subtitle / create label in
    // compact windows so the header stays usable at narrow widths
    // instead of overflowing its row.
    val horizontalPadding = if (isCompact) 14.dp else 28.dp
    val searchWidth = if (isCompact) 110.dp else 180.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.background)
            .border(width = 0.dp, color = Color.Transparent)
            .padding(horizontal = horizontalPadding, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = AppTypography.pageTitle,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (!isCompact) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = AppTypography.subtitle,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }

        // Search
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(1.dp, AppColors.borderLight, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.textDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = filterText,
                onValueChange = onFilterChange,
                textStyle = TextStyle(fontSize = 13.sp, color = AppColors.textSecondary),
                singleLine = true,
                modifier = Modifier.width(searchWidth).padding(vertical = 6.dp),
                decorationBox = { inner ->
                    Box {
                        if (filterText.isEmpty()) Text(filterPlaceholder, style = TextStyle(fontSize = 13.sp, color = AppColors.textDim), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        inner()
                    }
                }
            )
        }

        // Optional secondary action — rendered icon-only regardless of
        // layout density so the primary Create button always keeps its
        // label in full-size mode.
        if (onSecondaryClick != null && secondaryIcon != null) {
            Spacer(Modifier.width(8.dp))
            // Wrap the icon-only button in a tooltip so users who
            // don't recognise the glyph (e.g. the alarm-clock for
            // Schedules on Backups) get the label on hover. Falls
            // back to the icon's contentDescription when secondaryLabel
            // is null — still better than nothing for screen readers.
            AppTooltip(text = secondaryLabel ?: "") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, AppColors.borderLight, RoundedCornerShape(8.dp))
                        .clickable(onClick = onSecondaryClick)
                        .handOnHover()
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Icon(
                        secondaryIcon,
                        contentDescription = secondaryLabel,
                        tint = AppColors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Create button — icon-only in compact mode so the label doesn't
        // steal horizontal space from the page title + search.
        onCreateClick?.let { onClick ->
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.accentBg)
                    .border(1.dp, AppColors.accentBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .handOnHover()
                    .padding(
                        horizontal = if (isCompact) 9.dp else 14.dp,
                        vertical = 7.dp,
                    )
            ) {
                Icon(Icons.Default.Add, contentDescription = createLabel, tint = AppColors.accentLight, modifier = Modifier.size(14.dp))
                if (!isCompact) {
                    Spacer(Modifier.width(5.dp))
                    Text(createLabel, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.accentLight))
                }
            }
        }
    }
    Divider(color = AppColors.border, thickness = 1.dp)
}

// ──────────────────────────────────────────────
// List Row
// ──────────────────────────────────────────────

@Composable
fun ListRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    /**
     * Always-visible action slot, rendered between [trailing] and the
     * terminal chevron. Unlike `trailing` (which collapses in compact
     * mode to free horizontal room), actions stay pinned — intended
     * for per-row primary verbs like delete that should be reachable
     * without drilling into the detail pane.
     */
    actions: @Composable (RowScope.() -> Unit)? = null,
) {
    val metrics = LocalLayoutMetrics.current
    val isCompact = metrics.sizeClass == org.open.file.ui.util.WindowSizeClass.COMPACT
    // Hover tracking + left indicator bar. Custom InteractionSource
    // shared with clickable (`indication = null`) so Material's default
    // hover ripple doesn't apply its own tint — the bar is the whole
    // visual cue. drawBehind paints at the row's final rendered size;
    // using fillMaxHeight inside a LazyColumn item's unbounded
    // constraints was collapsing the bar to zero height.
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val barColor = when {
        selected -> AppColors.accent
        isHovered -> AppColors.accent.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // See the sidebar rationale — clickable already tracks
            // hover on the shared InteractionSource, so no separate
            // hoverable modifier.
            .clickable(
                interactionSource = hoverSource,
                indication = null,
                onClick = onClick,
            )
            .handOnHover()
            .drawBehind {
                if (barColor != Color.Transparent) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height),
                    )
                }
            }
            // Tighter horizontal padding in compact windows so long rows
            // stop pushing the trailing stats off screen.
            .padding(horizontal = if (isCompact) 14.dp else 28.dp, vertical = 14.dp)
    ) {
        icon?.invoke()
        icon?.let { Spacer(Modifier.width(12.dp)) }
        Column(modifier = Modifier.weight(1f)) { content() }
        // Hide the trailing metadata block in compact mode — secondary
        // information that can be inspected via the detail pane, and
        // keeping it in-line chokes the title column.
        if (!isCompact) {
            trailing?.let {
                Spacer(Modifier.width(12.dp))
                it()
            }
        }
        // Actions stay visible at every breakpoint — they're the
        // primary verbs for the row. Slight spacer so they don't
        // touch the trailing stats (when the latter is shown).
        actions?.let {
            Spacer(Modifier.width(if (isCompact) 4.dp else 10.dp))
            it()
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.textDim, modifier = Modifier.size(12.dp))
    }
    Divider(color = Color.White.copy(alpha = 0.025f), thickness = 1.dp)
}

// ──────────────────────────────────────────────
// Detail Panel Shell
// ──────────────────────────────────────────────

@Composable
fun DetailPanel(
    onClose: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(AppColors.surface)
            .border(width = 1.dp, color = AppColors.border, shape = RoundedCornerShape(0.dp))
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().padding(18.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) { header() }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp).handOnHover()) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.textDim, modifier = Modifier.size(18.dp))
            }
        }
        Divider(color = AppColors.border, thickness = 1.dp)

        // Body — wrap in a Box so we can overlay a visible scrollbar.
        // Detail panes can get tall (history timeline + preview tree
        // + action buttons), and on a short window there was no
        // signal that more content existed below the fold.
        //
        // SelectionContainer makes every Text inside the body (template
        // descriptions, paths, hashes, status labels) click-and-drag
        // selectable. Scoped to the body only so the close X in the
        // header row isn't dragged into the selection. `DetailFieldCode`
        // chips still intercept their single-click as copy-to-clipboard
        // — that's a click gesture; SelectionContainer only hooks drags.
        val bodyScroll = rememberScrollState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(bodyScroll)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    content()
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(bodyScroll),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
            )
        }
    }
}

// ──────────────────────────────────────────────
// LazyColumn + visible scrollbar
// ──────────────────────────────────────────────

/**
 * Drop-in replacement for [LazyColumn] that overlays a desktop-style
 * [VerticalScrollbar] on the right edge. The scrollbar auto-hides
 * when the list fits in its viewport, so short lists look identical
 * to a bare LazyColumn.
 *
 * Callers pass the modifier they'd normally put on LazyColumn (e.g.
 * `Modifier.weight(1f)` for a flex child) and a LazyListScope builder
 * block. State is optional — pass one in to hoist scroll position
 * across recompositions, otherwise we manage one here.
 */
@Composable
fun LazyColumnWithScrollbar(
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            content()
        }
        // A little inset so the thumb doesn't sit flush against the
        // right edge; makes it feel like part of the list rather than
        // the border.
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
        )
    }
}

// ──────────────────────────────────────────────
// Field Label + Value
// ──────────────────────────────────────────────

@Composable
fun DetailField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), style = AppTypography.label)
        content()
    }
}

@Composable
fun DetailFieldText(label: String, value: String) {
    DetailField(label) { Text(value, style = AppTypography.body) }
}

@Composable
fun DetailFieldCode(label: String, value: String) {
    // Clickable chip with inline "Copied" confirmation. Every
    // consumer of DetailFieldCode renders a path / hash / id the
    // user might want to paste somewhere — make the whole chip the
    // affordance. No need for a separate copy icon button; click on
    // the chip itself is the copy action.
    val s = LocalStrings.current
    var justCopied by remember(value) { mutableStateOf(false) }
    // Clear the "Copied" flag after ~1.2s so the confirmation doesn't
    // linger — long enough for the user to see, short enough that
    // repeated copies stay responsive.
    LaunchedEffect(justCopied) {
        if (justCopied) {
            kotlinx.coroutines.delay(1200)
            justCopied = false
        }
    }

    DetailField(label) {
        // Chip on top, "Copied" confirmation on its own row below.
        // A long path consumes the whole Row width, which squeezed the
        // confirmation into a narrow vertical strip previously —
        // stacking it vertically gives the check + label room to
        // render at their natural size.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Wrap long values across multiple lines. Ellipsising a
            // path / hash forced the user to copy-and-paste the chip
            // into a text editor to see what they were looking at —
            // defeating the purpose of showing it in the pane.
            // softWrap = true (default) lets Compose break at
            // character boundaries when there's no word boundary,
            // which is exactly what monospaced paths + hashes want.
            Text(
                value,
                style = AppTypography.code,
                softWrap = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.accent.copy(alpha = 0.08f))
                    .clickable {
                        if (copyToClipboard(value)) justCopied = true
                    }
                    .handOnHover()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (justCopied) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = AppColors.success,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(s.actionCopied, style = AppTypography.bodySmall.copy(color = AppColors.success))
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Action Buttons
// ──────────────────────────────────────────────

@Composable
fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AppColors.textSecondary.copy(alpha = 0.85f),
    borderColor: Color = AppColors.borderLight,
    backgroundColor: Color = Color.White.copy(alpha = 0.03f),
) {
    val fgColor = if (enabled) color else color.copy(alpha = 0.35f)
    val bdColor = if (enabled) borderColor else borderColor.copy(alpha = 0.3f)
    val bgColor = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.4f)
    val metrics = LocalLayoutMetrics.current
    val isCompact = metrics.sizeClass == org.open.file.ui.util.WindowSizeClass.COMPACT
    // DisableSelection guards against ancestor SelectionContainers
    // (e.g. the Settings dialog's right pane, detail panes) scooping
    // up the button label when the user drag-selects nearby text.
    // Button labels are for firing an action, not for copying — so
    // we opt every ActionButton out of the selection tree
    // unconditionally. Outside a SelectionContainer this is a no-op.
    DisableSelection {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .border(1.dp, bdColor, RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onClick)
                // Hand cursor on hover, gated on `enabled` — see
                // Modifier.handOnHover for the rationale on why
                // disabled buttons keep the default arrow.
                .handOnHover(enabled)
                // Tighter padding around icon-only buttons in compact mode
                // so they read as square chips rather than stretched pills.
                .padding(
                    horizontal = if (isCompact) 8.dp else 12.dp,
                    vertical = 7.dp,
                )
        ) {
            // Icon always rendered — a11y-friendly because the label becomes
            // a contentDescription when it's hidden visually.
            Icon(
                icon,
                contentDescription = if (isCompact) label else null,
                tint = fgColor,
                modifier = Modifier.size(13.dp),
            )
            if (!isCompact) {
                Spacer(Modifier.width(5.dp))
                // Single-line guarded — ActionButton often lives inside
                // a detail-pane Row that shrinks when the pane is
                // narrowed (e.g. "Delete" got character-stacked
                // vertically in the Backups info pane). softWrap=false
                // prefers clipping over per-char wrapping; if the user
                // truly has no space for the label, the button still
                // renders icon + whatever fits.
                Text(
                    label,
                    style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = fgColor),
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun DangerButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ActionButton(label, icon, onClick, enabled = enabled, color = AppColors.error, borderColor = AppColors.errorBg)
}

// ──────────────────────────────────────────────
// Tag Chips
// ──────────────────────────────────────────────

@Composable
fun TagChip(tag: String, onRemove: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(start = 7.dp, end = if (onRemove != null) 4.dp else 7.dp, top = 2.dp, bottom = 2.dp)
    ) {
        // SingleLineText — tags in a tag-row compete for horizontal
        // space with the row's other siblings; without the guard a
        // squeezed cell could character-stack the tag label.
        SingleLineText(tag, style = AppTypography.chip.copy(color = AppColors.textMuted))
        onRemove?.let {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $tag",
                tint = AppColors.textDim,
                modifier = Modifier.size(12.dp).clickable(onClick = it).handOnHover(),
            )
        }
    }
}

@Composable
fun TagRow(tags: List<String>, onRemove: ((String) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        tags.forEach { tag -> TagChip(tag, onRemove = onRemove?.let { { it(tag) } }) }
    }
}

// ──────────────────────────────────────────────
// Tag Input (for create modal)
// ──────────────────────────────────────────────

@Composable
fun TagInput(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = AppColors.textSecondary),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, AppColors.borderLight, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            val trimmed = input.trim().lowercase()
                            if (trimmed.isNotEmpty() && trimmed !in tags) {
                                onAddTag(trimmed)
                                input = ""
                            }
                            true
                        } else false
                    },
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) Text("Add tag...", style = TextStyle(fontSize = 13.sp, color = AppColors.textDim))
                        inner()
                    }
                }
            )
            IconButton(
                onClick = {
                    val trimmed = input.trim().lowercase()
                    if (trimmed.isNotEmpty() && trimmed !in tags) {
                        onAddTag(trimmed)
                        input = ""
                    }
                },
                modifier = Modifier.size(32.dp).handOnHover(),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add tag", tint = AppColors.textMuted, modifier = Modifier.size(16.dp))
            }
        }
        if (tags.isNotEmpty()) {
            TagRow(tags, onRemove = onRemoveTag)
        }
    }
}

// ──────────────────────────────────────────────
// Icon Picker (for template creation)
// ──────────────────────────────────────────────

val TEMPLATE_ICON_KEYS = listOf("kotlin", "react", "spring", "python", "rust", "go", "ktor", "node", "swift", "docker", "generic")

@Composable
fun IconPicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("ICON", style = AppTypography.label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            TEMPLATE_ICON_KEYS.forEach { key ->
                val isSelected = selected == key
                val tc = techColorFor(key)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) tc.bg else Color.White.copy(alpha = 0.02f))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AppColors.accent else Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(key) }
                ) {
                    // In Compose Desktop you'd use a Canvas or painterResource
                    // Here we use a text abbreviation as a stand-in for the SVG icons
                    Text(
                        key.take(2).uppercase(),
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = tc.fg
                        )
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Modal Dialog
// ──────────────────────────────────────────────

@Composable
fun AppDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    /**
     * Optional glyph rendered to the left of the [title] in the
     * header band. Passing null keeps the text-only header.
     * Tinted with the accent colour so it reads as part of the
     * header hierarchy rather than decoration; callers who want a
     * different tint can add another overload.
     */
    titleIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    // Pull dialog width from the layout metrics so it tracks the
    // parent window's size. `dialogWidth` = min(windowWidth/2, cap),
    // clamped to a readable floor — prevents the Create Backup
    // modal's schedule fields from crushing each other on wider
    // windows where the old 480dp was too stingy.
    val metrics = LocalLayoutMetrics.current
    val dialogWidth = metrics.dialogWidth

    // Gate dismiss on the file-picker grace period. When a user
    // clicks Browse inside this dialog, the OS picker closes, and
    // some window managers deliver the closing click to the dialog
    // underneath — which would dismiss this dialog a frame later.
    // `DialogDismissLock` suppresses those races for ~500ms.
    Dialog(
        onDismissRequest = {
            if (org.open.file.ui.util.DialogDismissLock.canDismissNow()) {
                onDismiss()
            }
        },
    ) {
        // Cap the dialog to a usable height even on short monitors.
        // Composed Desktop's Dialog isn't constrained by the main
        // window's size — it's its own AWT top-level — so we pin an
        // absolute ceiling here. 560dp fits every supported laptop
        // resolution (1366×768 down to 1280×720) with breathing room
        // for the Windows taskbar / macOS menu bar.
        val maxDialogHeight = 560.dp
        val bodyScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = maxDialogHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.surfaceVariant)
                .border(1.dp, AppColors.borderLight, RoundedCornerShape(14.dp))
        ) {
            // Header — fixed-height band. Kept outside the scrolling
            // body so the title + close button stay reachable even
            // when the dialog's content overflows.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Optional leading icon — shown when a caller passes
                // one (e.g. LanguageDialog → Public/globe, other
                // dialogs can opt in by passing their own). Rendered
                // at the same 18dp size as the SettingsDialog header
                // so all themed dialogs keep a uniform visual scale.
                titleIcon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = AppColors.accentLight,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary), modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp).handOnHover()) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                }
            }
            Divider(color = AppColors.border, thickness = 1.dp)

            // Body — weight(1f) hands the remaining vertical space to
            // this section so the scrollable content fills whatever's
            // below the header without overflowing the dialog.
            //
            // SelectionContainer wraps the body so every Text inside
            // becomes click-and-drag selectable. Users hit this most
            // often on error/message dialogs (copy the error text to
            // paste into a bug report, quote a hash into a terminal,
            // etc.). Scoped to the body only so button labels in the
            // header Row stay non-selectable — dragging through a
            // Cancel button shouldn't start a text selection.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(bodyScroll)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        content()
                    }
                }
                // Visible scrollbar only paints when content actually
                // overflows — rememberScrollbarAdapter auto-hides when
                // maxValue is 0. Aligned to the inner edge with a tiny
                // top-padding so it doesn't touch the divider above.
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(bodyScroll),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// Progress Dialog
// ──────────────────────────────────────────────

/**
 * Modal with a message, optional progress bar and a Cancel button. Used
 * for long-running jobs — today that's backup creation, but the shape is
 * generic enough to be reused by any operation that wants an
 * interruptible progress surface.
 *
 *  - [phase]: short verb describing what's happening ("Scanning",
 *    "Compressing", "Restoring"). Rendered bold above the detail line.
 *  - [detail]: free-form status line, typically the current file's path.
 *    Truncated to a single line because paths are often long.
 *  - [progress]: `null` → indeterminate bar; `0f..1f` → determinate.
 *  - [onCancel]: invoked when the user clicks Cancel. The dialog does not
 *    dismiss itself; the caller drops its own `visible` flag once the
 *    cancellation has actually taken effect, which keeps the dialog on
 *    screen while the job unwinds.
 */
@Composable
fun ProgressDialog(
    visible: Boolean,
    title: String,
    phase: String,
    detail: String,
    progress: Float? = null,
    onCancel: () -> Unit,
) {
    if (!visible) return

    // Mirror AppDialog's responsive width so progress / app dialogs
    // feel consistent — side-by-side they'd look odd if one was
    // half-window-wide and the other was a fixed 480dp.
    val metrics = LocalLayoutMetrics.current
    val dialogWidth = metrics.dialogWidth

    Dialog(onDismissRequest = { /* Only Cancel closes this — mis-click outside is easy. */ }) {
        Column(
            modifier = Modifier
                .width(dialogWidth)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.surfaceVariant)
                .border(1.dp, AppColors.borderLight, RoundedCornerShape(14.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    title,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary),
                    modifier = Modifier.weight(1f),
                )
            }
            Divider(color = AppColors.border, thickness = 1.dp)

            // SelectionContainer — scaffold / backup progress detail
            // lines are commonly useful to copy (path to the file
            // currently being processed, the failing file in an
            // error, etc.). Same reasoning as AppDialog's wrap.
            androidx.compose.foundation.text.selection.SelectionContainer {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    phase,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.accentLight),
                )
                Text(
                    detail,
                    style = AppTypography.bodySmall.copy(color = AppColors.textDim),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        color = AppColors.accent,
                        trackColor = AppColors.accent.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(
                        color = AppColors.accent,
                        trackColor = AppColors.accent.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    ActionButton("Cancel", Icons.Default.Close, onClick = onCancel)
                }
            }
            } // SelectionContainer
        }
    }
}

// ──────────────────────────────────────────────
// Confirm Delete Dialog
// ──────────────────────────────────────────────

/**
 * Generic "are you sure?" modal for destructive actions. Used by every
 * screen's Delete button so the confirmation copy stays consistent: one
 * phrasing pattern, one button layout, one place to update if the UX ever
 * needs to change.
 *
 *  - [itemLabel]: the noun, e.g. `"snapshot"`, `"template"`, `"backup"`.
 *    Rendered lower-case inline — callers should pass the
 *    already-localised noun from [org.open.file.ui.i18n.LocalStrings].
 *  - [itemName]: the human-facing identifier for the thing being deleted
 *    (a directory basename, a template name, etc.), shown quoted.
 *  - [extraMessage]: optional second line for side-effects worth
 *    warning about — e.g. "the linked snapshot will also be removed".
 *  - [onConfirm] fires exactly once per confirmation; the dialog
 *    dismisses itself first, so callers don't need to also flip their
 *    own `visible` state inside [onConfirm].
 */
@Composable
fun ConfirmDeleteDialog(
    visible: Boolean,
    itemLabel: String,
    itemName: String,
    extraMessage: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AppDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = s.dialogDeleteTitle,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AppColors.error,
                modifier = Modifier.size(22.dp),
            )
            Text(
                // `%s "%s"?` template; order preserved across locales
                // because both replacements are consumed left-to-right.
                s.dialogDeletePromptFormat.format(itemLabel, itemName),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.textPrimary),
            )
        }
        Text(
            s.dialogDeleteIrreversible,
            style = AppTypography.bodySmall.copy(color = AppColors.textDim),
        )
        extraMessage?.let {
            Text(it, style = AppTypography.bodySmall.copy(color = AppColors.textMuted))
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(s.actionCancel, Icons.Default.Close, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            DangerButton(
                s.actionDelete,
                Icons.Default.Delete,
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            )
        }
    }
}

/**
 * "Type the exact name to confirm" dialog — heavier guardrail than
 * [ConfirmDeleteDialog], intended for bulk / non-reversible actions
 * where a single errant click shouldn't be enough.
 *
 * Shape:
 *  - Warning glyph + main line ("Delete N backups of 'project'?")
 *  - Optional list of affected items so the user sees what's going
 *    away before they commit
 *  - Text input where the user must type [confirmName] verbatim
 *  - Delete button enabled only when `trimmed == confirmName.trim()`
 *
 * We use [SingleLineText] for the expected-name echo and compare on
 * trimmed equality — avoids trailing-whitespace gotchas while still
 * demanding the user actually type the name rather than click through.
 */
@Composable
fun TypeToConfirmDialog(
    visible: Boolean,
    title: String,
    headlineMessage: String,
    /** Exact string the user must retype to unlock the Delete button. */
    confirmName: String,
    /** Optional item list shown between the message and the input. */
    itemPreview: List<String> = emptyList(),
    /**
     * Optional slot rendered between the item preview and the
     * type-to-confirm input. Used by callers to inject extra
     * toggles that modify the confirmed action — e.g. the Backups
     * group bulk-delete uses it to surface a "Delete attached
     * schedules" checkbox. The dialog doesn't interpret what goes
     * inside; callers own the state and factor it into their
     * onConfirm handler.
     */
    extraContent: @Composable (ColumnScope.() -> Unit)? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val s = LocalStrings.current

    // `remember(confirmName)` resets the typed state when the dialog
    // opens for a different group — otherwise stale input from the
    // previous group would survive and could partially match.
    var typed by remember(confirmName) { mutableStateOf("") }
    val matches = typed.trim() == confirmName.trim() && confirmName.isNotBlank()

    AppDialog(
        visible = true,
        onDismiss = {
            typed = ""
            onDismiss()
        },
        title = title,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AppColors.error,
                modifier = Modifier.size(22.dp),
            )
            Text(
                headlineMessage,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.textPrimary),
            )
        }
        Text(
            s.dialogDeleteIrreversible,
            style = AppTypography.bodySmall.copy(color = AppColors.textDim),
        )

        if (itemPreview.isNotEmpty()) {
            // Scroll-bounded list of affected items. Max height keeps
            // the dialog from ballooning when the group has dozens
            // of rows; the Column is scrollable when it overflows.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, AppColors.borderLight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemPreview.forEach { item ->
                    SingleLineText(
                        "• $item",
                        style = AppTypography.codeSmall.copy(color = AppColors.textMuted),
                    )
                }
            }
        }

        // Caller-injected extras — runs in a Column so the caller
        // can stack multiple widgets (checkbox + help text, etc.)
        // without setting up their own layout container.
        extraContent?.invoke(this)

        // Type-to-confirm prompt — show the exact name the user must
        // type in a copy-looking chip so they can eyeball it without
        // squinting at the group header. Monospace + accent tint so
        // it reads as "the literal string to reproduce".
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Type",
                    style = AppTypography.bodySmall.copy(color = AppColors.textDim),
                )
                SingleLineText(
                    confirmName,
                    style = AppTypography.code,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.accent.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    "to confirm:",
                    style = AppTypography.bodySmall.copy(color = AppColors.textDim),
                )
            }
            AppTextField(
                value = typed,
                onValueChange = { typed = it },
                label = "",
                placeholder = confirmName,
            )
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                s.actionCancel,
                Icons.Default.Close,
                onClick = {
                    typed = ""
                    onDismiss()
                },
            )
            Spacer(Modifier.width(8.dp))
            DangerButton(
                s.actionDelete,
                Icons.Default.Delete,
                enabled = matches,
                onClick = {
                    if (matches) {
                        typed = ""
                        onDismiss()
                        onConfirm()
                    }
                },
            )
        }
    }
}

// ──────────────────────────────────────────────
// Text Input Field
// ──────────────────────────────────────────────

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    helpText: String? = null,
    mono: Boolean = true,
) {
    // Track focus state so the outline can shift to the accent
    // colour while the user is actually typing. BasicTextField
    // exposes an InteractionSource; collecting `isFocused` from it
    // gives us a Compose-tracked bool that drives both the border
    // colour and its thickness (1dp → 1.5dp when focused for a
    // subtle "you're here" cue).
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) AppColors.accent else AppColors.borderLight
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), style = AppTypography.label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                color = AppColors.textSecondary
            ),
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.accentLight),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = TextStyle(fontSize = 13.sp, color = AppColors.textDim))
                    inner()
                }
            }
        )
        helpText?.let { Text(it, style = TextStyle(fontSize = 11.sp, color = AppColors.textFaint)) }
    }
}

// ──────────────────────────────────────────────
// Path Validation Status Line
// ──────────────────────────────────────────────

/**
 * Inline status row that mirrors the three [PathValidation] outcomes:
 *  - [PathValidation.Empty]: render nothing — the user hasn't typed yet,
 *    and surfacing an error before any input would be noisy.
 *  - [PathValidation.Invalid]: red ErrorOutline + the specific reason,
 *    so the user knows exactly what's wrong (missing / not a dir / unreadable).
 *  - [PathValidation.Valid]: subtle accent-colored confirmation.
 *
 * Extracted here so SnapshotsScreen and TemplatesScreen share the same UI
 * (not just the same validation data) for their directory-picker dialogs.
 */
@Composable
fun PathStatusLine(validation: PathValidation) {
    val s = LocalStrings.current
    when (validation) {
        is PathValidation.Invalid -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = AppColors.error,
                modifier = Modifier.size(14.dp),
            )
            // Validation reasons come back in English from the util; map
            // them to translated strings here so users see localized
            // copy without the util layer needing a Strings dependency.
            val translatedReason = when (validation.reason) {
                "Path does not exist" -> s.validationPathNotExist
                "Path is not a directory" -> s.validationPathNotDirectory
                "Directory is not readable" -> s.validationPathNotReadable
                else -> validation.reason
            }
            Text(translatedReason, style = AppTypography.bodySmall.copy(color = AppColors.error))
        }
        PathValidation.Valid -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AppColors.accentLight,
                modifier = Modifier.size(14.dp),
            )
            Text(
                s.validationDirectoryReadable,
                style = AppTypography.bodySmall.copy(color = AppColors.accentLight),
            )
        }
        PathValidation.Empty -> { /* no message while the field is blank */ }
    }
}

// ──────────────────────────────────────────────
// Status Dot
// ──────────────────────────────────────────────

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ──────────────────────────────────────────────
// Empty State
// ──────────────────────────────────────────────

@Composable
fun EmptyState(message: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(60.dp)
    ) {
        Text(message, style = AppTypography.bodySmall.copy(color = AppColors.textDim))
    }
}
