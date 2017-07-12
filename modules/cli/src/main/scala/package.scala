package info.hupel.isabelle

import java.nio.file.{Path, Paths}

import caseapp.core.ArgParser

import info.hupel.isabelle.api.Version

package object cli {

  implicit val pathParser: ArgParser[Path] =
    ArgParser.instance[Path] { s => Right(Paths.get(s)) }

  implicit val versionParser: ArgParser[Version] =
    ArgParser.instance[Version](Version.parse)

}
