package org.open.file.utils

import java.io.File

object FileSystemUtils {

    val userHome: File by lazy { File(System.getProperty("user.home")) }

    val temp: File by lazy { File(System.getProperty("java.io.tmpdir")) }

    fun temp(path: String): File = File(temp, path).apply {
        parentFile.mkdirs()
    }

    val workingDirectory: File by lazy { File(System.getProperty("user.dir")) }

    val appHome: File by lazy {
        val envHome = EnvironmentUtils.appHome
        if (envHome != null) {
            val fullFileHome = File(envHome)
            if (fullFileHome.exists() || fullFileHome.parentFile.exists()) {
                return@lazy fullFileHome.apply { mkdirs() }
            }

            val relativeFileHome = File(userHome, envHome)
            if (relativeFileHome.exists() || relativeFileHome.parentFile.exists()) {
                return@lazy relativeFileHome.apply { mkdirs() }
            }
        }
        File(userHome, APP_HOME_NAME).apply { mkdirs() }
    }

    fun userHome(path: String): File {
        requireNotBlank(path)
        return File(userHome, path)
    }

    fun home(path: String): File {
        requireNotBlank(path)
        return File(appHome, path)
    }

    fun inferPath(_path: String): File? {
        val path = if (_path.startsWith("~/")) {
            _path.replace("~", userHome.absolutePath)
        } else {
            _path
        }
        val possibilities = listOf(File(_path), File(path), home(path), userHome(path))
        return possibilities.firstOrNull { file -> file.exists() }
    }

}