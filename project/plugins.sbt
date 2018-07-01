resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  "jgit-repo" at "http://download.eclipse.org/jgit/maven"
)

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.1")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.8")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.2" exclude("org.jruby", "jruby-complete"))
addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.6.4")
addSbtPlugin("info.hupel" % "sbt-api-mappings" % "3.0.1")
addSbtPlugin("info.hupel" % "sbt-jython" % "0.1.1")

addSbtPlugin("info.hupel" % "sbt-libisabelle" % "0.7.0-RC1")
