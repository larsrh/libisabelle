import java.nio.file.Paths;
import java.util.Arrays;
import edu.tum.cs.isabelle.japi.*;
import edu.tum.cs.isabelle.setup.*;

public class Hello_PIDE {

  public static void main(String args[]) {
    Environment env = new Environment(Paths.get(System.getenv("ISABELLE_HOME")), Version.latest());
    Configuration config = Configuration.fromPath(Paths.get("."), "Protocol");
    JSystem sys = JSystem.instance(env, config);
    System.out.println(sys.invoke(Operations.HELLO, "world"));
    sys.dispose();
  }

}
