package com.github.prologdb.connector

enum class InstructionMode {
    /** A query against data or a loaded library (prolog `?-`) */
    QUERY,

    /** The instruction is a directive (prolog `:-`) */
    DIRECTIVE
}