package com.github.prologdb.connector

import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class QueryHandleImpl(
    private val talkback: QueryHandleToConnection
) : QueryHandle {

    private val listeners: MutableSet<QueryEventListener> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Set to true when
     * * a close event is received
     * * an error event is received
     * * [close] is invoked
     *
     * Rejects further interaction with the handle pointing out
     * that the query is already closed.
     */
    @Volatile private var closed = false

    /**
     * To be invoked by the connection implementation when a new event
     * regarding this query is available.
     */
    internal fun onEvent(event: QueryEvent)  {
        if (event is QueryClosedEvent || event is QueryErrorEvent) {
            closed = true
        }

        listeners.forEach { it.onQueryEvent(event) }
    }

    private val closingMutex = Any()
    override fun close() {
        if (closed) return
        synchronized(closingMutex) {
            if (closed) return
            closed = true
        }

        talkback.closeQuery()
    }

    override fun addListener(l: QueryEventListener) {
        listeners.add(l)
    }

    override fun removeListener(l: QueryEventListener) {
        listeners.remove(l)
    }

    override fun requestSolutions(amount: Int, closeAfterConsumption: Boolean, doReturn: Boolean) {
        if (closed) throw QueryClosedException("This query is already closed.")

        talkback.requestSolutions(amount, closeAfterConsumption, doReturn)
    }
}

/**
 * Interface between a [QueryHandle] and its [PrologDBConnection]. The handle
 * can use this interface to send messages to the server, etc.
 */
internal interface QueryHandleToConnection {
    /**
     * @see [QueryHandle.requestSolutions]
     * @see [com.github.prologdb.net.v1.messages.QuerySolutionConsumption]
     */
    fun requestSolutions(amount: Int, closeAfterwards: Boolean, doReturn: Boolean)

    /**
     * @see [QueryHandle.close]
     * @see [com.github.prologdb.net.v1.messages.QuerySolutionConsumption]
     */
    fun closeQuery()
}