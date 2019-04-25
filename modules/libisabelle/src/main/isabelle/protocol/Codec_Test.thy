theory Codec_Test
imports Common Spec_Check.Spec_Check
begin

ML\<open>
fun check_for str =
  let
    val prop1 =
      "ALL x. (let val c = (" ^ str ^ ") in Codec.decode c (Codec.encode c x) = Codec.Success x end)"
    val prop2 =
      "ALL x. (let val c = (" ^ str ^ ") in Codec.decode c (YXML.parse (YXML.string_of (Codec.encode c x))) = Codec.Success x end)"
  in
    check_property prop1;
    check_property prop2
  end

fun gen_unit r =
  ((), r)
\<close>

ML_command\<open>
  check_for "Codec.unit";
  check_for "Codec.int";
  check_for "Codec.bool";
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
  check_for "Codec.term";
  check_for "Codec.typ";
\<close>

end
