package org.open.file.cli.models

class ErrorCommandState(val exception: Exception? = null) : CommandState<Any>(success = exception == null, exception)