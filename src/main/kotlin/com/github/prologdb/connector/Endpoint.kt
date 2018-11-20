package com.github.prologdb.connector

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.io.IOException

/**
 * Represents an open connection to an endpoint, very similar
 * to [java.net.Socket].
 */
interface EndpointConnection {
    val inputStream: InputStream
    val outputStream: OutputStream

    /**
     * Whether the endpoint is definitiely closed. Note that the
     * underlying connection may be stale / in an undefined state while
     * this method still returns false.
     */
    val closed: Boolean

    /**
     * Closes the connection
     * @throws IOException
     */
    fun close()
}

/**
 * Describes an endpoint to which the client can connect.
 */
interface Endpoint {
    fun openNewConnection(): EndpointConnection
}

/**
 * Directly connects to a host, unencrypted.
 */
data class DirectEndpoint(
    val host: InetAddress,
    val port: Int = 30001
) : Endpoint {
    init {
        if (port < 1 || port > 65535) {
            throw IllegalArgumentException("Port must be in range [1 ; 65535]")
        }
    }

    override fun openNewConnection(): EndpointConnection {
        return DirectEndpointConnection(this)
    }
}

private class DirectEndpointConnection(
    val endpoint: DirectEndpoint
) : EndpointConnection {
    private val socket = Socket(endpoint.host, endpoint.port)

    override val inputStream: InputStream
        get() = socket.getInputStream()

    override val outputStream: OutputStream
        get() = socket.getOutputStream()

    override val closed: Boolean
        get() = socket.isClosed

    override fun close() {
        socket.close()
    }
}