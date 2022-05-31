package com.saantiaguilera.deferrable

import java.io.File
import java.io.InputStream
import java.util.concurrent.Semaphore
import kotlin.contracts.ExperimentalContracts

// try-catch example
@ExperimentalContracts
fun tryCatchExample() = deferrable {
    defer { recover()?.let { err -> println(err) /* do something with error? */  } }
    throw Error("oops!") // do stuff
}

// try-catch complex example
context(RecoverableScope)
fun customRecover() {
    val err = recover()
    if (err != null) {
        // track in newrelic / your APM
        // log it?
        // do stuff.
        println(err)
    }
}
@ExperimentalContracts
fun tryCatchComplexExample() = deferrable {
    defer { customRecover() }
    throw Error("oops!") // do stuff
}

// example fun we can create on our own with context
context(DeferrableScope)
fun File.open(): InputStream? {
    return try {
        val fis = inputStream()
        defer { fis.close() }
        fis
    } catch (ignored: Exception) {
        null
    }
}

// example fun we can create on our own that uses defers not for closing stuff
// this traces even if an error is thrown
context(DeferrableScope)
fun trace(fn: () -> Unit) {
    val start = System.currentTimeMillis()
    defer { println("execution of fn took ${System.currentTimeMillis() - start} ms") }
    fn()
}

interface DoerRepository {
    fun getStuff(): File
}

// example fun were we do stuff
@ExperimentalContracts
fun doStuff(doer: DoerRepository): (String) {
    deferrable {
        defer { recover()?.let { err -> println(err) /* do something with error? */ } }

        trace {
            val file = doer.getStuff()
            val fis = file.open()

            if (fis != null) {
                // do stuff.
                throw Error("example!")
            }
        }

        return "inner return works fine"
    }

    return "outer return because if an error is thrown we have to return something"
}

// example fun of concurrent stuff that need to be released/mark as done
@ExperimentalContracts
fun semaphore(s: Semaphore) = deferrable {
    s.acquire()
    defer { s.release() }

    // do stuff, even throwing an error still release the acquired object
    throw Error("oops")
}