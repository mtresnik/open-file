package org.open.file.template.cli.models

class ErrorCommandState(val exception: Exception? = null) : CommandState<Any>(success = exception == null, exception)