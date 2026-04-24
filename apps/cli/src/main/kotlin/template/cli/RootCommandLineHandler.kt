package template.cli

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.open.file.cli.CommandLineHandler
import org.open.file.cli.list
import org.open.file.cli.models.DefaultCommandState
import org.open.file.cli.models.DefaultCommandState.Companion.fromCommandState
import org.open.file.cli.new
import org.open.file.cli.types
import template.App

/**
 * Options for `openfile template <verb>`:
 *   -ls / --list    list every template
 *   -t  / --types   list type usage counts
 *   -n  / --new     drop into the interactive create flow
 */
class RootCommandLineHandler : CommandLineHandler<App>() {

    override val options: Options get() = Options().apply {
        addOption("n", "new", false, "Create a new template.")
        addOption("ls", "list", false, "List all templates")
        addOption("t", "types", false, "List all template types")
        addOption("h", "help", false, "Shows the help menu")
        addOption("q", "quit", false, "Quits the application.")
    }

    override fun handle(cmd: CommandLine): DefaultCommandState {
        val base = super.handle(cmd)
        if (base.success) return fromCommandState(base)

        val app = App()

        if (cmd.list) {
            return DefaultCommandState(true, app.list())
        }
        if (cmd.types) {
            return DefaultCommandState(true, app.listTypes())
        }
        if (cmd.new) {
            return fromCommandState(NewCommandLineHandler(app).interactive())
                .apply { mutation = true }
        }
        return DefaultCommandState(false)
    }
}
