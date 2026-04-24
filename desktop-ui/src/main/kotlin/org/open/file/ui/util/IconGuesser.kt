package org.open.file.ui.util

/**
 * Best-guess template icon from a free-form name / description.
 *
 * Kept deliberately lightweight: a single linear scan over keyword
 * buckets, first match wins. The ordering matters — more-specific
 * keywords come before broader ones so e.g. "Kotlin/Ktor backend" lands
 * on the Ktor icon rather than generic Kotlin.
 *
 * Consumers typically wire this to `derivedStateOf { guessTemplateIcon(...) }`
 * in a form so the icon updates live as the user types. If the user
 * explicitly picks an icon from the dropdown, that choice should win
 * over the guess — track "user overrode it" state at the call site.
 *
 * Returns one of the [org.open.file.ui.components.TEMPLATE_ICON_KEYS]
 * strings, falling back to `"generic"` when nothing matches.
 */
fun guessTemplateIcon(name: String, description: String = ""): String {
    val hay = (name + " " + description).lowercase()

    // Word-boundary matcher so "goblin" doesn't match "go" etc.
    fun matches(vararg needles: String): Boolean =
        needles.any { needle ->
            if (needle.contains(' ') || needle.contains('-') || needle.contains('.')) {
                hay.contains(needle)
            } else {
                Regex("(?<![a-z0-9])${Regex.escape(needle)}(?![a-z0-9])").containsMatchIn(hay)
            }
        }

    return when {
        // Framework-first — they're more specific than the language.
        matches("ktor") -> "ktor"
        matches("spring", "spring-boot", "springboot") -> "spring"
        matches("react", "next.js", "nextjs", "next js", "jsx", "tsx", "vite-react") -> "react"
        matches("docker", "dockerfile", "docker-compose", "compose") -> "docker"

        // Languages.
        matches("kotlin", "kts", "gradle-kotlin") -> "kotlin"
        matches("swift", "swiftui", "xcode", "ios", "macos") -> "swift"
        matches("rust", "cargo", "rustlang") -> "rust"
        matches("golang", "go", "gopher") -> "go"
        matches("python", "django", "flask", "fastapi", "pyproject") -> "python"
        matches("node", "nodejs", "node.js", "npm", "pnpm", "yarn", "express", "nestjs") -> "node"
        matches("js", "javascript") -> "node"
        matches("ts", "typescript") -> "node"

        else -> "generic"
    }
}
