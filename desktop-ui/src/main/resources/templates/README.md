# Bundled template zips

Drop pre-packaged template zips in this directory. They get bundled
into the desktop-ui jar and extracted by `zipScaffold(...)` at scaffold
time, so any template that would be tedious to hand-code — full
framework starters, projects with binary assets, vendored SDK layouts
— can ship as a zip instead of a `scaffold = { ... }` lambda.

## Adding a new zip template

1. Zip the starter project. Keep it flat at the top level (no wrapping
   `<name>/` folder inside the zip — the repo wraps the output dir for
   you, so zip contents land at `<output>/<projectName>/*`):
   ```
   my-template.zip
   ├── build.gradle.kts
   ├── src/
   │   └── main/kotlin/Main.kt
   └── README.md
   ```

2. Drop the file here, named `<id>.zip` where `<id>` matches your
   `PackagedTemplate.id` suffix:
   ```
   desktop-ui/src/main/resources/templates/my-template.zip
   ```

3. Register the template in `PackagedTemplates.kt` with
   `scaffold = zipScaffold("/templates/my-template.zip")`:
   ```kotlin
   PackagedTemplate(
       id = "${PACKAGED_ID_PREFIX}my-template",
       name = "My Template",
       description = "...",
       icon = "generic",
       tags = listOf("..."),
       tools = listOf(T.NODE),
       requiredTools = listOf(T.NODE),
       scaffold = zipScaffold("/templates/my-template.zip"),
   )
   ```

## Placeholder substitution

`zipScaffold` rewrites these tokens inside text files during
extraction:

- `{{PROJECT_NAME}}` — the exact project name the user typed.
- `{{PROJECT_NAME_SAFE}}` — lower-cased + non-alphanumerics replaced
  with `-`, safe to drop into package names, module ids, etc.

Bake them into your zipped files wherever the user's name should
appear (build.gradle.kts `rootProject.name`, Cargo.toml `name`,
go.mod module path, package.json `"name"`, …).

Pass `substitute = false` to `zipScaffold(...)` when the bundled
template is already generic and shouldn't be rewritten.

## Why not a `scaffold = { ... }` lambda?

Use the lambda form when:
- Files are tiny and generating them in code is clearer than
  shipping a separate zip.
- You need logic (version-gated file writes, shell-out to
  `gradle init`, conditional sections per tool version).

Use `zipScaffold(...)` when:
- Many files (>10 or so) make the lambda unreadable.
- The template includes binary assets (images, fonts) the lambda
  can't embed cleanly.
- You want users / contributors to edit the template by working in a
  real project, then re-zipping — no Kotlin code changes required.

## Shared code with the backup tab

The extractor is the exact `BackupExtractor` the Restore Backup flow
calls. Same zip-slip guards, same empty-dir handling, same entry-path
separator logic. One implementation, two call sites — any fix for a
weird archive there improves scaffolding here.
