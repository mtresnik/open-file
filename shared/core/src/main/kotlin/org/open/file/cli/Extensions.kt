package org.open.file.cli

import org.apache.commons.cli.CommandLine
import org.open.file.utils.contains
import org.open.file.utils.get

val CommandLine.description: String?
    get() = this["description"]

val CommandLine.file: String?
    get() = this["file"]

val CommandLine.help: Boolean
    get() = "help" in this

val CommandLine.list: Boolean
    get() = "list" in this

val CommandLine.new: Boolean
    get() = "new" in this

val CommandLine.name: String?
    get() = this["name"]

val CommandLine.quit: Boolean
    get() = listOf("q", "quit").any { it in this }

val CommandLine.type: String?
    get() = this["type"] ?: this["t"]

val CommandLine.types: Boolean
    get() = "types" in this

/**
 * `--id` / `-i` option accessor. Used by the snapshot and backup
 * CLIs to target a specific row by its UUID — e.g. `snapshot delete
 * --id 48c1…` or `backup restore --id 22f5…`.
 */
val CommandLine.id: String?
    get() = this["id"] ?: this["i"]

/**
 * `--path` / `-p` option accessor. Every CLI that acts on a
 * filesystem location (snapshot a directory, back up a source dir,
 * restore into a destination dir) reads it through this so callers
 * can stay consistent.
 */
val CommandLine.path: String?
    get() = this["path"] ?: this["p"]

/**
 * `--delete` flag accessor — shorthand for CLIs that want a verb-
 * style flag rather than a positional subcommand.
 */
val CommandLine.delete: Boolean
    get() = "delete" in this

/**
 * `--restore` flag accessor — used by the backup CLI to spell out
 * the restore verb without conflicting with commons-cli's built-in
 * short-option namespace.
 */
val CommandLine.restore: Boolean
    get() = "restore" in this

/**
 * `--target` / `-t` option accessor. The backup CLI passes this for
 * the optional target directory (where the archive file is written);
 * distinct from [type] because they're used in different contexts.
 */
val CommandLine.target: String?
    get() = this["target"]