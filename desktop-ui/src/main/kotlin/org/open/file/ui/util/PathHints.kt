package org.open.file.ui.util

import java.io.File

/**
 * Runtime-resolved example paths used as text-field placeholders.
 *
 * Every path is built from `System.getProperty("user.home")` and
 * [java.io.File.separator], so the hints always use the host OS's
 * native form:
 *
 *   Windows : C:\Users\alice\Documents\my-project
 *   macOS   : /Users/alice/Documents/my-project
 *   Linux   : /home/alice/Documents/my-project
 *
 * These are hints only — never seeded into a text field as the actual
 * value. Seeding caused confusion when the hardcoded Linux-style path
 * appeared on Windows and didn't match anything on disk.
 */
object PathHints {
    private val home: String = System.getProperty("user.home").orEmpty()
    private val sep: String = File.separator

    /** `~` expansion on the host OS. C:\Users\alice / /Users/alice / /home/alice */
    val userHome: String get() = home

    /** Documents directory — the "where do new projects live" root. */
    val documentsDir: String get() = "$home${sep}Documents"

    /** Placeholder for a source-project / source-template directory. */
    val exampleProjectPath: String get() = "$home${sep}Documents${sep}my-project"

    /** Placeholder for a backup archive's source directory — same shape as a project path. */
    val exampleBackupSource: String get() = "$home${sep}Documents${sep}my-project"

    /** Placeholder for a password-file path. Non-existent by design; just a shape hint. */
    val examplePasswordFile: String get() = "$home${sep}.config${sep}restic-password.txt"

    /**
     * Where backup archives go when the user hasn't picked an explicit
     * target directory. Matches `BackupRepository.defaultArchiveDir` so
     * the Create / Schedule dialogs can show the user exactly where the
     * default will land on their OS.
     */
    val defaultBackupTargetDir: String get() = "$home${sep}.open-file${sep}backups"
}
