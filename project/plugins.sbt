resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  "jgit-repo" at "http://download.eclipse.org/jgit/maven"
)

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.1")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0-M1")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.0")
addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.6.2")
addSbtPlugin("info.hupel" % "sbt-api-mappings" % "3.0.1")
addSbtPlugin("info.hupel" % "sbt-jsr223" % "0.1.0")

addSbtPlugin("info.hupel" % "sbt-libisabelle" % "0.6.1")
