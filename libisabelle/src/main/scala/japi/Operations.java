package edu.tum.cs.isabelle.japi;

import edu.tum.cs.isabelle.*;

public class Operations {

  private Operations() {}

  public static <I, O> Operation<I, O> fromCodecs(String name, XMLCodec<I> enc, XMLCodec<O> dec) {
    return Operation$.MODULE$.fromCodecs(name, enc, dec);
  }

  public static final Operation<String, String> HELLO =
    Operation$.MODULE$.Hello();

}
