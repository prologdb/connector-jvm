package com.github.prologdb.connector

import java.net.SocketAddress
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousSocketChannel
/**
 * Describes an endpoint to which the client can connect.
 */
interface Endpoint {
    fun openNewConnection(): AsynchronousByteChannel
}

/**
 * Directly connects to a host, unencrypted.
 */
data class DirectEndpoint(
    val address: SocketAddress
) : Endpoint {
    override fun openNewConnection(): AsynchronousByteChannel {
        val channel = AsynchronousSocketChannel.open()
        channel.connect(address)
        return channel
    }
}