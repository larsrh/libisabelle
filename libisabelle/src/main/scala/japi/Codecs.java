package edu.tum.cs.isabelle.japi;

import scala.math.BigInt;

import edu.tum.cs.isabelle.*;

public class Codecs {

  private Codecs() {}


  public static final Codec<String> STRING =
    Codec$.MODULE$.string();

  public static final Codec<BigInt> BIGINT =
    Codec$.MODULE$.integer();

}
