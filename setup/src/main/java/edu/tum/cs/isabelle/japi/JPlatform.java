package edu.tum.cs.isabelle.japi;

import scala.Option;

import edu.tum.cs.isabelle.setup.*;

public class JPlatform {

  public static class UnknownPlatformException extends RuntimeException {}

  private JPlatform() {}


  public static final OfficialPlatform LINUX =
    Platform.Linux$.MODULE$;

  public static final OfficialPlatform WINDOWS =
    Platform.Windows$.MODULE$;

  public static final OfficialPlatform OSX =
    Platform.OSX$.MODULE$;

  public static OfficialPlatform guess() {
    Option<OfficialPlatform> option = Platform.guess();
    if (option.isDefined())
      return option.get();
    else
      throw new UnknownPlatformException();
  }

}
