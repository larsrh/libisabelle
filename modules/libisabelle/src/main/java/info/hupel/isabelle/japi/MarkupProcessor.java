package info.hupel.isabelle.japi;

import info.hupel.isabelle.*;
import info.hupel.isabelle.api.*;

public interface MarkupProcessor<A, B> {

  A init();
  A markup(A a, XML.Tree tree);
  B finish(A a);

  public static MarkupProcessor<Void, Void> NULL_PROCESSOR = new MarkupProcessor<Void, Void>() {
    public Void init() { return null; }
    public Void markup(Void v, XML.Tree tree) { return v; }
    public Void finish(Void v) { return v; }
  };

  public static MarkupProcessor<Reports, Reports> REPORTS_PROCESSOR = new MarkupProcessor<Reports, Reports>() {
    public Reports init() { return Reports.empty(); }
    public Reports markup(Reports r, XML.Tree tree) { return r.update(tree); }
    public Reports finish(Reports r) { return r; }
  };

}
