theory Protocol
imports Main
begin

ML{*

structure Operations : sig

  val hello : XML.body -> XML.body
  val Iterator : XML.body -> XML.body

end = struct

  fun hello raw_data =
    let
      val data = XML.Decode.string raw_data
      val result = "Hello " ^ data
    in
      XML.Encode.string result
    end

  (* at the interface to Java the isac-kernel adopts Java naming conventions *)
  fun Iterator raw_calcID = 
  	let
  	  val calcID = XML.Decode.int raw_calcID
  	  val iterID = (* computed by isac-kernel *) 111
  	  val result = 
  	    "<ADDUSER> " ^ 
  	      "<CALCID> " ^ string_of_int calcID ^ " </CALCID> " ^ 
  	      "<USERID> " ^ string_of_int iterID ^ " </USERID> " ^
        "</ADDUSER>"
    in
      [XML.parse result]
    end

end

*}

ML{*

local

fun response id: Properties.T =
  [(Markup.functionN, "libisabelle_response"),
   ("id", Markup.print_int id)]

val encode_result = XML.Encode.variant
  [fn Exn.Res a => ([], a),
   fn Exn.Exn exn => ([], XML.Encode.string (@{make_string} exn))]

in

val _ = Isabelle_Process.protocol_command "libisabelle"
  (fn id :: cmd :: args =>
    let
      val id = Markup.parse_int id
      fun exec f x =
        (Future.fork (fn () =>
          let
            val res = Exn.interruptible_capture f x
            val yxml = YXML.string_of_body (encode_result res)
          in
            Output.protocol_message (response id) [yxml]
          end);
        ())
      val args = map YXML.parse_body args
    in
      case (cmd, args) of
        ("hello", [data]) =>
          exec Operations.hello data
      | ("Iterator", [data]) =>
          exec Operations.Iterator data
      | _ =>
          error "libisabelle: unknown command"
    end)

end
*}

end
