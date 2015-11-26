package edu.tum.cs.isabelle.tests

import org.specs2.Specification
import org.specs2.matcher._

import edu.tum.cs.isabelle._

trait IsabelleMatchers { self: Specification =>

  def beSuccess[A](check: ValueCheck[A]): Matcher[ProverResult[A]] =
    new OptionLikeCheckedMatcher[ProverResult, A, A]("ProverResult.Success", {
      case ProverResult.Success(a) => Some(a)
      case _ => None
    }, check)

  def beFailure[A]: Matcher[ProverResult[A]] =
    new OptionLikeMatcher[ProverResult, A, Exception]("ProverResult.Failure", {
      case ProverResult.Failure(exn) => Some(exn)
      case _ => None
    })

  def exist[A]: Matcher[A] = ((a: A) => a != null, "doesn't exist")

}

