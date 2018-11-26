@file:JvmName("JavaAPIAdapter")
package com.github.prologdb.connector.adapt

import com.github.prologdb.connector.InstructionMode
import com.github.prologdb.connector.PreparedInstruction
import com.github.prologdb.connector.PrologDBConnection
import com.github.prologdb.connector.PrologQuery
import com.github.prologdb.runtime.unification.Unification

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
 * Convenience method for [executeAndIterate].
 */
@JvmOverloads
fun PrologDBConnection.executeAndIterate(instruction: String, mode: InstructionMode = InstructionMode.QUERY): Iterator<Unification> {
    return executeAndIterate(PreparedInstruction(PrologQuery.fromSource(instruction), mode))
}