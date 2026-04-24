package org.open.file.ui.notifications

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Central funnel for user-facing errors. Every catch site in the UI layer
 * should go through [report] / [reportError] rather than `System.err.println`
 * so users actually see what went wrong (as a toast) and developers have a
 * persistent breadcrumb trail (on disk) to walk back from a bug report.
 *
 * Log file: `~/.open-file/logs/error.log`, plain-text append-only. Rotation
 * is intentionally absent — the log is scoped to a desktop tool and stays
 * small; if it ever becomes a problem we can add size-based rotation
 * without changing the caller contract.
 *
 * Thread-safety: the JDK's `PrintWriter(append=true)` is itself synchronised
 * on its lock, and we wrap each write in our own `synchronized` block so
 * multi-line stack traces don't interleave between concurrent reports.
 */
class ErrorReporter(
    private val toaster: Toaster,
    private val logFile: File = FileSystemUtils.home("logs/error.log"),
) {
    private val writeLock = Any()

    init {
        runCatching { logFile.parentFile?.mkdirs() }
    }

    /**
     * Show [message] at the given toast [level] and, for errors, append to
     * the log. INFO/WARNING toasts don't hit the log — they're transient UI
     * state, not failures.
     */
    fun report(message: String, level: ToastLevel = ToastLevel.ERROR) {
        toaster.show(message, level)
        if (level == ToastLevel.ERROR) {
            appendToLog(context = message, throwable = null)
        }
    }

    /**
     * Preferred entry point from `catch` blocks. [context] is the verb the
     * user would use to describe what they tried ("create backup"), not the
     * exception class. The toast reads "<context>: <message>"; the log
     * entry has the full stack trace for debugging.
     */
    fun reportError(context: String, throwable: Throwable) {
        val detail = throwable.message?.takeIf { it.isNotBlank() } ?: throwable.javaClass.simpleName
        val userMessage = "$context: $detail"
        toaster.show(userMessage, ToastLevel.ERROR)
        appendToLog(context = context, throwable = throwable)
    }

    private fun appendToLog(context: String, throwable: Throwable?) {
        val stamp = Instant.now().toString()
        val entry = buildString {
            append("[").append(stamp).append("] ").append(context)
            if (throwable != null) {
                append('\n')
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString())
            }
            if (!endsWith('\n')) append('\n')
        }
        synchronized(writeLock) {
            runCatching {
                logFile.parentFile?.mkdirs()
                // Append = true so entries accumulate across sessions.
                // Opened / closed per write rather than holding a long-lived
                // handle, which would also defeat external log viewers that
                // tail the file.
                PrintWriter(java.io.FileOutputStream(logFile, true)).use {
                    it.print(entry)
                }
            }.onFailure {
                // If we can't write the log, don't fail the UI — the toast
                // has already surfaced the error.
                System.err.println("ErrorReporter: failed to append to log: ${it.message}")
            }
        }
    }
}
