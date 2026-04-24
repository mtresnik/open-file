package org.open.file.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.open.file.ui.theme.AccentChoice
import org.open.file.ui.theme.AppColors
import org.open.file.ui.theme.AppPalette
import org.open.file.ui.theme.AppTypography
import org.open.file.ui.theme.FontChoice
import org.open.file.ui.theme.builtInAccents
import org.open.file.ui.theme.builtInPalettes

/**
 * Inner theme controls — palette grid + accent row, no dialog chrome.
 *
 * Exposed separately from [ThemeDialog] so the umbrella
 * [SettingsDialog] can embed the same widgets under its own header, and
 * the controls stay DRY. Selecting a swatch applies + persists via the
 * passed-in callbacks; the visual preview comes for free because
 * `palette.apply()` writes to [AppColors] which every surface reads
 * reactively.
 */
@Composable
fun ThemeControls(
    selectedPalette: String?,
    selectedAccent: String?,
    selectedFont: FontChoice,
    onPaletteSelected: (AppPalette) -> Unit,
    onAccentSelected: (AccentChoice) -> Unit,
    onFontSelected: (FontChoice) -> Unit,
) {
    val s = org.open.file.ui.i18n.LocalStrings.current
    Text(s.dialogThemePaletteLabel, style = AppTypography.label)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        builtInPalettes.chunked(3).forEach { rowPalettes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowPalettes.forEach { palette ->
                    PaletteSwatch(
                        palette = palette,
                        selected = palette.name == selectedPalette,
                        onClick = { onPaletteSelected(palette) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad the final row if it doesn't fill the grid so the
                // remaining cells align with the row above.
                repeat(3 - rowPalettes.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    Text(s.dialogThemeAccentLabel, style = AppTypography.label)
    // Even-share layout: each swatch gets `weight(1f)` of the
    // available width so all six end-up the same size regardless of
    // label length. Before, the Row let each cell take its intrinsic
    // max(circle, label) width, so longer labels like "Emerald" gave
    // those cells more room and squeezed the short-label cells like
    // "Rose" at the end.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        builtInAccents.forEach { accent ->
            AccentSwatch(
                accent = accent,
                selected = accent.name == selectedAccent,
                onClick = { onAccentSelected(accent) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    Text("FONT", style = AppTypography.label)
    // Dropdown picker for the font family — takes one row instead of
    // four chips, frees up vertical space in the Appearance section.
    // The selected label renders in its own FontFamily so the user
    // previews the shape without having to open the menu again.
    FontDropdown(
        selected = selectedFont,
        onSelect = onFontSelected,
    )
}

/**
 * Standalone Theme dialog — kept for callers that want the theme surface
 * without the broader Settings umbrella. Re-uses [ThemeControls] so the
 * two entry points stay in sync.
 */
@Composable
fun ThemeDialog(
    visible: Boolean,
    selectedPalette: String?,
    selectedAccent: String?,
    selectedFont: FontChoice,
    onPaletteSelected: (AppPalette) -> Unit,
    onAccentSelected: (AccentChoice) -> Unit,
    onFontSelected: (FontChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = org.open.file.ui.i18n.LocalStrings.current
    AppDialog(visible = visible, onDismiss = onDismiss, title = s.dialogThemeTitle) {
        ThemeControls(
            selectedPalette = selectedPalette,
            selectedAccent = selectedAccent,
            selectedFont = selectedFont,
            onPaletteSelected = onPaletteSelected,
            onAccentSelected = onAccentSelected,
            onFontSelected = onFontSelected,
        )

        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                s.actionDone,
                androidx.compose.material.icons.Icons.Default.Check,
                onClick = onDismiss,
                backgroundColor = AppColors.accentBg,
                borderColor = AppColors.accentBorder,
                color = AppColors.accentLight,
            )
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: AppPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Miniature of the palette: background + three dots for surface /
    // accent / text. Conveys the "feel" of the theme at a glance without
    // needing to actually apply it first.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.accent else AppColors.borderLight,
                shape = RoundedCornerShape(8.dp),
            )
            .background(palette.background)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SwatchDot(palette.surface)
            SwatchDot(palette.accent)
            SwatchDot(palette.textPrimary)
        }
        Text(
            palette.name,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = palette.textSecondary),
        )
    }
}

@Composable
private fun SwatchDot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
    )
}

/**
 * Dropdown picker over [FontChoice]. The collapsed state shows the
 * currently-selected font's name rendered in that font; clicking opens
 * a Material3 [DropdownMenu] with every option, each label also
 * rendered in its own family so the user can preview before picking.
 *
 * We avoid `ExposedDropdownMenuBox` to stay off the experimental
 * Material3 API — a plain clickable Row + anchored [DropdownMenu]
 * gives us the same UX without the opt-in.
 */
@Composable
private fun FontDropdown(
    selected: FontChoice,
    onSelect: (FontChoice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.03f))
                .border(1.dp, AppColors.borderLight, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                selected.displayName,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontFamily = selected.family,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.textSecondary,
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) androidx.compose.material.icons.Icons.Default.ArrowDropUp
                else androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Match the anchor's background so the menu blends with
            // the rest of the settings surface — default looks like a
            // pop-up from a different app.
            modifier = Modifier.background(AppColors.surfaceVariant),
        ) {
            FontChoice.values().forEach { choice ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            choice.displayName,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontFamily = choice.family,
                                color = if (choice == selected) AppColors.accentLight
                                else AppColors.textSecondary,
                            ),
                        )
                    },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    accent: AccentChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.accent)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AppColors.textPrimary else Color.White.copy(alpha = 0.10f),
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        )
        // maxLines + ellipsis in case the cell is narrower than the
        // label ("Emerald") at very small window widths. Labels
        // never wrap to a second line — keeps the row height
        // consistent across swatches.
        Text(
            accent.name,
            style = TextStyle(fontSize = 10.sp, color = AppColors.textMuted),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
      