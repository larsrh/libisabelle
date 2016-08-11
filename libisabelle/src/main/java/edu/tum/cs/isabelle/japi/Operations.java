package info.hupel.isabelle.japi;

import info.hupel.isabelle.*;

public class Operations {

  private Operations() {}

  public static <I, O> Operation<I, O> fromCodecs(String name, Codec<I> enc, Codec<O> dec) {
    return Operation$.MODULE$.simple(name, enc, dec);
  }

  public static final Operation<String, String> HELLO =
    Operation$.MODULE$.Hello();

  public static final Operation<java.util.List<String>, Void> useThys(MarkupProcessor processor) {
    return Operation$.MODULE$.UseThys_Java(processor);
  }

  public static final Operation<java.util.List<String>, Void> USE_THYS =
    useThys(MarkupProcessor.NULL_PROCESSOR);

}
