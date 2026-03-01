package org.open.file.template.utils

object EnvironmentUtils {

    val appHome: String? by lazy { System.getProperties()[ENV_HOME_KEY]?.toString() }

}