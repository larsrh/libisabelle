package info.hupel.isabelle.japi;

import info.hupel.isabelle.*;

import static scala.compat.java8.JFunction.*;

public class Operations {

  private Operations() {}

  public static <I, O> Operation<I, O> fromCodecs(String name, Codec<I> enc, Codec<O> dec) {
    return Operation.simple(name, enc, dec);
  }

  public static final Operation<String, String> HELLO =
    Operation.Hello();

  public static final <A, B> Operation<java.util.List<String>, B> useThys(MarkupProcessor<A, B> processor) {
    Operation<scala.collection.immutable.List<String>, B> operation =
        Operation.UseThys(
            processor.init(),
            func(processor::markup),
            func(processor::finish));
    return operation.<java.util.List<String>, B> map(
        func(x -> scala.collection.JavaConversions.asScalaBuffer(x).toList()),
        func(x -> x));
  }

  public static final Operation<java.util.List<String>, Void> USE_THYS =
    Operations.useThys(MarkupProcessor.NULL_PROCESSOR);

  public static final Operation<java.util.List<String>, JReports> USE_THYS_REPORTS =
    Operations.useThys(MarkupProcessor.REPORTS_PROCESSOR).<java.util.List<String>, JReports> map(
        func(x -> x),
        func(JReports::new)
    );

}
