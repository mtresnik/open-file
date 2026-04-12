package org.open.file.template.cli

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.open.file.template.App
import org.open.file.template.cli.models.CommandState
import org.open.file.template.cli.models.DefaultCommandState
import org.open.file.template.cli.models.ErrorCommandState
import org.open.file.template.models.TemplateData
import org.open.file.template.models.directory.DirectoryTemplateData
import org.open.file.utils.FileSystemUtils
import java.io.IOException

class NewDirectoryTemplateCommandLineHandler(override var app: App, private val args: TemplateData) : CommandLineHandler<App>() {

    override val parent: Class<*>
        get() = NewCommandLineHandler::class.java

    override val utilityName: String
        get() = "Templates > New > Directory"

    override val options: Options
        get() = Options().apply {
            addRequiredOption("f", "file", true, "Template path.")
            addOption("h", "help", false, "Shows the help menu")
            addOption("q", "quit", false, "Quits the application.")
        }

    override fun printHelp(vararg extras: String) {
        val combinedExtras = extras.toMutableSet().apply { add("  ..\t\t\t Go upwards in the command tree") }
        super.printHelp(*combinedExtras.toTypedArray())
    }

    override fun handle(cmd: CommandLine): CommandState<Any> {
        val result = super.handle(cmd)
        if (result.success) {
            return DefaultCommandState.fromCommandState(result)
        }
        val path = cmd.file
            ?: return DefaultCommandState(false)
        val file = FileSystemUtils.inferPath(path)
            ?: return ErrorCommandState(
                IOException("File does not exist at path: $path")
            )
        val data = DirectoryTemplateData.fromTemplateData(args, file).getOrNull() ?: return ErrorCommandState(
            IllegalStateException("Data can't be converted!")
        )

        return try {
            requireNotNull(app.create(data))
            logger.info("Template created!")
            DefaultCommandState(true)
                .apply { mutation = true }
        } catch (ex: Exception) {
            ErrorCommandState(ex)
        }
    }

}