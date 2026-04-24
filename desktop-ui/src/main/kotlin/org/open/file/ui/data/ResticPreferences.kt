package org.open.file.ui.data

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * Stores the user's restic repository connection info.
 *
 * File: `~/.open-file/ui/restic.properties`:
 *
 *   repo.path        = /path/to/restic/repo   (or rclone:remote:/path, etc.)
 *   password.file    = /path/to/password.txt  (optional)
 *
 * Deliberately *doesn't* accept a password directly. Restic supports
 * `--password-file <path>` and `RESTIC_PASSWORD_COMMAND <cmd>` for
 * automated access; pointing at a local text file the user already
 * manages is far safer than us storing the password ourselves.
 *
 * A configured state means [repoPath] is non-blank. The password file
 * is optional — a repo initialised without a password (or with the
 * `RESTIC_PASSWORD` environment variable inherited from the user's
 * shell at the time they launched the app) doesn't need one.
 */
class ResticPreferences(
    private val prefsFile: File = FileSystemUtils.home("ui/restic.properties"),
) {
    private val lock = Any()

    data class Config(
        val repoPath: String,
        val passwordFile: String?,
    )

    fun load(): Config = synchronized(lock) {
        val props = readProps()
        Config(
            repoPath = props.getProperty(KEY_REPO).orEmpty(),
            passwordFile = props.getProperty(KEY_PASSWORD_FILE)?.takeIf { it.isNotBlank() },
        )
    }

    /** Convenience — true when [load]'s [Config.repoPath] is set. */
    fun isConfigured(): Boolean = load().repoPath.isNotBlank()

    fun save(repoPath: String, passwordFile: String?) = synchronized(lock) {
        val props = readProps()
        if (repoPath.isBlank()) {
            props.remove(KEY_REPO)
        } else {
            props.setProperty(KEY_REPO, repoPath)
        }
        if (passwordFile.isNullOrBlank()) {
            props.remove(KEY_PASSWORD_FILE)
        } else {
            props.setProperty(KEY_PASSWORD_FILE, passwordFile)
        }
        writeProps(props)
    }

    private fun readProps(): Properties {
        val props = Properties()
        if (prefsFile.exists()) prefsFile.inputStream().use { props.load(it) }
        return props
    }

    private fun writeProps(props: Properties) {
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file restic repository") }
    }

    companion object {
        private const val KEY_REPO = "repo.path"
        private const val KEY_PASSWORD_FILE = "password.file"
    }
}
