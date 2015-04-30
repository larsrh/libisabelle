package edu.tum.cs.isabelle

import org.specs2.matcher._

import isabelle.Exn

trait IsabelleMatchers {

  def beRes[A](check: ValueCheck[A]): Matcher[Exn.Result[A]] =
    new OptionLikeCheckedMatcher[Exn.Result, A, A]("Exn.Res", {
      case Exn.Res(a) => Some(a)
      case _ => None
    }, check)

}
