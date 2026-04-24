package org.open.file.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import java.util.concurrent.atomic.AtomicLong

/**
 * Visual urgency for a toast. Drives colour + icon in [ToastHost] without
 * the caller having to pass styling in; the level enum is the whole API.
 */
enum class ToastLevel { INFO, WARNING, ERROR }

/**
 * Single toast entry held in [Toaster.toasts]. [id] is unique-per-process
 * so list keys stay stable even when two toasts have identical messages.
 */
data class Toast(
    val id: Long,
    val message: String,
    val level: ToastLevel,
)

/**
 * Host-agnostic state holder for the toast queue.
 *
 * Kept deliberately tiny: [show] enqueues, [dismiss] removes, [toasts] is
 * the observable list a host composable renders. Auto-dismiss is driven
 * by the host via a [kotlinx.coroutines.delay] on each entry — keeping the
 * timing out of Toaster means tests can hold toasts open indefinitely and
 * the host owns its own lifecycle.
 */
class Toaster {
    private val idSeq = AtomicLong(0)

    /** Observable queue. Newest on the end; [ToastHost] renders from top (newest first). */
    val toasts = mutableStateListOf<Toast>()

    fun show(message: String, level: ToastLevel = ToastLevel.INFO) {
        // Collapse duplicate messages so a flurry of failures doesn't
        // spam-stack the screen with the same line.
        val existing = toasts.lastOrNull()
        if (existing != null && existing.message == message && existing.level == level) {
            return
        }
        toasts.add(Toast(id = idSeq.incrementAndGet(), message = message, level = level))
    }

    fun dismiss(id: Long) {
        toasts.removeAll { it.id == id }
    }
}

@Composable
fun rememberToaster(): Toaster = remember { Toaster() }
