package org.open.file.ui.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cross-platform "run at login" integration.
 *
 * Each OS has its own idiomatic mechanism — we prefer the mechanism the
 * user's file manager would show them if they went hunting, so toggling
 * it off in our UI leaves a clean system state:
 *
 *  - **Windows**: `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
 *    registry entry. Visible in Task Manager → Startup and controlled
 *    from Settings → Apps → Startup. Uses the bundled `reg` CLI so we
 *    don't carry a JNI dependency.
 *  - **macOS**: a [LaunchAgent plist] under
 *    `~/Library/LaunchAgents/`. Picked up by `launchd` at login. We
 *    don't `launchctl load` it because macOS loads this directory on
 *    next login regardless, and we want to avoid admin privileges.
 *  - **Linux**: a `.desktop` file under `~/.config/autostart/`, the
 *    XDG autostart convention honoured by every major desktop
 *    environment (GNOME, KDE, XFCE, Cinnamon, etc.).
 *
 * [status] reads the current state; [setEnabled] writes. Both return a
 * [Status] carrying a `supported` flag so the UI can render a disabled
 * toggle with a reason when we can't manage startup on this install —
 * most commonly in dev mode, where [currentExecutablePath] returns the
 * JVM binary and autostarting `/usr/bin/java` would be useless.
 *
 * [LaunchAgent plist]: https://developer.apple.com/library/archive/documentation/MacOSX/Conceptual/BPSystemStartup/Chapters/CreatingLaunchdJobs.html
 */
object AutostartManager {

    enum class Platform { WINDOWS, MAC, LINUX, UNKNOWN }

    /** Detected at class-load time; OS doesn't change at runtime. */
    val platform: Platform by lazy { detectPlatform() }

    /**
     * What the UI needs to render the Run-on-Startup toggle.
     *
     *  - [supported] is false on unknown platforms or when we can't
     *    resolve a real launcher path (e.g. gradle dev run). Render
     *    the toggle disabled and show [reason].
     *  - [enabled] mirrors the OS state. After [setEnabled] this is
     *    the post-write reading, so callers don't need to re-query.
     */
    data class Status(
        val supported: Boolean,
        val enabled: Boolean,
        val reason: String? = null,
    )

    fun status(): Status {
        // [currentExecutablePath] is called purely as a gate — its
        // non-null return isn't used here, but without a real launcher
        // we can't manage startup, so we short-circuit to a
        // "not supported" status with a user-facing reason.
        currentExecutablePath()
            ?: return Status(
                supported = false,
                enabled = false,
                reason = "Run-on-startup is only available for the packaged app (currently running from a JVM launcher).",
            )
        return when (platform) {
            Platform.WINDOWS -> Status(supported = true, enabled = windowsIsEnabled())
            Platform.MAC -> Status(supported = true, enabled = macPlistFile().exists())
            Platform.LINUX -> Status(supported = true, enabled = linuxDesktopFile().exists())
            Platform.UNKNOWN -> Status(
                supported = false,
                enabled = false,
                reason = "Unsupported platform: ${System.getProperty("os.name")}",
            )
        }
    }

    /**
     * Switch the registered-at-login state. Returns the post-write
     * [Status] so callers can diff it against their cached UI flag
     * and surface mismatches (e.g. the registry write failed even
     * though `reg` exited 0).
     */
    fun setEnabled(enabled: Boolean): Status {
        val exe = currentExecutablePath()
            ?: return Status(
                supported = false,
                enabled = false,
                reason = "Can't resolve the app's executable path.",
            )
        return try {
            when (platform) {
                Platform.WINDOWS -> windowsSet(enabled, exe)
                Platform.MAC -> macSet(enabled, exe)
                Platform.LINUX -> linuxSet(enabled, exe)
                Platform.UNKNOWN -> Status(false, false, "Unsupported platform")
            }
        } catch (t: Throwable) {
            Status(supported = true, enabled = false, reason = "Failed to update autostart: ${t.message}")
        }
    }

    // ──────────────────────────────────────────────
    // Platform detection + launcher path
    // ──────────────────────────────────────────────

    private fun detectPlatform(): Platform {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            os.contains("win") -> Platform.WINDOWS
            os.contains("mac") || os.contains("darwin") -> Platform.MAC
            os.contains("nix") || os.contains("nux") || os.contains("bsd") || os.contains("sunos") -> Platform.LINUX
            else -> Platform.UNKNOWN
        }
    }

    /**
     * Best-effort lookup of the executable the OS should run at
     * login. On a packaged [jpackage] build this is the native
     * launcher (e.g. `open-file.exe`, `open-file.app/Contents/MacOS/open-file`);
     * on a gradle dev run it's the JVM itself (`java` / `javaw.exe`),
     * which we reject because autostarting that wouldn't bring the UI up.
     */
    private fun currentExecutablePath(): String? {
        val raw = runCatching { ProcessHandle.current().info().command().orElse(null) }
            .getOrNull() ?: return null
        if (raw.isBlank()) return null
        val file = File(raw)
        val name = file.name.lowercase()
        // jpackage launchers are always named after the app; the JVM
        // itself is one of these standard binaries regardless of OS.
        val looksLikeBareJvm = name == "java" || name == "javaw" ||
            name == "java.exe" || name == "javaw.exe"
        return if (looksLikeBareJvm) null else raw
    }

    // ──────────────────────────────────────────────
    // Windows — HKCU Run key via `reg`
    // ──────────────────────────────────────────────

    private const val WIN_RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val WIN_VALUE_NAME = "open-file"

    private fun windowsIsEnabled(): Boolean {
        val exit = runCmd("reg", "query", WIN_RUN_KEY, "/v", WIN_VALUE_NAME)
        return exit == 0
    }

    private fun windowsSet(enabled: Boolean, exe: String): Status {
        val exit = if (enabled) {
            // Wrap the path in double quotes inside the registry
            // value so the shell handles spaces in e.g.
            // "C:\Program Files\open-file\open-file.exe".
            runCmd(
                "reg", "add", WIN_RUN_KEY,
                "/v", WIN_VALUE_NAME,
                "/t", "REG_SZ",
                "/d", "\"$exe\"",
                "/f",
            )
        } else {
            // Ignore exit code on delete when the value didn't
            // exist — treat "nothing to delete" as success.
            val e = runCmd("reg", "delete", WIN_RUN_KEY, "/v", WIN_VALUE_NAME, "/f")
            if (e != 0 && !windowsIsEnabled()) 0 else e
        }
        return Status(supported = true, enabled = windowsIsEnabled(), reason = if (exit != 0) "reg exit=$exit" else null)
    }

    // ──────────────────────────────────────────────
    // macOS — LaunchAgent plist
    // ──────────────────────────────────────────────

    private const val MAC_LABEL = "org.open-file.openfile"

    private fun macPlistFile(): File = File(
        System.getProperty("user.home"),
        "Library/LaunchAgents/$MAC_LABEL.plist",
    )

    private fun macSet(enabled: Boolean, exe: String): Status {
        val plist = macPlistFile()
        if (enabled) {
            plist.parentFile?.mkdirs()
            plist.writeText(macPlistContent(exe))
        } else if (plist.exists()) {
            plist.delete()
        }
        return Status(supported = true, enabled = plist.exists())
    }

    /**
     * Minimal LaunchAgent — RunAtLoad makes launchd start us at
     * login. KeepAlive is deliberately omitted; we don't want macOS
     * to re-spawn the app when the user quits it.
     *
     * Escapes XML special chars in the path so a username with
     * ampersands doesn't break the parser. Unusual but legal.
     */
    private fun macPlistContent(exe: String): String {
        val safe = exe.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$MAC_LABEL</string>
    <key>ProgramArguments</key>
    <array>
        <string>$safe</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>ProcessType</key>
    <string>Interactive</string>
</dict>
</plist>
"""
    }

    // ──────────────────────────────────────────────
    // Linux — XDG autostart .desktop file
    // ──────────────────────────────────────────────

    private fun linuxDesktopFile(): File = File(
        System.getProperty("user.home"),
        ".config/autostart/open-file.desktop",
    )

    private fun linuxSet(enabled: Boolean, exe: String): Status {
        val f = linuxDesktopFile()
        if (enabled) {
            f.parentFile?.mkdirs()
            f.writeText(linuxDesktopContent(exe))
            // Best-effort chmod — some desktop environments ignore
            // non-executable .desktop files. Errors are harmless
            // (filesystem doesn't support +x, e.g. Windows-formatted
            // drive mounted read-only) so we swallow them.
            runCatching { f.setExecutable(true) }
        } else if (f.exists()) {
            f.delete()
        }
        return Status(supported = true, enabled = f.exists())
    }

    /**
     * XDG autostart .desktop spec — the `Exec` line uses a quoted
     * path so spaces (e.g. in a Flatpak install) don't break. All
     * desktop environments honour `X-GNOME-Autostart-enabled=true`
     * even though its name implies GNOME-only.
     */
    private fun linuxDesktopContent(exe: String): String = """[Desktop Entry]
Type=Application
Name=OpenFile
Comment=OpenFile — runs scheduled backups in the background.
Exec="$exe"
Terminal=false
X-GNOME-Autostart-enabled=true
"""

    // ──────────────────────────────────────────────
    // Process helper
    // ──────────────────────────────────────────────

    /**
     * Run [cmd] with [args], wait up to 5s, return the exit code.
     * `-1` if the process timed out or couldn't launch. Errors don't
     * throw — callers branch on the exit code.
     */
    private fun runCmd(vararg cmd: String): Int {
        return try {
            val p = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            if (p.waitFor(5, TimeUnit.SECONDS)) p.exitValue() else {
                runCatching { p.destroyForcibly() }
                -1
            }
        } catch (_: Throwable) {
            -1
        }
    }
}
