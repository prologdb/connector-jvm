package com.github.prologdb.connector.adapt

import com.github.prologdb.connector.*
import com.github.prologdb.runtime.unification.Unification
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.NoSuchElementException

/**
 * A **non-thread-safe** iterator over [QueryHandle].
 */
internal class QuerySolutionsIterator private constructor(
    /**
     * The handle. A listener MUST NOT have been registered with this
     * handle before.
     */
    private val handle: QueryHandle,

    /**
     * On construction time, these many solutions are requested from the
     * server.
     */
    prefetchAmount: Int = 1,

    /**
     * If true, a new solution is requested when the last cached one is
     * consumed via [next] (as opposed to when checking for more using [hasNext])
     */
    private val eagerFetch: Boolean = false
) : Iterator<Unification> {

    init {
        assert(prefetchAmount >= 0)
    }

    private val eventQueue = LinkedBlockingQueue<QueryEvent>()
    init {
        handle.addListener { eventQueue.put(it) }
    }

    private var closed = false
    private var error: Throwable? = null

    private fun processNextEvent() {
        if (closed) return

        val event = eventQueue.take()
        when (event) {
            is QueryInitializedEvent -> { /* nothing to do */ }
            is QuerySolutionEvent    -> solutionQueue.add(event.solution)
            is QueryErrorEvent       -> {
                error = event.error
                closed = true
            }
            is QueryClosedEvent      -> {
                closed = true
            }
        }
    }

    private val solutionQueue = ArrayDeque<Unification>(prefetchAmount + 2)

    init {
        handle.requestSolutions(prefetchAmount, false, true)
    }

    override fun hasNext(): Boolean {
        while (!closed && solutionQueue.isEmpty()) {
            processNextEvent()
        }

        return solutionQueue.isNotEmpty() || error != null
    }

    override fun next(): Unification {
        if (!hasNext()) throw NoSuchElementException()

        return solutionQueue.poll() ?: throw fixStackTrace(error ?: throw RuntimeException("Internal error"))
    }

    companion object {
        fun createStepByStep(handle: QueryHandle, prefetchAmount: Int, eagerFetch: Boolean): Iterator<Unification> {
            return QuerySolutionsIterator(handle, prefetchAmount, eagerFetch)
        }
    }
}