package com.github.prologdb.connector

import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Connection handle for a connection to a prologdb server, implemented for
 * the JVM.
 *
 * This class is **THREAD SAFE**. This is achieved by having a separate
 * worker thread. All methods invoked on the connection object send a
 * message to the worker. The worker keeps all the state and because
 * there is only one worker thread per connection.
 */
class PrologDBConnection(
    val connection: EndpointConnection
) : AutoCloseable {
    /**
     * Set to true when [close] is called for the first time.
     */
    @Volatile private var closed: Boolean = false

    /**
     * Executes the given instruction. This method will return almost instantly.
     * The query will be asynchronously submitted to the server and initialized there.
     *
     * All further action regarding the query happens as result of interaction with
     * the returned [QueryHandle]. The thread invoking this function has the possibility
     * to also register the first [QueryEventListener] on the handle, thus assuring not
     * to miss any [QueryEvent] regarding the query started by this invocation.
     */
    fun execute(instruction: PreparedInstruction): QueryHandle {
        TODO()
    }

    private val closingMutex = Any()
    override fun close() {
        if (closed) return
        synchronized(closingMutex) {
            if (closed) return
            closed = true
        }


    }

    private val messagesToWorker = LinkedBlockingQueue<MessageToWorker>()

    private val worker = thread {
        TODO()
    }

    private sealed class MessageToWorker {
        data class StartInstruction(val instruction: PreparedInstruction): MessageToWorker()
    }

    private inner class HandleToConnectionImpl(private val queryId: Int) : QueryHandleToConnection {
        override fun requestSolutions(amount: Int, closeAfterwards: Boolean, doReturn: Boolean) {
            TODO()
        }

        override fun closeQuery() {
            TODO()
        }
    }
}