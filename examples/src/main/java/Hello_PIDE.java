import java.io.File;
import java.util.Arrays;
import edu.tum.cs.isabelle.japi.*;

public class Hello_PIDE {

  public static void main(String args[]) {
    JSystem sys = JSystem.instance(new File("."), "Hello_PIDE");
    System.out.println(sys.sendCommand("hello", Arrays.asList("world")));
    System.out.println(sys.sendCommand("Iterator", Arrays.asList(1)));
    sys.dispose();
  }

}
