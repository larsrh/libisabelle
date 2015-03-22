theory Protocol
imports Main
begin

ML\<open>

signature LIBISABELLE = sig
  type operation = XML.body list -> XML.body
  type id = string

  val add_operation : id -> operation -> unit
end

structure Libisabelle : LIBISABELLE = struct

type operation = XML.body list -> XML.body
type id = string

val operations =
  Synchronized.var "libisabelle.operations" (Symtab.empty: operation Symtab.table)

fun add_operation id operation =
  Synchronized.change operations (Symtab.update_new (id, operation))


val encode_result = XML.Encode.variant
  [fn Exn.Res a => ([], a),
   fn Exn.Exn exn => ([], XML.Encode.string (@{make_string} exn))]

val _ = Isabelle_Process.protocol_command "libisabelle"
  (fn id :: cmd :: args =>
    let
      val id = Markup.parse_int id
      val response =
        [(Markup.functionN, "libisabelle_response"),
         ("id", Markup.print_int id)]
      val args = map YXML.parse_body args
      fun exec f =
        (Future.fork (fn () =>
          let
            val res = Exn.interruptible_capture f args
            val yxml = YXML.string_of_body (encode_result res)
          in
            Output.protocol_message response [yxml]
          end);
        ())
    in
      (case Symtab.lookup (Synchronized.value operations) cmd of
        SOME operation => exec operation
      | NONE => exec (fn _ => raise Fail "libisabelle: unknown command"))
    end)

val _ = add_operation "hello"
  (fn [raw_data] =>
    let
      val data = XML.Decode.string raw_data
      val result = "Hello " ^ data
    in
      XML.Encode.string result
    end
  )

end

\<close>

end
