signature PROTOCOL = sig
  type name = string
  type ('i, 'o) operation =
    {from_lib : 'i codec,
     to_lib : 'o codec,
     action : 'i -> 'o}

  type flags = {sequential: bool, bracket: bool}

  val default_flags : flags
  val join_flags : flags -> flags -> flags
  val print_flags : flags -> string

  val add_operation : name -> ('i, 'o) operation -> flags -> unit
  val get_operation : name -> int -> XML.tree -> XML.tree
end

structure Protocol: PROTOCOL = struct

type name = string
type ('i, 'o) operation =
  {from_lib : 'i codec,
   to_lib : 'o codec,
   action : 'i -> 'o}

type flags = {sequential: bool, bracket: bool}

val default_flags = {sequential = false, bracket = false}

fun join_flags
  {sequential = seq1, bracket = br1}
  {sequential = seq2, bracket = br2} =
  {sequential = seq1 orelse seq2, bracket = br1 orelse br2}

fun print_flags {sequential, bracket} =
  "({sequential=" ^ Markup.print_bool sequential ^ ",bracket=" ^ Markup.print_bool bracket ^ "})"

type raw_operation = int -> XML.tree -> XML.tree

exception GENERIC of string

val operations =
  Synchronized.var "libisabelle.operations" (Symtab.empty: raw_operation Symtab.table)

val requests =
  Synchronized.var "libisabelle.requests" (Inttab.empty: (unit -> unit) Inttab.table)

fun sequentialize name f =
  let
    val var = Synchronized.var ("libisabelle." ^ name) ()
  in
    fn x => Synchronized.change_result var (fn _ => (f x, ()))
  end

fun bracketize f id x =
  let
    val start = [(Markup.functionN, "libisabelle_start"), ("id", Markup.print_int id)]
    val stop = [(Markup.functionN, "libisabelle_stop"), ("id", Markup.print_int id)]
    val _ = Output.protocol_message start []
    val res = f id x
    val _ = Output.protocol_message stop []
  in res end

fun add_operation name {from_lib, to_lib, action} {sequential, bracket} =
  let
    fun raw _ tree =
      case Codec.decode from_lib tree of
        Codec.Success i => Codec.encode to_lib (action i)
      | Codec.Failure (msg, _) => raise Fail ("decoding input failed for operation " ^ name ^ ": " ^ msg)
    val raw' = raw
      |> (if bracket then bracketize else I)
      |> (if sequential then sequentialize name else I)
  in
    Synchronized.change operations (Symtab.update (name, raw'))
  end

fun get_operation name =
  case Symtab.lookup (Synchronized.value operations) name of
    SOME operation => operation
  | NONE => fn _ => raise Fail "libisabelle: unknown command"

val _ = Isabelle_Process.protocol_command "libisabelle"
  (fn id :: name :: [arg] =>
    let
      val id = Markup.parse_int id
      val response = [(Markup.functionN, "libisabelle_response"), ("id", Markup.print_int id)]
      val args = YXML.parse arg
      fun exec f =
        let
          val future = Future.fork (fn () =>
            let
              val res = Exn.interruptible_capture (f id) args
              val yxml = YXML.string_of (Codec.encode (Codec.exn_result Codec.id) res)
            in
              Output.protocol_message response [yxml]
            end)
        in
          Synchronized.change requests (Inttab.update_new (id, fn () => Future.cancel future))
        end
    in
      exec (get_operation name)
    end)

val _ = Isabelle_Process.protocol_command "libisabelle_cancel"
  (fn ids =>
    let
      fun remove id tab = (Inttab.lookup tab id, Inttab.delete_safe id tab)
      val _ =
        map Markup.parse_int ids
        |> fold_map remove
        |> Synchronized.change_result requests
        |> map (fn NONE => () | SOME f => f ())
    in
      ()
    end)

end