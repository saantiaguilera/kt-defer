package com.saantiaguilera.deferrable

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias Deferrable = context(DeferrableScope) () -> Unit
typealias Recoverable = context(RecoverableScope) () -> Unit

interface DeferrableScope {
    fun defer(block: Recoverable)
}

interface RecoverableScope {
    fun recover(): Throwable?
}

class GoDeferrableScope : DeferrableScope {

    private val defers: ArrayDeque<Recoverable> = ArrayDeque()

    override fun defer(block: Recoverable) {
        defers.add(block)
    }

    fun isEmpty(): Boolean = defers.isEmpty()

    fun pop(): Recoverable = defers.removeLast()
}

class GoRecoverableScope(private var err: Throwable?) : RecoverableScope {

    override fun recover(): Throwable? {
        val ret = err
        err = null
        return ret
    }

    fun recovered(): Boolean = err == null
}

@Throws(Throwable::class)
@ExperimentalContracts
inline fun deferrable(block: Deferrable) {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    val scope = GoDeferrableScope()
    var err: Throwable? = null
    try {
        block(scope)
    } catch (e: Throwable) {
        err = e
    } finally {
        val recoverableScope = GoRecoverableScope(err)
        while (!scope.isEmpty()) {
            scope.pop()(recoverableScope)
        }

        // if there was an error and it wasn't recovered, throw it.
        if (!recoverableScope.recovered()) {
            throw err!!
        }
    }
}