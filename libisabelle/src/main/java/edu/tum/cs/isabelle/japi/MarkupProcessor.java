package edu.tum.cs.isabelle.japi;

import edu.tum.cs.isabelle.api.*;

public interface MarkupProcessor {

  void markup(XML.Tree tree);
  void finish();

  public static MarkupProcessor NULL_PROCESSOR = new MarkupProcessor() {
    public void markup(XML.Tree tree) {}
    public void finish() {}
  };

}
