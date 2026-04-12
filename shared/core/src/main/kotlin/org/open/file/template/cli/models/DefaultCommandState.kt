package org.open.file.template.cli.models

class DefaultCommandState(success: Boolean, result: Any = mutableMapOf<String, Any>()) : CommandState<Any>(success, result) {

    companion object {

        fun <T: Any> fromCommandState(commandState: CommandState<T>): DefaultCommandState {
            return DefaultCommandState(
                commandState.success,
                commandState.data ?: mutableMapOf<String, Any>()
            )
        }

    }

}