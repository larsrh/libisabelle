package edu.tum.cs.isabelle.api

object `package` {

  type Properties = List[(String, String)]

  type Markup = (String, Properties)

  /**
   * Result from the prover.
   *
   * In the error case, usually a special
   * [[edu.tum.cs.isabelle.Codec.ProverException ProverException]] will be
   * provided, though implementations of an [[Environment environment]] may
   * choose differently.
   *
   * @see [[edu.tum.cs.isabelle.System#invoke]]
   * @see [[edu.tum.cs.isabelle.Codec.exn]]
   */
  type ProverResult[+T] = Either[Throwable, T]

}
