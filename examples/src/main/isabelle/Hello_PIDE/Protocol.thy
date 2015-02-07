theory Protocol
imports Main
begin

ML{*

structure Operations : sig

  val hello : XML.body -> XML.body

end = struct

  fun hello raw_data =
    let
      val data = XML.Decode.string raw_data
      val result = "Hello " ^ data
    in
      XML.Encode.string result
    end

end

*}

ML{*

local

fun response sys_id req_id: Properties.T =
  [(Markup.functionN, "libisabelle_response"),
   ("sys_id", Markup.print_int sys_id),
   ("req_id", Markup.print_int req_id)];

val encode_result = XML.Encode.variant
  [fn Exn.Res a => ([], a),
   fn Exn.Exn exn => ([], XML.Encode.string (@{make_string} exn))]

in

val _ = Session.protocol_handler "edu.tum.cs.isabelle.System$Handler"

val _ = Isabelle_Process.protocol_command "libisabelle"
  (fn sys_id :: req_id :: cmd :: args =>
    let
      val sys_id = Markup.parse_int sys_id
      val req_id = Markup.parse_int req_id
      val id = (sys_id, req_id)
      fun exec f x =
        (Future.fork (fn () =>
          let
            val res = Exn.interruptible_capture f x
            val yxml = YXML.string_of_body (encode_result res)
          in
            Output.protocol_message (response sys_id req_id) [yxml]
          end);
        ())
      val args = map YXML.parse_body args
    in
      case (cmd, args) of
        ("hello", [data]) =>
          exec Operations.hello data
      | _ =>
          error "libisabelle: unknown command"
    end)

end
*}

end
