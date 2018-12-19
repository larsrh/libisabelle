package info.hupel.isabelle.tests

import org.specs2.matcher._

import info.hupel.isabelle._

trait IsabelleMatchers { self: Matchers =>

  def beSuccess[A](check: ValueCheck[A]): Matcher[ProverResult[A]] =
    new OptionLikeCheckedMatcher[ProverResult, A, A]("ProverResult.Success", {
      case ProverResult.Success(a) => Some(a)
      case _ => None
    }, check)

  def beFailure[A](check: ValueCheck[String]): Matcher[ProverResult[A]] =
    new OptionLikeCheckedMatcher[ProverResult, A, String]("ProverResult.Failure", {
      case ProverResult.Failure(_, msg, _) => Some(msg)
      case _ => None
    }, check)

  def exist[A]: Matcher[A] = ((a: A) => a != null, "doesn't exist")

}
