package se.strawberry.support.ports

import java.net.ServerSocket

object PortManager {
    fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

    fun findFreePorts(count: Int): List<Int> = (0 until count).map { findFreePort() }
}
