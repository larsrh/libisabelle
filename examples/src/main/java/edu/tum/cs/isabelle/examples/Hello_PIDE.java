package edu.tum.cs.isabelle.examples.java;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import edu.tum.cs.isabelle.api.*;
import edu.tum.cs.isabelle.japi.*;
import edu.tum.cs.isabelle.setup.*;

public class Hello_PIDE {

  public static void main(String args[]) {
    Environment env = JSetup.makeEnvironment(JSetup.defaultSetup(new Version("2015")));
    JResources res = JResources.dumpIsabelleResources();
    Configuration config = res.makeConfiguration(Arrays.<Path> asList(), "Protocol");
    JSystem sys = JSystem.create(env, config);
    String response = sys.invoke(Operations.HELLO, "world");
    System.out.println(response);
    sys.dispose();
  }

}
