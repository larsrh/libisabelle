package edu.tum.cs.isabelle.japi;

import scala.math.BigInt;

import edu.tum.cs.isabelle.*;
import edu.tum.cs.isabelle.api.*;

public class Codecs {

  private Codecs() {}


  public static final Codec<XML.Tree> TREE =
    Codec$.MODULE$.tree();

  public static final Codec<String> STRING =
    Codec$.MODULE$.string();

  public static final Codec<BigInt> BIGINT =
    Codec$.MODULE$.integer();

}
