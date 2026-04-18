package org.open.file.cli.models

abstract class CommandState<T>(val success: Boolean, val data: T?, var mutation: Boolean = false) {

    val isError: Boolean
        get() = this is ErrorCommandState

}