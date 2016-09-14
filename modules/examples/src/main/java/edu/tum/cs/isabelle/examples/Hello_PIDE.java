package info.hupel.isabelle.examples.java;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import info.hupel.isabelle.api.*;
import info.hupel.isabelle.japi.*;
import info.hupel.isabelle.setup.*;

public class Hello_PIDE {

  public static void main(String args[]) {
    Environment env = JSetup.makeEnvironment(JSetup.defaultSetup(new Version("2016")));
    JResources res = JResources.dumpIsabelleResources();
    Configuration config = res.makeConfiguration(Arrays.<Path> asList(), "Protocol");
    JSystem sys = JSystem.create(env, config);
    String response = sys.invoke(Operations.HELLO, "world");
    System.out.println(response);
    sys.dispose();
  }

}
