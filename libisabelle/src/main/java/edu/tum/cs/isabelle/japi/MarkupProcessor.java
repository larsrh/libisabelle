package info.hupel.isabelle.japi;

import info.hupel.isabelle.api.*;

public interface MarkupProcessor {

  void markup(XML.Tree tree);
  void finish();

  public static MarkupProcessor NULL_PROCESSOR = new MarkupProcessor() {
    public void markup(XML.Tree tree) {}
    public void finish() {}
  };

}
