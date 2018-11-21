package com.github.prologdb.connector

import com.github.prologdb.runtime.query.Query
import com.github.prologdb.runtime.term.PrologDecimal
import com.github.prologdb.runtime.term.PrologInteger
import com.github.prologdb.runtime.term.PrologString
import com.github.prologdb.runtime.term.Term

/**
 * A prolog term either in AST form or in source form.
 */
data class PrologTerm private constructor(
    /**
     * Whether the instruction is present as prolog source or AST form
     */
    val codeIsProlog: Boolean,
    val prologSource: String?,
    val ast: Term?
) {
    init {
        if (codeIsProlog && (prologSource == null || ast != null)) {
            throw IllegalArgumentException("When codeIsProlog = true, prologSource must be set and ast must be null")
        }

        if (!codeIsProlog && (prologSource != null || ast == null)) {
            throw IllegalArgumentException("When codeIsProlog = false, ast must be set and prologSource must be null")
        }
    }

    /**
     * If this term is present in AST form, directly returns the AST. Otherwise
     * invokes the given function to obtain an AST from the source text. The
     * result of that function is not cached.
     */
    fun asAST(parserIfNeeded: (String) -> Term): Term {
        return if (codeIsProlog) parserIfNeeded(prologSource!!) else ast!!
    }

    /**
     * If this term is present in source form, directly returns it. Otherwise
     * delegates to [Term.toString]
     */
    fun asPrologSource(): String {
        return if (codeIsProlog) prologSource!! else ast!!.toString()
    }

    companion object {
        @JvmStatic
        fun fromSource(source: String) = PrologTerm(true, source, null)

        @JvmStatic
        fun fromAST(ast: Term) = PrologTerm(false, null, ast)

        @JvmStatic
        fun fromPrimitive(number: Number): PrologTerm = when(number) {
            is Float  -> fromAST(PrologDecimal(number.toDouble()))
            is Double -> fromAST(PrologDecimal(number))
            else      -> fromAST(PrologInteger(number.toLong()))
        }

        @JvmStatic
        fun fromPrimitive(string: String): PrologTerm = fromAST(PrologString(string))
    }
}

/**
 * A prolog query either in AST form or in source form.
 */
data class PrologQuery private constructor(
    /**
     * Whether the instruction is present as prolog source or AST form
     */
    val codeIsProlog: Boolean,
    val prologSource: String?,
    val ast: Query?
) {
    init {
        if (codeIsProlog && (prologSource == null || ast != null)) {
            throw IllegalArgumentException("When codeIsProlog = true, prologSource must be set and ast must be null")
        }

        if (!codeIsProlog && (prologSource != null || ast == null)) {
            throw IllegalArgumentException("When codeIsProlog = false, ast must be set and prologSource must be null")
        }
    }

    /**
     * If this query is present in AST form, directly returns the AST. Otherwise
     * invokes the given function to obtain an AST from the source text. The
     * result of that function is not cached.
     */
    fun asAST(parserIfNeeded: (String) -> Query): Query {
        return if (codeIsProlog) parserIfNeeded(prologSource!!) else ast!!
    }

    /**
     * If this query is present in source form, directly returns it. Otherwise
     * delegates to [Term.toString]
     */
    fun asPrologSource(): String {
        return if (codeIsProlog) prologSource!! else ast!!.toString()
    }

    companion object {
        @JvmStatic
        fun fromSource(source: String) = PrologQuery(true, source, null)

        @JvmStatic
        fun fromAST(ast: Query) = PrologQuery(false, null, ast)
    }
}