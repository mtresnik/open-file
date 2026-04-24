package org.open.file.ui.data

import androidx.compose.ui.graphics.Color
import org.open.file.ui.theme.AccentChoice
import org.open.file.ui.theme.AppFonts
import org.open.file.ui.theme.AppPalette
import org.open.file.ui.theme.FontChoice
import org.open.file.ui.theme.applyAccent
import org.open.file.ui.theme.builtInAccents
import org.open.file.ui.theme.builtInPalettes
import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * UI-only theme selection store. Persists the user's chosen palette name
 * and accent name at `~/.open-file/ui/theme.properties`:
 *
 *   palette=Midnight
 *   accent=Cyan
 *
 * Values are stored by name (not hex) so a palette rename in code is a
 * no-op migration — we just fall back to the default when the name doesn't
 * resolve.
 *
 * This is a thin store; it doesn't apply colours itself, it just reads and
 * writes two strings. [loadAndApply] is the convenience that wires them
 * through to [AppPalette.apply] / [applyAccent].
 */
class ThemePreferences(
    private val prefsFile: File = FileSystemUtils.home("ui/theme.properties"),
) {
    private val lock = Any()

    fun loadPaletteName(): String? = synchronized(lock) { load().getProperty(PALETTE_KEY) }
    fun loadAccentName(): String? = synchronized(lock) { load().getProperty(ACCENT_KEY) }
    fun loadFontChoice(): FontChoice = synchronized(lock) {
        FontChoice.byKey(load().getProperty(FONT_KEY))
    }

    fun setPalette(name: String) = synchronized(lock) {
        val props = load()
        props.setProperty(PALETTE_KEY, name)
        save(props)
    }

    fun setAccent(name: String) = synchronized(lock) {
        val props = load()
        props.setProperty(ACCENT_KEY, name)
        save(props)
    }

    /** Persist + live-apply the user's font pick. */
    fun setFont(choice: FontChoice) = synchronized(lock) {
        val props = load()
        props.setProperty(FONT_KEY, choice.storageKey)
        save(props)
        AppFonts.primary = choice.family
    }

    /**
     * Apply the persisted palette + accent to [AppColors] on startup.
     * Unknown / missing names fall back to the first built-in palette
     * (Midnight) and that palette's own accent, so the UI always lands in
     * a usable state.
     */
    fun loadAndApply() {
        val paletteName = loadPaletteName()
        val accentName = loadAccentName()

        val palette = builtInPalettes.firstOrNull { it.name == paletteName } ?: builtInPalettes.first()
        palette.apply()

        val accent = builtInAccents.firstOrNull { it.name == accentName }
        if (accent != null) {
            applyAccent(accent.accent, accent.light)
        }
        // If no accent saved, AppPalette.apply already set the palette's
        // own default — no second call needed.

        // Same lifecycle for the font — write directly to AppFonts so
        // the first paint uses the user's pick (no flash of default).
        AppFonts.primary = FontChoice.byKey(load().getProperty(FONT_KEY)).family
    }

    private fun load(): Properties {
        val props = Properties()
        if (prefsFile.exists()) prefsFile.inputStream().use { props.load(it) }
        return props
    }

    private fun save(props: Properties) {
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file UI theme") }
    }

    companion object {
        private const val PALETTE_KEY = "palette"
        private const val ACCENT_KEY = "accent"
        private const val FONT_KEY = "font"
    }
}

/** Utility — find a built-in palette by name, defaulting to the first. */
fun paletteByName(name: String?): AppPalette =
    builtInPalettes.firstOrNull { it.name == name } ?: builtInPalettes.first()

/** Utility — find a built-in accent by name, or null for "use palette default". */
fun accentByName(name: String?): AccentChoice? =
    builtInAccents.firstOrNull { it.name == name }

/** For the dialog preview: flatten an accent choice to a plain Color. */
fun AccentChoice.toAccent(): Color = this.accent
