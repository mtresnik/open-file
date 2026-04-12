package org.open.file.utils

import java.net.InetAddress
import java.net.UnknownHostException

fun isLocalhost(host: String): Boolean {
    return try {
        val address = InetAddress.getByName(host)
        address.isLoopbackAddress || address.isAnyLocalAddress
    } catch (e: UnknownHostException) {
        false
    }
}