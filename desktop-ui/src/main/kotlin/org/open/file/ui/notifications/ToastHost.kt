package org.open.file.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import org.open.file.ui.theme.AppColors
import org.open.file.ui.util.handOnHover
import org.open.file.ui.util.copyToClipboard

/**
 * Renders the [toaster]'s queue as a bottom-anchored stack of cards.
 *
 * Each toast auto-dismisses after [autoDismissMillis] unless the user
 * clicks the close `x` first. Mount this once at the top of the
 * composition tree, outside any scrollable content, so toasts float above
 * everything.
 *
 * Layout: `Box(fillMaxSize).BottomCenter` wrapping a Column. Toasts appear
 * newest-on-top by iterating the list in reverse — new arrivals push
 * older ones down visually, which matches most OS toast conventions.
 */
@Composable
fun ToastHost(
    toaster: Toaster,
    autoDismissMillis: Long = 4_500L,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier.padding(bottom = 20.dp, end = 20.dp, start = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Reverse so the most recent toast sits on top.
            toaster.toasts.asReversed().forEach { toast ->
                ToastCard(toast = toast, onDismiss = { toaster.dismiss(toast.id) })

                // Per-toast auto-dismiss timer. Keyed on the toast id so
                // the timer re-starts only when a new toast arrives, not
                // on every recomposition of unrelated state.
                //
                // Dismiss delay scales with message length so long
                // errors (stack-trace-like "Delete failed for <uuid>
                // because …") stay readable. ~30ms per character on
                // top of the base gives roughly 200 wpm reading
                // speed, clamped to 12s so a pathological 500-char
                // message doesn't linger forever.
                val dismissMs = (autoDismissMillis + toast.message.length * 30L)
                    .coerceIn(autoDismissMillis, 12_000L)
                LaunchedEffect(toast.id) {
                    delay(dismissMs)
                    toaster.dismiss(toast.id)
                }
            }
        }
    }
}

@Composable
private fun ToastCard(toast: Toast, onDismiss: () -> Unit) {
    val accent = when (toast.level) {
        ToastLevel.INFO -> AppColors.accentLight
        ToastLevel.WARNING -> AppColors.warning
        ToastLevel.ERROR -> AppColors.error
    }
    val icon = when (toast.level) {
        ToastLevel.INFO -> Icons.Default.Info
        ToastLevel.WARNING -> Icons.Default.WarningAmber
        ToastLevel.ERROR -> Icons.Default.ErrorOutline
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.surfaceVariant)
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            // SelectionContainer around the toast message — users
            // hitting an error ("Scaffold failed: …", "Delete backup
            // failed for <uuid>") want to click-and-drag to copy it
            // into a bug report or paste the UUID into a terminal.
            // Scoped to the message cell so the dismiss IconButton
            // isn't drawn into the selection.
            androidx.compose.foundation.text.selection.SelectionContainer(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    toast.message,
                    style = TextStyle(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.textSecondary,
                    ),
                )
            }

            // Inline copy button — users hitting an error typically
            // want the text on their clipboard before the toast
            // fades. Tapping flashes a checkmark for ~1.2s, then
            // the icon reverts to ContentCopy so a follow-up copy
            // (if the toast hasn't auto-dismissed) still works.
            // Keyed on toast.id so one toast's confirmation state
            // doesn't leak into its neighbour in the stack.
            var justCopied by remember(toast.id) { mutableStateOf(false) }
            LaunchedEffect(justCopied) {
                if (justCopied) {
                    delay(1200)
                    justCopied = false
                }
            }
            IconButton(
                onClick = {
                    if (copyToClipboard(toast.message)) justCopied = true
                },
                modifier = Modifier.size(24.dp).handOnHover(),
            ) {
                Icon(
                    if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = if (justCopied) "Copied" else "Copy message",
                    tint = if (justCopied) AppColors.success else AppColors.textDim,
                    modifier = Modifier.size(14.dp),
                )
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp).handOnHover()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = AppColors.textDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
