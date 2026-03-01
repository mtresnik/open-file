package org.open.file.template.cli

import org.apache.commons.cli.*
import org.open.file.template.cli.models.CommandState
import org.open.file.template.cli.models.DefaultCommandState
import org.open.file.template.utils.ConsoleUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.thread
import kotlin.system.exitProcess

abstract class CommandLineHandler<APP : Any> {

    protected open lateinit var app: APP
    protected abstract val options: Options

    protected open val parser: CommandLineParser = DefaultParser()
    protected open val formatter = HelpFormatter()
    protected val logger: Logger = requireNotNull(LoggerFactory.getLogger(javaClass)) { "Logger was null" }
    protected open val utilityName = "Templates"
    protected open val parent: Class<*> = Any::class.java

    open fun handle(cmd: CommandLine): CommandState<Any> {
        if (cmd.quit) {
            ConsoleUtils.printExit()
            exitProcess(0)
            // return DefaultCommandState(true)
        }

        // meta opts short-circuit execution
        if (cmd.help) {
            val formatter = HelpFormatter()
            formatter.printHelp(utilityName, options)
            return DefaultCommandState(true)
        }
        return DefaultCommandState(false)
    }

    protected open fun printHelp(vararg extras: String) {
        fun _printHelp() {
            ConsoleUtils.clear()
            formatter.printHelp(utilityName, options)
            extras.onEach(::println)
        }
        if (extras.isNotEmpty() || parent != Any::class.java) {
            thread(start = true) {
                Thread.sleep(100L)
                _printHelp()
            }
        } else {
            _printHelp()
        }

    }

    fun parse(args: Array<String>): CommandLine? = runCatching { parser.parse(options, args) }.onFailure {
            logger.error("Error parsing command with args: {}", args)
        }.getOrNull()

    fun interactive(): CommandState<Any> {
        ConsoleUtils.clear()
        val scanner = Scanner(System.`in`)
        printHelp()
        while (true) {
            try {
                val line = scanner.nextLine()
                val args = line.split(" ").toTypedArray()
                if (".." in args) {
                    return DefaultCommandState(
                        false
                    )
                }
                if (listOf("?", "help", "--help", "-h").any { it in args }) {
                    printHelp()
                    continue
                }
                val cmd: CommandLine = parser.parse(options, args)
                if (cmd.quit || listOf("--quit", "-q", "quit").any { it in args }) {
                    println("Quitting...")
                    exitProcess(0)
                }
                val res = handle(cmd)
                if (res.mutation && res.success) {
                    return res
                }
                if (!res.success) {
                    printHelp()
                }
            } catch (_: InterruptedException) {
                return DefaultCommandState(false)
            } catch (missing: MissingOptionException) {
                logger.error(missing.message)
            } catch (missing: MissingArgumentException) {
                logger.error(missing.message)
            } catch (ex : Exception) {
                ex.printStackTrace()
            }
        }
    }

}