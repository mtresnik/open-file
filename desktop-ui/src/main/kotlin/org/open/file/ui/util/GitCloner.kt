package org.open.file.ui.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Thin shell wrapper around `git clone` for pulling template sources.
 *
 * Avoids JGit / Git libraries on purpose — users who make templates
 * from repos almost certainly have git installed, and requiring it
 * keeps the dependency surface small. The downside is that git must
 * be on `PATH`; the caller surfaces [Result.errorMessage] via the
 * ErrorReporter when [Result.success] is false so users see what went
 * wrong (missing git, bad URL, auth required, etc.).
 *
 * Timeout is intentionally generous (120s) since even moderate repos
 * can take a while over a slow network — but capped so the UI never
 * hangs forever on an unreachable host.
 */
object GitCloner {

    data class Result(
        val success: Boolean,
        val exitCode: Int,
        val output: String,
        /** Human-readable message for toast display on failure. Null on success. */
        val errorMessage: String?,
    )

    /**
     * Clone [url] into [destination]. [destination] must not exist — git
     * refuses to clone into an existing non-empty directory, and we'd
     * rather surface that as a clean failure than silently overwrite.
     *
     * [branch] is optional; when non-null we pass `--branch <branch>`.
     */
    fun clone(url: String, destination: File, branch: String? = null, timeoutSeconds: Long = 120): Result {
        if (destination.exists()) {
            return Result(
                success = false,
                exitCode = -1,
                output = "",
                errorMessage = "Destination already exists: ${destination.absolutePath}",
            )
        }
        destination.parentFile?.mkdirs()

        val args = buildList {
            add("git")
            add("clone")
            add("--depth")
            add("1")
            if (branch != null) {
                add("--branch")
                add(branch)
            }
            add(url)
            add(destination.absolutePath)
        }

        return try {
            val pb = ProcessBuilder(args).redirectErrorStream(true)
            val process = pb.start()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                Result(
                    success = false,
                    exitCode = -1,
                    output = "",
                    errorMessage = "git clone timed out after ${timeoutSeconds}s",
                )
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val code = process.exitValue()
                if (code == 0) {
                    Result(success = true, exitCode = 0, output = output, errorMessage = null)
                } else {
                    Result(
                        success = false,
                        exitCode = code,
                        output = output,
                        // git's last few lines are usually the actual
                        // reason (auth, 404, etc.); surface them.
                        errorMessage = output.lines().filter { it.isNotBlank() }.takeLast(3).joinToString("\n")
                            .ifBlank { "git clone failed with exit code $code" },
                    )
                }
            }
        } catch (t: Throwable) {
            Result(
                success = false,
                exitCode = -1,
                output = "",
                errorMessage = "git not found on PATH or clone failed: ${t.message}",
            )
        }
    }
}
