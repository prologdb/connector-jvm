package com.github.prologdb.connector

import com.github.prologdb.net.async.readSingleDelimited
import com.github.prologdb.net.async.writeDelimitedTo
import com.github.prologdb.net.negotiation.ClientHello
import com.github.prologdb.net.negotiation.ToClient
import com.github.prologdb.net.negotiation.ToServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Connection handle for a connection to a prologdb server.
 *
 * **Implementations MUST BE thread-safe!**
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
        fun connectTo(endpoint: Endpoint): Future<out PrologDBConnection> {
            val connection = endpoint.openNewConnection()

            ToServer.newBuilder()
                .setHello(ClientHello.newBuilder().addDesiredProtocolVersion(PROTOCOL_VERSION1_SEMVER).build())
                .build()
                .writeDelimitedTo(connection)

            return connection.readSingleDelimited(ToClient::class.java)
                .thenApply { toClient ->
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

                    ProtocolV1PrologDBConnection(connection)
                }
                .toCompletableFuture()
        }
    }
}

open class PrologDBClientException @JvmOverloads constructor(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

open class PrologDBConnectionClosedException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) : PrologDBClientException(message, cause)