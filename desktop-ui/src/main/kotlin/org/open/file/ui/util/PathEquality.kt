package org.open.file.ui.util

import java.io.File

/**
 * Cross-OS path equality helpers.
 *
 * Motivation: the same on-disk directory can be written several ways
 * depending on OS and user habit:
 *
 *  - Windows is case-insensitive at the filesystem level, so
 *    `C:\Users\Mike\Documents` and `c:\users\mike\documents` refer
 *    to the same directory. A user typing one form in the Create
 *    Backup dialog and another in the Schedule dialog shouldn't see
 *    them treated as distinct sources.
 *  - Both OS families let paths end with or without a trailing
 *    separator (`/home/user/docs` vs `/home/user/docs/`). Every
 *    comparison in this codebase should be trailing-slash-agnostic.
 *
 * macOS is technically case-preserving but case-insensitive by
 * default (HFS+ / APFS) yet *can* be formatted case-sensitive. We
 * treat it as case-sensitive to match the usual POSIX contract; if
 * a user's drive is case-insensitive they'll still get exact matches
 * when typing the canonical form, and false-negatives (distinct-by-
 * case paths that are the same on disk) are the rarer footgun than
 * false-positives.
 */
object PathEquality {

    private val isWindows: Boolean by lazy {
        System.getProperty("os.name").orEmpty().lowercase().contains("win")
    }

    /**
     * Canonical form used for comparison / grouping / hashing:
     *
     *  - Trimmed of leading/trailing whitespace.
     *  - Trailing `/` and `\` separators removed (except a single
     *    leading `/` on Unix roots and `C:\` on Windows drive
     *    letters, both of which we keep intact).
     *  - Lowercased on Windows, preserved elsewhere.
     *
     * Pure string manipulation — no filesystem access, so it's safe
     * to call on user-typed paths that may not exist yet (e.g. the
     * target directory in Create Backup).
     */
    fun normalize(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return ""

        // Strip trailing separators except when the whole thing is
        // a root: keep `/` on Unix and `C:\` on Windows intact so
        // a root path still round-trips through File(...) correctly.
        var out = trimmed
        while (out.length > 1 &&
            (out.endsWith('/') || out.endsWith('\\')) &&
            !isBareRoot(out)
        ) {
            out = out.dropLast(1)
        }

        return if (isWindows) out.lowercase() else out
    }

    /** True when [path] is `/`, `C:\`, `D:\`, etc. Not `C:\foo`. */
    private fun isBareRoot(path: String): Boolean {
        if (path == "/") return true
        // Windows drive roots: single letter + ':' + trailing sep.
        return path.length == 3 &&
            path[0].isLetter() &&
            path[1] == ':' &&
            (path[2] == '\\' || path[2] == '/')
    }

    /**
     * True when [a] and [b] refer to the same logical path after
     * normalisation. Safe to call with empty strings (returns true
     * only when both are empty).
     */
    fun equal(a: String, b: String): Boolean = normalize(a) == normalize(b)

    /**
     * Nullable variant — null == null is true, null == non-null is false.
     * Useful when comparing optional target directories where both
     * sides might legitimately be null ("use default").
     */
    fun equalNullable(a: String?, b: String?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return equal(a, b)
    }

    /** Convenience for `File` inputs — delegates to [equal] on absolute paths. */
    fun equal(a: File, b: File): Boolean = equal(a.absolutePath, b.absolutePath)
}

/**
 * Extension convenience so call sites read like prose:
 * ```
 * backups.filter { it.rootPath.pathEquals(rootPath) }
 * ```
 * instead of the fully-qualified `PathEquality.equal(a, b)` form.
 */
fun String.pathEquals(other: String): Boolean = PathEquality.equal(this, other)

