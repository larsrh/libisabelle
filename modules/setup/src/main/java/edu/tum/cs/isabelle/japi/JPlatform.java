package info.hupel.isabelle.japi;

import scala.Option;

import info.hupel.isabelle.api.*;
import info.hupel.isabelle.setup.*;

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
    Option<OfficialPlatform> option = Setup.guessPlatform();
    if (option.isDefined())
      return option.get();
    else
      throw new UnknownPlatformException();
  }

}
