package com.github.prologdb.connector

import com.github.prologdb.runtime.unification.Unification

/**
 * A handle to a query on a prolodb server. Offers low-level interactions.
 */
interface QueryHandle {
    /**
     * Registers the given listener for the events received from the server
     * regarding this query.
     *
     * The first subscriber to this observable will receive all events that
     * have happened since. Subsequent subscribers will only receive those
     * that are received after subscribing.
     */
    fun addListener(l: QueryEventListener)

    /**
     * When this method returns it is guaranteed that the given listener will
     * not be notified about any more events (unless it is passed to [addListener]
     * afterwards).
     */
    fun removeListener(l: QueryEventListener)

    /**
     * Instructs the server to calculate `amount` additional solutions. The order in
     * which this method is invoked is also the order in which the server will handle
     * the solutions.
     * @param closeAfterConsumption If true, the query will be closed after this request
     * for solutions has been completed.
     * @param doReturn If true, the server will send the solutions back. They will
     * then be available through [events]
     */
    @Throws(QueryClosedException::class)
    fun requestSolutions(amount: Int, closeAfterConsumption: Boolean = false, doReturn: Boolean = true)

    /**
     * Assures this query is closed client- and server-side. The first invocation
     * of this method will notify the server. Solutions not yet consumed will not
     * be calculated.
     */
    fun close()
}

/**
 * Gets notified about query events when registered using
 * [QueryHandle.addListener].
 *
 * Java users might want to use [AbstractQueryEventListener].
 */
interface QueryEventListener {
    fun onQueryEvent(event: QueryEvent)
}

/**
 * Thrown when interacting with [QueryHandle] but the query is already closed.
 */
class QueryClosedException(message: String, cause: Throwable? = null) : Exception(message, cause)

// a (possibly?) more convenient option for the java users because
// java does not know that [QueryEvent] is sealed.
abstract class AbstractQueryEventListener : QueryEventListener {
    final override fun onQueryEvent(event: QueryEvent) {
        return when (event) {
            is QueryInitializedEvent -> onInitialized()
            is QuerySolutionEvent    -> onSolutionReceived(event.solution)
            is QueryErrorEvent       -> onError(event.error)
            is QueryClosedEvent      -> onClosed()
        }
    }

    /**
     * @see QueryInitializedEvent
     */
    abstract fun onInitialized()

    /**
     * @see QuerySolutionEvent
     */
    abstract fun onSolutionReceived(solution: Unification)

    /**
     * @see QueryErrorEvent
     */
    abstract fun onError(error: Throwable)

    /**
     * @see QueryClosedEvent
     */
    abstract fun onClosed()
}