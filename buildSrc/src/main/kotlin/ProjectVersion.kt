/**
 * Single source of truth for the project version. Resolved in this
 * order:
 *
 *   1. `RELEASE_VERSION` env var — set by the release CI workflow
 *      from the pushed git tag (e.g. `v0.0.1` → `0.0.1`). Wins over
 *      every other rule so explicit overrides always apply.
 *
 *   2. Develop-branch snapshot. If the current branch is `develop`,
 *      take the last `v*` semver tag and bump its minor, then
 *      suffix `-SNAPSHOT`. No tags yet → start from `0.1.0-SNAPSHOT`.
 *      Example: last tag `v0.0.1` → develop reports `0.1.0-SNAPSHOT`.
 *
 *   3. `git describe --tags --always --dirty=-SNAPSHOT` — for
 *      other local / feature-branch builds. Returns something like
 *      `0.0.1-3-g0deadbe` on a commit past the tag.
 *
 *   4. `0.0.0-SNAPSHOT` (or `0.0.0-SNAPSHOT-<sha>` if we have a
 *      commit hash) — fallback when there's no git history yet
 *      (fresh repo, no tags, or the build is running outside a
 *      working tree). Crucially this is what you get *before*
 *      cutting your first tag.
 *
 * Branch detection prefers `GITHUB_REF_NAME` (set by GitHub Actions
 * on push events) and falls back to `git rev-parse --abbrev-ref
 * HEAD` for local builds. Detached-HEAD checkouts return null and
 * fall through to `git describe`.
 */
fun projectVersion(): String {
    System.getenv("RELEASE_VERSION")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it.removePrefix("v") }

    if (currentBranch() == "develop") {
        val base = lastSemverTag() ?: "0.0.0"
        return bumpMinor(base) + "-SNAPSHOT"
    }

    gitDescribe()?.let { return it }

    return "0.0.0-SNAPSHOT"
}

/**
 * Current branch name from CI env var or `git rev-parse`. Returns
 * null in detached-HEAD contexts.
 */
private fun currentBranch(): String? {
    System.getenv("GITHUB_REF_NAME")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return runGit("rev-parse", "--abbrev-ref", "HEAD")
        ?.takeUnless { it == "HEAD" }
}

/**
 * The most recent `v<major>.<minor>.<patch>` tag reachable from
 * the current commit, stripped of its leading `v`. Returns null if
 * the repo has no semver tags yet — which is the expected case for
 * a project that hasn't cut its first release.
 */
private fun lastSemverTag(): String? {
    val raw = runGit("tag", "--list", "v[0-9]*.[0-9]*.[0-9]*", "--sort=-v:refname")
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.matches(Regex("""v\d+\.\d+\.\d+""")) }
        ?: return null
    return raw.removePrefix("v")
}

/**
 * Increment the minor of a bare `major.minor.patch` string and
 * reset patch to 0. `0.0.1` → `0.1.0`, `1.4.7` → `1.5.0`. Falls
 * back to `0.1.0` if the input doesn't parse.
 */
private fun bumpMinor(version: String): String {
    val parts = version.split('.')
    if (parts.size < 3) return "0.1.0"
    val major = parts[0].toIntOrNull() ?: return "0.1.0"
    val minor = parts[1].toIntOrNull() ?: return "0.1.0"
    return "$major.${minor + 1}.0"
}

/**
 * `git describe --tags --always --dirty=-SNAPSHOT`. With no tags
 * present, `--always` makes git fall back to the abbreviated
 * commit SHA — useful as an identifier but not a valid semver. We
 * detect that pre-tag case and produce `0.0.0-SNAPSHOT-<sha>`
 * instead so downstream consumers (Gradle module versions, MSI
 * sanitizer, etc.) see something parseable.
 */
private fun gitDescribe(): String? {
    val output = runGit("describe", "--tags", "--always", "--dirty=-SNAPSHOT")
        ?: return null
    val stripped = output.removePrefix("v")

    // Pre-first-tag world: `git describe --always` returns just a
    // commit SHA (no dots, optional `-SNAPSHOT` suffix from
    // --dirty). Wrap it in a proper SNAPSHOT version so callers
    // never see a bare hash as a version.
    val withoutDirtyMarker = stripped.removeSuffix("-SNAPSHOT")
    if (!withoutDirtyMarker.contains('.')) {
        return "0.0.0-SNAPSHOT-$withoutDirtyMarker"
    }
    return stripped
}

/** Run `git <args>`; return trimmed stdout, or null if it fails. */
private fun runGit(vararg args: String): String? = runCatching {
    val process = ProcessBuilder(listOf("git") + args.toList())
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    if (process.waitFor() == 0 && output.isNotBlank()) output else null
}.getOrNull()
