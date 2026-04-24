package openfile

import snapshot.runSnapshotCli
import template.runTemplateCli
import backup.runBackupCli
import kotlin.system.exitProcess

/**
 * `openfile <domain> <args...>` — dispatches to the matching
 * domain's handler and exits with that handler's result.
 *
 * Help + unknown-domain fall through to [printHelp]; an empty
 * argv list prints help and exits 0.
 */
private enum class Domain(
    val key: String,
    val description: String,
    val dispatch: (Array<String>) -> Unit,
) {
    SNAPSHOT("snapshot", "Snapshot directory trees (list / new / delete).", ::runSnapshotCli),
    TEMPLATE("template", "Project templates (list / types / new).", ::runTemplateCli),
    BACKUP("backup", "Create / restore / delete zip-backed backups.", ::runBackupCli);

    companion object {
        fun forKey(key: String): Domain? = entries.firstOrNull { it.key == key }
    }
}

private fun printHelp() {
    println(
        buildString {
            appendLine("openfile — snapshot / backup / template CLI")
            appendLine()
            appendLine("Usage: openfile <domain> [options]")
            appendLine()
            appendLine("Domains:")
            Domain.entries.forEach { appendLine("  ${it.key.padEnd(10)}  ${it.description}") }
            appendLine()
            appendLine("Run `openfile <domain> --help` to see verbs for each domain.")
            appendLine("`openfile <domain>` with no further args drops into an interactive REPL.")
        },
    )
}

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help", "help")) {
        printHelp()
        exitProcess(0)
    }
    val domain = Domain.forKey(args[0]) ?: run {
        System.err.println("Unknown domain: ${args[0]}\n")
        printHelp()
        exitProcess(2)
    }
    domain.dispatch(args.copyOfRange(1, args.size))
    exitProcess(0)
}
