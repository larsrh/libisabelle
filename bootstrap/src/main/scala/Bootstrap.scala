import java.nio.file._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.setup._

object Bootstrap extends App {

  println("Downloading and untarring latest supported Isabelle")
  val env = Await.result(Setup.defaultSetup, duration.Duration.Inf)
  println(s"Successfully set up latest Isabelle: $env")
  val config = Configuration.fromPath(Paths.get("."), "Protocol")
  val built = System.build(env, config)
  if (built)
    println(s"Successfully built configuration: $config")

}
