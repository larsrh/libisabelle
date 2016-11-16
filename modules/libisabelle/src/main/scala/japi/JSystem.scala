package info.hupel.isabelle.japi

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

import info.hupel.isabelle._
import info.hupel.isabelle.api.{Configuration, Environment}

object JSystem {

  def create(env: Environment, config: Configuration, timeout: Duration): JSystem =
    new JSystem(Await.result(System.create(env, config), timeout), timeout)

  def create(env: Environment, config: Configuration): JSystem =
    create(env, config, Duration.Inf)

}

final class JSystem private(system: System, timeout: Duration) {

  def getSystem(): System = system
  def getTimeout(): Duration = timeout

  def withTimeout(newTimeout: Duration): JSystem = new JSystem(system, newTimeout)

  private def await[A](future: Future[A]) =
    Await.result(future, timeout)

  def dispose(): Unit =
    await(system.dispose)

  def invoke[I, O](operation: Operation[I, O], arg: I): O =
    await(system.invoke(operation)(arg)) match {
      case ProverResult.Success(o) => o
      case ProverResult.Failure(exn) => throw exn
    }

}
