package org.open.file.ui.util

import java.io.File

/**
 * Outcome of validating a user-entered directory path.
 *
 *  - [Empty]: blank field — don't render an error yet, but treat creation
 *    as blocked so users aren't scolded before they've typed.
 *  - [Invalid]: the path is set but not usable. [reason] is surfaced inline.
 *  - [Valid]: the path exists on disk and is a readable directory.
 */
sealed class PathValidation {
    object Empty : PathValidation()
    data class Invalid(val reason: String) : PathValidation()
    object Valid : PathValidation()
}

/**
 * Check that [raw] (after trim) resolves to a readable directory on disk.
 *
 * Each failure mode returns [PathValidation.Invalid] with a specific reason
 * so the UI can surface exactly what's wrong ("Path does not exist",
 * "Path is not a directory", "Directory is not readable") instead of a
 * generic "invalid path" message.
 *
 * The File#exists / #isDirectory / #canRead calls only touch metadata, so
 * this is cheap enough to run synchronously on each keystroke for a modal
 * dialog on a local filesystem.
 */
fun validateDirectory(raw: String): PathValidation {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return PathValidation.Empty
    val file = File(trimmed)
    if (!file.exists()) return PathValidation.Invalid("Path does not exist")
    if (!file.isDirectory) return PathValidation.Invalid("Path is not a directory")
    if (!file.canRead()) return PathValidation.Invalid("Directory is not readable")
    return PathValidation.Valid
}
