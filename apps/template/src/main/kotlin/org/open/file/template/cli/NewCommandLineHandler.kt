package org.open.file.template.cli

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.open.file.template.App
import org.open.file.template.cli.models.CommandState
import org.open.file.template.cli.models.DefaultCommandState
import org.open.file.template.models.TemplateData
import org.open.file.template.models.TemplateFactory
import org.open.file.template.models.directory.DirectoryTemplate
import org.open.file.utils.filterNotBlank

class NewCommandLineHandler(override var app: App, vararg args : String) : CommandLineHandler<App>() {

    override val parent: Class<*>
        get() = CommandLineHandler::class.java

    override val utilityName: String
        get() = "Templates > New"

    private val args = args.filterNotBlank()

    override val options: Options
        get() = Options().apply {
            addRequiredOption("n", "name", true, "*Template name.")
            addOption("d", "description", true, "Template description.")
            addRequiredOption("t", "type", true, "*Template type.")
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
        val name = requireNotNull(cmd.name ?: args.getOrNull(0)) {
            "Template name was null!"
        }
        val alias = requireNotNull(cmd.type ?: args.getOrNull(1)) {
            "Template type was null!"
        }
        // get actual type based on allowed aliases
        val type = requireNotNull(TemplateFactory.getTypeForAlias(alias)) {
            "Template type was null!"
        }

        val description = cmd.description ?: args.getOrNull(2) ?: ""

        val data = TemplateData(
            type = type,
            name = name,
            description = description
        )

        return when (type) {
            DirectoryTemplate.TYPE -> {
                NewDirectoryTemplateCommandLineHandler(
                    app,
                    data
                )
                    .interactive()
                    .apply { mutation = true }
            }
            else -> {
                DefaultCommandState(false, data)
            }
        }
    }

}