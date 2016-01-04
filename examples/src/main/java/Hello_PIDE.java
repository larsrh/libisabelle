import java.nio.file.Paths;
import edu.tum.cs.isabelle.api.*;
import edu.tum.cs.isabelle.japi.*;
import edu.tum.cs.isabelle.setup.*;

public class Hello_PIDE {

  public static void main(String args[]) {
    Environment env = JSetup.makeEnvironment(Paths.get(System.getenv("ISABELLE_HOME")), JPlatform.guess(), new Version("2015"));
    Configuration config = Configuration.fromPath(Paths.get("."), "Protocol2015");
    JSystem sys = JSystem.create(env, config);
    System.out.println(sys.invoke(Operations.HELLO, "world"));
    sys.dispose();
  }

}
