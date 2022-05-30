## Deferrable

Toy project trying out Kotlin's contexts introduced in 1.6.X. This projects mirrors golang's defer feature as much as we can allowing flexible try-catches + function defers to close stuff or operate after the end of it regardless of a potential error being thrown

Feel free to copy this and do whatever you like with it, although (as kotlin also suggests) I highly discourage using it in production since the context API is experimental

### Implementation
Pretty straightforward implementation were we create a scope function that will have as context a `DeferrableScope` which, as its name suggests, allows us to defer functions (that will be invoked once the scope ends regardless of an error thrown or not.

When deferring a func, we will also have another scope called `RecoverableScope` since all deferred functions can gracefully recover from a thrown error. The first function to recover from it will consume it (hence, further calls to recover will yield no value). If no function consumes the thrown error, the thrown error will be broadcasted as a normal one (hence you will have to try-catch it)

Behavior should be exactly as go, regardless of the stuff we can't replicate because of language barriers (eg. deferring an invocation instead of a function), regardless of those stuff it should be the same
- internal panics cant be recovered, thus are signaled
- multiple recovers behave in a first come first serve fashion
- cannot recover from a panic after a recover
- stack invocation ordering
- context visibility
- etc

#### Code
Code is super straightforward as said before:
```kt
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
inline fun deferrable(crossinline block: Deferrable) {
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
```

### Examples
#### Try-Catch
Catch an error more gracefully without needing to pollute the whole method
```kt
fun main() = deferrable {
  defer { recover()?.let { err -> /* handle error */ } }
  // do stuff
  throw Error("oops!")
}

// example of previous method. Imagine a REST API controller for a GET endpoint 
fun restGet(request: Request): Any {
  var response: Any? = null
  deferrable {
    defer { recover()?.let { err -> response = handleError(request, err) } }

    // do stuff
    throw Error("oops!")
  }

  return response
}
```

Or even, in a more complex fashion you can create your own APM contextual function, eg:
```kt
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

fun tryCatchComplexExample() = deferrable {
  defer { customRecover() }
  throw Error("oops!") // do stuff
}
```

#### Closeables
Stuff that should be closed after usage to zero it
```kt
context(DeferrableScope)
fun File.open(): InputStream? {
  return try {
    val fis = inputStream()
    defer { fis.close() } // will still be closed even if there's a panic
    fis
  } catch (ignored: Exception) {
    null // handle it better in your implementation. this is an example :)
  }
}

fun main() = deferrable {
  val fis = File("test.txt").open()
  // use it, it will be closed afterwards
}
```

#### Countdown Latch / Semaphore
Concurrent stuff that has to be signaled after usage to release them
```kt
fun semaphore(s: Semaphore) = deferrable {
  s.acquire()
  defer { s.release() }

  // do stuff, even throwing an error still release the acquired object
  throw Error("oops")
}
```

#### Elapsed time
Getting the elapsed time an operation took even if it throws
```kt
context(DeferrableScope)
fun trace(fn: () -> Unit) {
  val start = System.currentTimeMillis()
  defer { println("execution of fn took ${System.currentTimeMillis() - start} ms") }
  fn()
}

fun main() = deferrable {
  trace {
    // do stuff
  }
}
```

