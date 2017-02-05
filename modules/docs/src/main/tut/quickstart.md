## Quickstart

```tut:invisible
import io.rbricks.scalog.{Level, LoggingBackend}
LoggingBackend.console("info.hupel" -> Level.Info)
```

```tut:book
import scala.concurrent._, scala.concurrent.duration._

import monix.execution.Scheduler.Implicits.global

import info.hupel.isabelle._, info.hupel.isabelle.api._, info.hupel.isabelle.setup._

val setup = Setup.default(Version("2016")).right.get
val resources = Resources.dumpIsabelleResources().right.get
val config = Configuration.simple("Protocol")

val transaction =
  for {
    env <- setup.makeEnvironment(resources)
    sys <- System.create(env, config)
    response <- sys.invoke(Operation.Hello)("world")
    () <- sys.dispose
  } yield response.unsafeGet

val response = Await.result(transaction, Duration.Inf)

assert(response == "Hello world")
```
