package com.github.prologdb.connector

/**
 * Connection handle for a connection to a prologdb server.
 */
class PrologDBConnection(
    val connection: EndpointConnection
) {
    /**
     * Executes the given instruction. This method will return almost instantly.
     * The query will be asynchronously submitted to the server and initialized there.
     * All further action regarding the query happens as result of interaction with
     * the returned [QueryHandle]
     */
    fun execute(instruction: PreparedInstruction): QueryHandle {
        TODO()
    }
}