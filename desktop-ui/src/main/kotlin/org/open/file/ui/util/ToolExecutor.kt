package org.open.file.ui.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around `ProcessBuilder` for running CLI tools on the user's
 * PATH — `gradle init`, `cargo init`, `npm create vite`, etc.
 *
 * Every call is non-interactive: stdin is inherited default (empty), and
 * we redirect stderr into stdout so callers get the full output in one
 * buffer for inclusion in toast / log messages.
 *
 * Timeout is capped so a misbehaving init command can't wedge the UI
 * thread (callers are expected to be on IO anyway).
 */
object ToolExecutor {

    data class Result(
        val success: Boolean,
        val exitCode: Int,
        /** Merged stdout+stderr. First few lines are surfaced on failure. */
        val output: String,
    )

    /**
     * Run [command] with [workingDir] as the CWD. The first argument is
     * the executable, looked up via PATH.
     */
    fun run(
        command: List<String>,
        workingDir: File,
        timeoutSeconds: Long = 120,
    ): Result {
        return try {
            // Same Windows caveat as VersionDetector — bare command
            // names like "gradle" or "npm" are actually .bat / .cmd
            // scripts that ProcessBuilder won't resolve without the
            // extension. Route through `cmd /c` on Windows so the
            // OS's native PATH resolver handles it.
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val resolvedCommand = if (os.contains("win")) listOf("cmd", "/c") + command else command
            val pb = ProcessBuilder(resolvedCommand)
                .directory(workingDir)
                .redirectErrorStream(true)
            val process = pb.start()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                Result(false, -1, "Timed out after ${timeoutSeconds}s running: ${command.joinToString(" ")}")
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                Result(
                    success = process.exitValue() == 0,
                    exitCode = process.exitValue(),
                    output = output,
                )
            }
        } catch (t: Throwable) {
            // Tool not on PATH, permission denied, etc. — surface the
            // reason so the toast reads usefully rather than a bare
            // exception class name.
            Result(false, -1, "Failed to run ${command.firstOrNull()}: ${t.message ?: t.javaClass.simpleName}")
        }
    }
}
