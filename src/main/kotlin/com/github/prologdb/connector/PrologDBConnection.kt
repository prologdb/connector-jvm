package com.github.prologdb.connector

import com.github.prologdb.net.negotiation.ClientHello
import com.github.prologdb.net.negotiation.ToClient
import com.github.prologdb.net.negotiation.ToServer

/**
 * Connection handle for a connection to a prologdb server, implemented for
 * the JVM.
 *
 * This class is **THREAD SAFE**. This is achieved by having a separate
 * worker thread. All methods invoked on the connection object send a
 * message to the worker. The worker keeps all the state and because
 * there is only one worker thread per connection.
 */
interface PrologDBConnection : AutoCloseable {
    /**
     * Executes the given instruction. This method will return almost instantly.
     * The query will be asynchronously submitted to the server and initialized there.
     *
     * All further action regarding the query happens as result of interaction with
     * the returned [QueryHandle]. The thread invoking this function has the possibility
     * to also register the first [QueryEventListener] on the handle, thus assuring not
     * to miss any [QueryEvent] regarding the query started by this invocation.
     */
    @Throws(PrologDBClientException::class)
    fun execute(instruction: PreparedInstruction): QueryHandle

    /**
     * Immediately after this method has been invoked, [execute] will refuse to start new queries.
     * The connection will then be closed.
     */
    override fun close() = close(false)

    /**
     * @see close
     * @param waitForQueriesToComplete If true, blocks until all queries (that were started via [execute] before
     * this function was invoked) have completed before closing the connection. **Use this with caution!** If the queries
     * are not closed or depleted via [QueryHandle.requestSolutions], they will remain open forever and this method
     * will never return.
     */
    fun close(waitForQueriesToComplete: Boolean)

    companion object {
        @JvmStatic
        fun connectTo(endpoint: Endpoint): PrologDBConnection {
            val connection = endpoint.openNewConnection()

            ToServer.newBuilder()
                .setHello(ClientHello.newBuilder().addDesiredProtocolVersion(PROTOCOL_VERSION1_SEMVER).build())
                .build()
                .writeDelimitedTo(connection.outputStream)

            val toClient = ToClient.parseDelimitedFrom(connection.inputStream)
            if (toClient.error.isInitialized) {
                val error = PrologDBClientException(toClient.error.message)
                try {
                    connection.close()
                }
                catch (ex: Throwable) {
                    error.addSuppressed(error)
                }

                throw error
            }

            val serverHello = toClient.hello ?: throw RuntimeException("Server did not send a hello")
            if (serverHello.chosenProtocolVersion != PROTOCOL_VERSION1_SEMVER) {
                val ex = PrologDBClientException("Could not negotiate protocol version: server insisted on unsupported version ${serverHello.chosenProtocolVersion}")
                try {
                    connection.close()
                } catch (ex2: Throwable) {
                    ex.addSuppressed(ex2)
                }

                throw ex
            }

            return ProtocolV1PrologDBConnection(connection, serverHello)
        }
    }
}

open class PrologDBClientException @JvmOverloads constructor(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

open class PrologDBConnectionClosedException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) : PrologDBClientException(message, cause)