theory Codec_Test
imports Codec "~~/src/Tools/Spec_Check/Spec_Check"
begin

ML\<open>
fun check_for str =
  let
    val prop =
      "ALL x. (let val c = (" ^ str ^ ") in Codec.decode c (Codec.encode c x) = Codec.Success x end)"
  in check_property prop end

fun gen_unit r =
  ((), r)
\<close>

ML_command\<open>
  check_for "Codec.unit";
  check_for "Codec.int";
  check_for "Codec.string";
  check_for "Codec.tuple Codec.int Codec.int";
  check_for "Codec.tuple Codec.string Codec.unit";
  check_for "Codec.list Codec.unit";
  check_for "Codec.list Codec.int";
  check_for "Codec.list Codec.string";
  check_for "Codec.list (Codec.list Codec.string)";
  check_for "Codec.list (Codec.tuple Codec.int Codec.int)";
  check_for "Codec.tuple Codec.int (Codec.list Codec.int)";
  check_for "Codec.option Codec.int";
  check_for "Codec.option (Codec.list Codec.int)";
  check_for "Codec.list (Codec.option (Codec.int))";
\<close>

end
