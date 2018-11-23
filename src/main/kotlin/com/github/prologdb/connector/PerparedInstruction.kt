package com.github.prologdb.connector

import com.github.prologdb.runtime.term.Atom

/**
 * The counterpart of the JDBC PreparedStatement: a directive invocation or
 * query.
 */
data class PreparedInstruction(
    val instruction: PrologQuery,
    val mode: InstructionMode = InstructionMode.QUERY
) {
    private val instantiationsMap = mutableMapOf<String, PrologTerm>()

    val instantiations: Map<String, PrologTerm> = instantiationsMap

    /**
     * Instantiates the variable with the given name to the given value in the
     * [instruction] (parsing and replacement will be done server-side).
     */
    fun instantiate(variableName: String, value: PrologTerm) {
        instantiationsMap[variableName] = value
    }

    /**
     * @see instantiate
     */
    fun instantiateString(variableName: String, value: String) {
        instantiationsMap[variableName] = PrologTerm.fromPrimitive(variableName)
    }

    /**
     * @see instantiate
     */
    fun instantiateNumber(variableName: String, value: Number) {
        instantiationsMap[variableName] = PrologTerm.fromPrimitive(value)
    }

    /**
     * @see instantiate
     */
    fun instantiateAtom(variableName: String, atomName: String) {
        instantiationsMap[variableName] = PrologTerm.fromAST(Atom(atomName))
    }

    /** delegates to [instantiate] */
    operator fun set(variableName: String, value: PrologTerm) = instantiate(variableName, value)

    /** delegates to [instantiateString] */
    operator fun set(variableName: String, value: String) = instantiateString(variableName, value)

    /** delegates to [instantiateNumber] */
    operator fun set(variableName: String, value: Number) = instantiateNumber(variableName, value)

    /**
     * The total limit of solutions to calculate. Null expresses the intention
     * of the client to consume all solutions.
     */
    var totalLimit: Long? = null

    companion object {
        @JvmStatic
        fun createQuery(instruction: String) = PreparedInstruction(
            PrologQuery.fromSource(instruction),
            InstructionMode.QUERY
        )

        @JvmStatic
        fun createDirectiveInvocation(instruction: String) = PreparedInstruction(
            PrologQuery.fromSource(instruction),
            InstructionMode.DIRECTIVE
        )
    }
}