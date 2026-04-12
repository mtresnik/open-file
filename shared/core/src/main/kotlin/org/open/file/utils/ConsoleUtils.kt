package org.open.file.utils

object ConsoleUtils {

    fun printExit(numDots: Int = 3, delay: Long = 300L) {
        print("Quitting")
        repeat(numDots) {
            Thread.sleep(delay)
            print(".")
        }
        clear()
    }

    fun printSeparator(numDashes: Int = 10) {
        repeat(numDashes) {
            print("-")
        }
        println()
    }

    fun clear() {
        print(listOf(Char(33), Char(143)).joinToString(""))
    }

}