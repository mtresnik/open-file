/**
 * Single source of truth for the project version. Resolved in this
 * order:
 *
 *   1. `RELEASE_VERSION` env var — set by the release CI workflow
 *      from the pushed git tag (e.g. `v0.0.1` → `0.0.1`). Takes
 *      precedence over every other rule so explicit overrides
 *      always win.
 *
 *   2. Develop-branch snapshot. If the current branch is `develop`
 *      (checked via `GITHUB_REF_NAME` in CI, `git rev-parse
 *      --abbrev-ref HEAD` locally), take the last `v*` semver tag
 *      and bump its minor component, then suffix `-SNAPSHOT`.
 *      Example: last tag `v0.0.1` → develop builds report
 *      `0.1.0-SNAPSHOT`. No tags yet? Start from `0.1.0-SNAPSHOT`.
 *
 *   3. `git describe --tags --always --dirty=-SNAPSHOT` — for other
 *      local / feature-branch builds, gives something like
 *      `0.0.1-3-g0deadbe` on a commit past the tag.
 *
 *   4. `0.0.0-SNAPSHOT` — fallback when there's no git available
 *      (source tarballs, some CI contexts).
 *
 * The leading `v` on tags is stripped so downstream consumers get
 * bare semver (e.g. the MSI `packageVersion` attribute, which
 * rejects `v`-prefixed values).
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
 * Current branch name. Prefers `GITHUB_REF_NAME` (set by GitHub
 * Actions on push events), falls back to `git rev-parse`. Returns
 * null in detached-HEAD contexts where neither works.
 */
private fun currentBranch(): String? {
    System.getenv("GITHUB_REF_NAME")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return runGit("rev-parse", "--abbrev-ref", "HEAD")
        ?.takeUnless { it == "HEAD" }  // detached HEAD
}

/**
 * The most recent `v<major>.<minor>.<patch>` tag reachable from
 * the current commit, stripped of its leading `v`. Returns null if
 * the repo has no semver tags yet.
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
 * Increment the minor of a bare `major.minor.patch` string, reset
 * patch to 0. `0.0.1` → `0.1.0`, `1.4.7` → `1.5.0`. Falls back to
 * `0.1.0` if the input doesn't parse.
 */
private fun bumpMinor(version: String): String {
    val parts = version.split('.')
    if (parts.size < 3) return "0.1.0"
    val major = parts[0].toIntOrNull() ?: return "0.1.0"
    val minor = parts[1].toIntOrNull() ?: return "0.1.0"
    return "$major.${minor + 1}.0"
}

/**
 * `git describe --tags --always --dirty=-SNAPSHOT` with leading-v
 * stripping. Returns null when git fails or the output is empty.
 */
private fun gitDescribe(): String? =
    runGit("describe", "--tags", "--always", "--dirty=-SNAPSHOT")
        ?.removePrefix("v")

/**
 * Run `git` with [args] and return trimmed stdout, or null if the
 * process fails or prints nothing.
 */
private fun runGit(vararg args: String): String? = runCatching {
    val process = ProcessBuilder(listOf("git") + args.toList())
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    if (process.waitFor() == 0 && output.isNotBlank()) output else null
}.getOrNull()
