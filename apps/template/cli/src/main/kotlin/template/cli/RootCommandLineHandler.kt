package template.cli

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.open.file.cli.CommandLineHandler
import org.open.file.cli.list
import template.App
import org.open.file.cli.models.DefaultCommandState
import org.open.file.cli.models.DefaultCommandState.Companion.fromCommandState
import org.open.file.cli.new
import org.open.file.cli.types

class RootCommandLineHandler : CommandLineHandler<App>() {

    override val options: Options
        get() = Options().apply {
            addOption("n", "new", false, "Create a new template.")
            addOption("ls", "list", false, "List all templates")
            addOption("t", "types", false, "List all template types")
            addOption("h", "help", false, "Shows the help menu")
            addOption("q", "quit", false, "Quits the application.")
        }

    override fun handle(cmd: CommandLine): DefaultCommandState {
        val result = super.handle(cmd)
        if (result.success) {
            return fromCommandState(result)
        }

        val app = App()

        if (cmd.list) {
            logger.info("Listing all templates...")
            val result = app.list()
            return DefaultCommandState(
                true,
                result
            )
        }

        if (cmd.types) {
            logger.debug("Listing all types...")
            val result = app.listTypes()
            return DefaultCommandState(
                true,
                result
            )
        }

        if (cmd.new) {
            return fromCommandState(
                NewCommandLineHandler(
                    app
                ).interactive()).apply { mutation = true }
        }
        return DefaultCommandState(false)
    }

}