package com.github.prologdb.connector

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
    @Throws(PrologDBConnectionException::class)
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
}

open class PrologDBConnectionException @JvmOverloads constructor(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

open class PrologDBConnectionClosedException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) : PrologDBConnectionException(message, cause)