package com.github.prologdb.connector

import com.github.prologdb.runtime.unification.Unification

/**
 * An event that is reported by the server in the context of a query.
 */
sealed class QueryEvent

class QueryInitializedEvent : QueryEvent() {
    override fun equals(other: Any?): Boolean {
        return other?.javaClass == this.javaClass
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

/**
 * A new solution has been returned from the server (not necessarily discarded!)
 */
data class QuerySolutionEvent(
    val solution: Unification
) : QueryEvent()

/**
 * The query has errored. This event implies that the query is also closed as a result;
 * a separate [QueryClosedEvent] will not be emitted.
 */
data class QueryErrorEvent(
    val error: Throwable
) : QueryEvent()

/**
 * The query has been closed (either by user request or because all solutions have
 * been consumed).
 */
class QueryClosedEvent : QueryEvent() {
    override fun equals(other: Any?): Boolean {
        return other?.javaClass == this.javaClass
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}