package edu.tum.cs.isabelle

import acyclic.file

/**
 * Minimal API for managing some Isabelle version. It is centered around the
 * notion of an [[Environment environment]], which captures the base
 * functionality of an Isabelle process, e.g. starting and stopping an
 * instance. API clients should go through the higher-level
 * [[edu.tum.cs.isabelle.Implementations implementations]] and
 * [[edu.tum.cs.isabelle.System system]] interfaces.
 */
package object api {

  type Properties = List[(String, String)]

  type Markup = (String, Properties)

}
