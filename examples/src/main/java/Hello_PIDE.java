import java.io.File;
import java.util.Arrays;
import edu.tum.cs.isabelle.japi.*;

public class Hello_PIDE {

  public static void main(String args[]) {
    JSystem sys = JSystem.instance(new File("."), "Protocol");
    System.out.println(sys.invoke(Operations.HELLO, "world"));
    sys.dispose();
  }

}
