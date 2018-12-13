@file:JvmName("JavaAPIAdapter")
package com.github.prologdb.connector.adapt

import com.github.prologdb.connector.*
import com.github.prologdb.runtime.unification.Unification
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Returns an iterator over the solutions to a given `instruction`.
 *
 * **Important:** more solutions will be requested from the server than are read from
 * the iterator via [Iterator.next]. This is very important when dealing with side-effects of
 * queries (particularly `assert/1` and `retract/1`).
 *
 * @receiver the connection on which to execute the given `instruction`.
 * @param instruction the instruction to execute
 * @return An iterator over the solutions.
 */
fun PrologDBConnection.executeAndIterate(instruction: PreparedInstruction): Iterator<Unification> {
    return QuerySolutionsIterator.createStepByStep(this.execute(instruction), 10, true)
}

/**
 * Runs the given instruction and returns the first solution, if any. The query is closed after
 * consuming that solution.
 */
fun PrologDBConnection.executeSingle(instruction: PreparedInstruction): CompletionStage<Optional<Unification>> {
    instruction.totalLimit = 1

    val future = CompletableFuture<Optional<Unification>>()
    val handle = execute(instruction)
    handle.addListener { evt ->
        when(evt) {
        is QuerySolutionEvent -> {
            future.complete(Optional.of(evt.solution))
        }
        is QueryErrorEvent -> {
            future.completeExceptionally(evt.error)
        }
        is QueryClosedEvent -> {
            if (!future.isDone) {
                future.complete(Optional.empty())
            } // already completed by error or solution
        }
        is QueryInitializedEvent -> { /* nothing to do */ }
    }}

    handle.requestSolutions(1, true, true)
    return future
}

/**
 * Convenience method for [executeSingle]: runs the given instruction as a directive and returns the first
 * result. The invocation is closed after the first result has been found.
 */
@JvmOverloads
fun PrologDBConnection.invokeSingle(directive: String, instantiations: Map<String, PrologTerm>? = null): CompletionStage<Optional<Unification>> {
    val pI = PreparedInstruction(PrologQuery.fromSource(directive), InstructionMode.DIRECTIVE)
    instantiations?.let { it.forEach(pI::instantiate) }
    return executeSingle(pI)
}

/**
 * Makes the server calculate all solutions to the given instruction but not return
 * any of it. This is intended for queries where the user is only interested in the
 * side-effects of the queries (such as writing).
 * @return Completes when the server reports to have computed all solutions.
 */
fun PrologDBConnection.doAll(instruction: PreparedInstruction): CompletionStage<Unit> {
    val future = CompletableFuture<Unit>()
    val handle = execute(instruction)
    handle.addListener { evt -> when(evt) {
        is QueryClosedEvent -> future.complete(Unit)
        is QueryErrorEvent  -> future.completeExceptionally(evt.error)
        is QuerySolutionEvent, is QueryInitializedEvent -> { /* nothing to do */ }
    }}

    return future
}