import java.io.File;
import java.util.Arrays;
import edu.tum.cs.isabelle.japi.*;
import isabelle.XML.Encode.*;    //ERROR

public class Hello_PIDE {

  public static void main(String args[]) {
    JSystem sys = JSystem.instance(new File("."), "Hello_PIDE");
    System.out.println(sys.sendCommand("hello", Arrays.asList("world")));
    System.out.println("--- test_1:");
    System.out.println(sys.sendCommand("test_1", Arrays.asList(Integer.toString(111))));
    System.out.println("--- test_2:");
    System.out.println(sys.sendCommandXML("test_2", Arrays.asList(isabelle.XML$.Encode$.int_().apply(222) ) ) );
    System.out.println("--- Iterator:");
//    System.out.println(sys.sendCommandXML("Iterator", Arrays.asList(Integer.toString(1))));
    sys.dispose();
  }

}
