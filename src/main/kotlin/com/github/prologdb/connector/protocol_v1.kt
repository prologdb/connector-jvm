package com.github.prologdb.connector

import com.github.prologdb.io.binaryprolog.BinaryPrologReader
import com.github.prologdb.io.binaryprolog.BinaryPrologWriter
import com.github.prologdb.io.util.ByteArrayOutputStream
import com.github.prologdb.io.util.Pool
import com.github.prologdb.net.async.AsyncByteChannelDelimitedProtobufReader
import com.github.prologdb.net.async.AsyncChannelProtobufOutgoingQueue
import com.github.prologdb.net.negotiation.SemanticVersion
import com.github.prologdb.net.v1.messages.*
import com.github.prologdb.runtime.term.Variable
import com.github.prologdb.runtime.unification.Unification
import com.github.prologdb.runtime.unification.VariableBucket
import com.google.protobuf.ByteString
import java.io.DataOutput
import java.io.DataOutputStream
import java.nio.channels.AsynchronousByteChannel
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.concurrent.thread

val PROTOCOL_VERSION1_SEMVER = SemanticVersion.newBuilder()
    .setMajor(1)
    .setMinor(0)
    .setPatch(0)
    .build()

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
    private val connectionChannel: AsynchronousByteChannel
) : PrologDBConnection, AutoCloseable {

    private val outQueue = AsyncChannelProtobufOutgoingQueue(connectionChannel)
    private val inReader = AsyncByteChannelDelimitedProtobufReader(
            ToClient::class.java,
            connectionChannel,
            Consumer { onNewMessageFromServer(it) },
            Consumer { onServerReadError(it) },
            Callable<Unit> { onEndpointConnectionClosed() }
        )

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

        messagesToWorker.put(MessageToWorker.StartInstruction(instruction, queryId))

        return handle
    }

    private val messagesToWorker = LinkedBlockingQueue<MessageToWorker>()

    private val worker = object {

        /**
         * Completes when the worker thread quits; completes exceptionally if
         * the worker thread errors unexpectedly.
         */
        val onStopped = CompletableFuture<Unit>()

        private val thread = thread {
            try {
                doTasks@while (true) {
                    val nextTask = messagesToWorker.takeUninterruptibly()
                    when (nextTask) {
                        is MessageToWorker.StartInstruction -> startInstruction(nextTask)
                        is MessageToWorker.RequestSolutions -> requestSolutions(nextTask)
                        is MessageToWorker.CloseQuery       -> closeQuery(nextTask)
                        is MessageToWorker.TerminateWorker  -> break@doTasks
                    }
                }

                onStopped.complete(Unit)
            }
            catch (ex: Throwable) {
                onStopped.completeExceptionally(ex)
                throw ex
            }
        }

        private fun startInstruction(msg: MessageToWorker.StartInstruction) {
            val initQueryProto = QueryInitialization.newBuilder()
                .setQueryId(msg.queryId)
                .setKind(msg.instruction.mode.toProtocol())
                .setInstruction(msg.instruction.instruction.toProtocol())

            for ((varName, value) in msg.instruction.instantiations) {
                initQueryProto.putInstantiations(varName, value.toProtocol())
            }

            msg.instruction.totalLimit?.let { initQueryProto.setLimit(it) }

            outQueue.queue(
                ToServer.newBuilder()
                    .setInitQuery(initQueryProto.build())
                .build()
            )
        }

        private fun requestSolutions(msg: MessageToWorker.RequestSolutions) {
            outQueue.queue(ToServer.newBuilder()
                .setConsumeResults(msg.request)
                .build()
            )
        }

        private fun closeQuery(msg: MessageToWorker.CloseQuery) {
            outQueue.queue(
                ToServer.newBuilder()
                    .setConsumeResults(
                        QuerySolutionConsumption.newBuilder()
                            .setQueryId(msg.queryId)
                            .setAmount(0)
                            .setCloseAfterwards(true)
                            .setHandling(QuerySolutionConsumption.PostConsumptionAction.DISCARD)
                        .build()
                    )
                .build()
            )
        }
    }

    private fun onNewMessageFromServer(message: ToClient) {
        when (message.eventCase) {
            ToClient.EventCase.QUERY_OPENED -> {
                val queryId = message.queryOpened!!.queryId
                currentlyOpenQueries[queryId]?.onEvent(QueryInitializedEvent())
            }
            ToClient.EventCase.QUERY_CLOSED -> {
                val queryId = message.queryClosed!!.queryId
                currentlyOpenQueries[queryId]?.onEvent(QueryClosedEvent())
                currentlyOpenQueries.remove(queryId)
            }
            ToClient.EventCase.SOLUTION -> {
                val queryId = message.solution!!.queryId
                currentlyOpenQueries[queryId]?.onEvent(QuerySolutionEvent(message.solution!!.toRuntimeUnification()))
            }
            ToClient.EventCase.QUERY_ERROR -> {
                val queryId = message.queryError!!.queryId
                val error = message.queryError!!.toThrowable()
                currentlyOpenQueries[queryId]?.onEvent(QueryErrorEvent(error))
            }
            ToClient.EventCase.SERVER_ERROR -> {
                System.err.println(message.serverError!!)
                TODO()
            }
            ToClient.EventCase.GOODBYE -> if (!closed) close(false)
            ToClient.EventCase.EVENT_NOT_SET -> TODO()
        }
    }

    private fun onServerReadError(error: Throwable) {
        error.printStackTrace()
        TODO()
    }

    private fun onEndpointConnectionClosed() {
        if (closed) {
            // all perfectly fine
            return
        }

        TODO()
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

        // any messages to the worker between closed = true and this point are arguably not important to keep
        // the worker must receive the terminate message in order to terminate cleanly
        // to speed that up, the messages are cleared.
        messagesToWorker.clear()
        messagesToWorker.put(MessageToWorker.TerminateWorker())

        // wait for the worker to stop using the network connection
        worker.onStopped.joinUninterruptibly()

        if (!waitForQueriesToComplete) {
            // close the queries now!
            currentlyOpenQueries
                .filter { (_, handle) -> !handle.closed }
                .forEach { (queryId, handle) ->
                    handle.onEvent(QueryClosedEvent())

                    outQueue.queue(
                        ToServer.newBuilder()
                            .setConsumeResults(QuerySolutionConsumption.newBuilder()
                                .setQueryId(queryId)
                                .setHandling(QuerySolutionConsumption.PostConsumptionAction.DISCARD)
                                .setCloseAfterwards(true)
                                .setAmount(0)
                                .build()
                            )
                            .build()
                    )
                }
        }

        outQueue.queue(
            ToServer.newBuilder()
                .setGoodbye(Goodbye.getDefaultInstance())
                .build()
        ).joinUninterruptibly()
    }

    private sealed class MessageToWorker {
        data class StartInstruction(val instruction: PreparedInstruction, val queryId: Int): MessageToWorker()
        data class RequestSolutions(val request: QuerySolutionConsumption): MessageToWorker()
        data class CloseQuery(val queryId: Int): MessageToWorker()
        class TerminateWorker: MessageToWorker() {
            override fun equals(other: Any?): Boolean = other != null && other::class.java == TerminateWorker::class.java

            override fun hashCode() = 0
        }
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

        fun requestAllRemainingSolutions(doReturn: Boolean) {
            messagesToWorker.put(MessageToWorker.RequestSolutions(QuerySolutionConsumption.newBuilder()
                .setQueryId(queryId)
                .setCloseAfterwards(true)
                .setHandling(if (doReturn) QuerySolutionConsumption.PostConsumptionAction.RETURN else QuerySolutionConsumption.PostConsumptionAction.DISCARD)
                .clearAmount()
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

    private var firstListenerRegistered = AtomicBoolean(false)
    private val firstListenerQueue = ArrayDeque<QueryEvent>()

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

        if (!firstListenerRegistered.get()) {
            synchronized(firstListenerQueue) {
                if (!firstListenerRegistered.get()) {
                    firstListenerQueue.add(event)
                }
            }
        } else {
            listeners.forEach { it.onQueryEvent(event) }
        }
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
        if (firstListenerRegistered.compareAndSet(false, true)) {
            synchronized(firstListenerQueue) {
                firstListenerQueue.forEach(l::onQueryEvent)
                firstListenerQueue.clear()
            }
        }

        listeners.add(l)
    }

    override fun removeListener(l: QueryEventListener) {
        listeners.remove(l)
    }

    override fun requestSolutions(amount: Int, closeAfterConsumption: Boolean, doReturn: Boolean) {
        if (closed) throw QueryClosedException("This query is already closed.")

        talkback.requestSolutions(amount, closeAfterConsumption, doReturn)
    }

    override fun requestAllRemainingSolutions(doReturn: Boolean) {
        if (closed) throw QueryClosedException("This query is already closed.")

        talkback.requestAllRemainingSolutions(doReturn)
    }
}

private val defaultBinaryReader = BinaryPrologReader.getDefaultInstance()
private val defaultBinaryWriter = BinaryPrologWriter.getDefaultInstance()

private fun InstructionMode.toProtocol(): QueryInitialization.Kind = when (this) {
    InstructionMode.QUERY     -> QueryInitialization.Kind.QUERY
    InstructionMode.DIRECTIVE -> QueryInitialization.Kind.DIRECTIVE
}

private val bufferPool = Pool<Pair<ByteArrayOutputStream, DataOutput>>(2, {
    val stream = ByteArrayOutputStream(2048)
    Pair(stream, DataOutputStream(stream))
})

private fun PrologQuery.toProtocol(): Query {
    return if (this.codeIsProlog) {
        Query.newBuilder()
            .setType(Query.Type.STRING)
            .setData(ByteString.copyFrom(this.prologSource!!, Charsets.UTF_8))
            .build()
    } else {
        val binary = bufferPool.using { (bufferStream, dataOutput) ->
            defaultBinaryWriter.writeQueryTo(this.ast!!, dataOutput)
            ByteString.copyFrom(bufferStream.bufferOfData)
        }

        Query.newBuilder()
            .setType(Query.Type.BINARY)
            .setData(binary)
            .build()
    }
}

private fun PrologTerm.toProtocol(): Term {
    return if (this.codeIsProlog) {
        Term.newBuilder()
            .setType(Term.Type.STRING)
            .setData(ByteString.copyFrom(prologSource!!, Charsets.UTF_8))
            .build()
    } else {
        val binary = bufferPool.using { (bufferStream, dataOutput) ->
            defaultBinaryWriter.writeTermTo(this.ast!!, dataOutput)
            ByteString.copyFrom(bufferStream.bufferOfData)
        }

        Term.newBuilder()
            .setType(Term.Type.BINARY)
            .setData(binary)
            .build()
    }
}

private fun QuerySolution.toRuntimeUnification(): Unification {
    val bucket = VariableBucket()
    for ((varName, protocolValue) in instantiationsMap) {
        bucket.instantiate(Variable(varName), protocolValue.toRuntime())
    }

    return Unification(bucket)
}

private fun Term.toRuntime(): com.github.prologdb.runtime.term.Term {
    if (type != Term.Type.BINARY) throw UnsupportedOperationException()

    return defaultBinaryReader.readTermFrom(data.asReadOnlyByteBuffer())
}

private fun QueryRelatedError.toThrowable(): Throwable {
    return PrologDBClientException(this.shortMessage, null)
}