## Quickstart

```tut:book
import scala.concurrent._, scala.concurrent.duration._

import monix.execution.Scheduler.Implicits.global

import info.hupel.isabelle._, info.hupel.isabelle.api._, info.hupel.isabelle.setup._

val setup = Setup.default(Version("2016")).right.get

val transaction =
  for {
    env <- setup.makeEnvironment
    resources = Resources.dumpIsabelleResources().right.get
    config = resources.makeConfiguration(Nil, "Protocol")
    sys <- System.create(env, config)
    response <- sys.invoke(Operation.Hello)("world")
    () <- sys.dispose
  } yield response.unsafeGet

val response = Await.result(transaction, Duration.Inf)

assert(response == "Hello world")
```
