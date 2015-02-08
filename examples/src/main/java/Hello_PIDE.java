import java.io.File;
import java.util.Arrays;
import edu.tum.cs.isabelle.japi.*;

public class Hello_PIDE {

  public static void main(String args[]) {
    JSystem sys = JSystem.instance(new File("examples/src/main/isabelle/Hello_PIDE"), "Hello_PIDE");
    System.out.println(sys.sendCommand("hello", Arrays.asList("world")));
    sys.dispose();
  }

}
