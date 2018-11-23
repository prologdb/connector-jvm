package com.github.prologdb.connector

import com.github.prologdb.net.v1.messages.Goodbye
import com.github.prologdb.net.v1.messages.QuerySolutionConsumption
import com.github.prologdb.net.v1.messages.ToServer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
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
internal class ProtocolV1PrologDBConnection(
    val connection: EndpointConnection
) : PrologDBConnection, AutoCloseable {
    /**
     * Set to true when [close] is called for the first time.
     */
    @Volatile private var closed: Boolean = false

    private val currentlyOpenQueries: MutableMap<Int, QueryHandleImpl> = ConcurrentHashMap()
    private val queryIdCounter = AtomicInteger(0)

    override fun execute(instruction: PreparedInstruction): QueryHandle {
        if (closed) throw PrologDBConnectionClosedException()

        // try to find a free query id
        var queryId: Int
        do {
            queryId = queryIdCounter.getAndUpdate { c -> (c + 1) % Int.MAX_VALUE }
        }
        while (queryId in currentlyOpenQueries)

        val handle = QueryHandleImpl(QueryHandleToConnectionTalkback(queryId))
        if (currentlyOpenQueries.putIfAbsent(queryId, handle) != null) {
            // race condition, retry
            return execute(instruction)
        }

        // query handle registered successfully

        messagesToWorker.put(MessageToWorker.StartInstruction(instruction))

        return handle
    }

    private val closingMutex = Any()

    override fun close(waitForQueriesToComplete: Boolean) {
        if (closed) return
        synchronized(closingMutex) {
            if (closed) return
            closed = true
        }

        // wait for race conditions of the is-closed check in [execute]
        sleepUninterruptibly(500)

        val allQueriesDone = CompletableFuture<Unit>()
        thread(start = true) {
            while (currentlyOpenQueries.isNotEmpty()) {
                sleepUninterruptibly(500)
            }
            allQueriesDone.complete(Unit)
        }

        if (waitForQueriesToComplete) {
            // wait now, before closing the socket connection
            allQueriesDone.join()
        }

        // wait for the worker to stop using the network connection
        worker.stop().joinUninterruptibly()

        if (!waitForQueriesToComplete) {
            // close the queries now!
            currentlyOpenQueries
                .filter { (_, handle) -> !handle.closed }
                .forEach { (queryId, handle) ->
                    handle.onEvent(QueryClosedEvent())

                    ToServer.newBuilder()
                        .setConsumeResults(QuerySolutionConsumption.newBuilder()
                            .setQueryId(queryId)
                            .setHandling(QuerySolutionConsumption.PostConsumptionAction.DISCARD)
                            .setCloseAfterwards(true)
                            .setAmount(0)
                            .build()
                        )
                        .build()
                        .writeDelimitedTo(connection.outputStream)
                }
        }

        ToServer.newBuilder()
            .setGoodbye(Goodbye.getDefaultInstance())
            .build()
            .writeDelimitedTo(connection.outputStream)

        connection.close()
    }

    private val messagesToWorker = LinkedBlockingQueue<MessageToWorker>()

    private val worker = object {

        private @Volatile var shouldStop = false

        private val onStopped = CompletableFuture<Unit>()

        fun stop(): Future<Unit> {
            shouldStop = true
            return onStopped
        }

        private val thread = thread {
            try {
                TODO()

                onStopped.complete(Unit)
            }
            catch (ex: Throwable) {
                onStopped.completeExceptionally(ex)
            }
        }
    }

    private sealed class MessageToWorker {
        data class StartInstruction(val instruction: PreparedInstruction): MessageToWorker()
        data class RequestSolutions(val request: QuerySolutionConsumption): MessageToWorker()
        data class CloseQuery(val queryId: Int): MessageToWorker()
    }

    inner class QueryHandleToConnectionTalkback(private val queryId: Int) {
        fun requestSolutions(amount: Int, closeAfterwards: Boolean, doReturn: Boolean) {
            if (amount < 0) throw IllegalArgumentException("Cannot request a negative number of solutions")
            if (amount == 0 && !closeAfterwards) return // nothing to do

            messagesToWorker.put(MessageToWorker.RequestSolutions(QuerySolutionConsumption.newBuilder()
                .setQueryId(queryId)
                .setAmount(amount)
                .setCloseAfterwards(closeAfterwards)
                .setHandling(if (doReturn) QuerySolutionConsumption.PostConsumptionAction.RETURN else QuerySolutionConsumption.PostConsumptionAction.DISCARD)
                .build()
            ))
        }

        fun closeQuery() {
            messagesToWorker.put(MessageToWorker.CloseQuery(queryId))
        }
    }
}

private class QueryHandleImpl(
    private val talkback: ProtocolV1PrologDBConnection.QueryHandleToConnectionTalkback
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
    @Volatile var closed = false
        private set

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