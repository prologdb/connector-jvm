package com.github.prologdb.connector

import com.github.prologdb.runtime.unification.Unification

/**
 * Resembles a query; provides low-level operations for the query.
 */
interface QueryHandle {
    /**
     * The ID of the query, unique to the connection/session
     */
    val queryId: Int

    /**
     * Instructs the server to calculate `amount` additional solutions. Calls to
     * this method are queued on the server-side. Solutions requested through this
     * method and `doReturn == true` will become available later.
     * @param doReturn If true, the server will send the solutions back. They will
     * then be available through [pollSolutions]
     */
    fun consumeSolutions(amount: Int, doReturn: Boolean = true)

    /**
     * Removes the next solution from the local queue and returns it.
     *
     * @return the next solution, or null if none are currently available.
     * @see [java.util.Queue.poll]
     */
    fun pollSolutions(): Unification?

    /**
     * Not considering multithreading: if this method `> 0`, the next call to [pollSolutions] will not
     * return null.
     *
     * @return The number of solutions directly available at the client-side.
     */
    fun solutionsAvailable(): Int

    /**
     * Whether this query is currently active.
     *
     * Note that both [isOpen] and [isClosed] may both be `false` during the time in which the
     * server initialized the query. This value will remain false until the server actually confirmed
     * that the query is now active. This might never happen if the server encounters a problem while
     * initializing the query (e.g. syntax errors, ...).
     */
    val isOpen: Boolean

    /**
     * Whether the server considers this query to be closed (either because
     * all solutions have been consumed or because an error occured).
     *
     * Note that both [isOpen] and [isClosed] may be `false` at the same time; see [isOpen].
     */
    val isClosed: Boolean

    /**
     * Whether the server has encountered an error while calculating the
     * solutions.
     * @see [error]
     */
    val hasFailed: Boolean

    /**
     * If [hasFailed], this value contains a representation of the error
     * (as constructed from the data received via the connection stream).
     */
    val error: Throwable?
}