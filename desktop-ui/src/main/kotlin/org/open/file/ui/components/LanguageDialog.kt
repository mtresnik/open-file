package org.open.file.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.open.file.ui.i18n.Locale
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.i18n.locales
import org.open.file.ui.theme.AppColors

/**
 * Language picker. Renders one row per [locales] entry — flag emoji, the
 * language's own display name (so users see "Español" rather than "Spanish"),
 * and a check mark on the active row.
 *
 * Selection is applied + persisted via [onSelected]; the dialog doesn't
 * dismiss itself because we want the user to see the new strings light up
 * before closing.
 */
@Composable
fun LanguageDialog(
    visible: Boolean,
    selectedCode: String,
    onSelected: (Locale) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AppDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = s.dialogLanguageTitle,
        // Public is the standard Material "globe / worldwide" glyph.
        // Reads as the canonical internationalisation icon across
        // the major icon packs, so users don't need to learn a
        // bespoke symbol for the language modal.
        titleIcon = Icons.Default.Public,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            locales.forEach { locale ->
                LocaleRow(
                    locale = locale,
                    selected = locale.code == selectedCode,
                    onClick = { onSelected(locale) },
                )
            }
        }
    }
}

@Composable
private fun LocaleRow(
    locale: Locale,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppColors.accentBg else AppColors.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AppColors.accentBorder else AppColors.borderLight,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Flag emoji — larger than body text so the row reads at a glance.
        Text(locale.flag, style = TextStyle(fontSize = 18.sp))
        Text(
            locale.displayName,
            style = TextStyle(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) AppColors.accentLight else AppColors.textSecondary,
            ),
            modifier = Modifier.weight(1f),
        )
        // Quiet secondary code — helpful for users with the same language
        // choice offered in multiple scripts (e.g. pt-BR vs pt-PT when we
        // add them later).
        Text(
            locale.code,
            style = TextStyle(
                fontSize = 11.sp,
                color = AppColors.textDim,
            ),
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = AppColors.accentLight,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
