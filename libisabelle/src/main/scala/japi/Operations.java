package edu.tum.cs.isabelle.japi;

import edu.tum.cs.isabelle.*;

public class Operations {

  private Operations() {}

  public static final Operation<String, String> HELLO =
    Operation$.MODULE$.Hello();

}
