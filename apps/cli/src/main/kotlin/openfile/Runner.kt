package openfile

import org.open.file.cli.CommandLineHandler

/**
 * Parse [args] with [handler]; drop into an interactive REPL
 * when there are none or they fail to parse. Used by every
 * domain's `runXCli(args)` wrapper.
 */
fun <A : Any> runCli(handler: CommandLineHandler<A>, args: Array<String>) {
    val cmd = handler.parse(args)
    if (args.isEmpty() || cmd == null) {
        while (true) handler.interactive()
    }
    handler.handle(cmd)
}
