package com.github.prologdb.connector.adapt

/**
 * Changes the stack trace of the given throwable to point to the
 * call site of this function.
 * @return the given object for inline use, e.g. `throw fixStackTrace(error)`.
 */
internal fun <T : Throwable> fixStackTrace(err: T): T {
    val stackTraceUntilThisLine = Thread.currentThread().stackTrace

    val fixedTrace = Array<StackTraceElement>(stackTraceUntilThisLine.size - 2) { stackTraceUntilThisLine[it + 2] }
    err.stackTrace = fixedTrace

    return err
}