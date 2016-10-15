signature CODEC = sig
  datatype 'a result = Success of 'a | Failure of string * XML.body
  datatype ('a, 'b) either = Left of 'a | Right of 'b
  type 'a codec

  val the_success: 'a result -> 'a

  val map_result: ('a -> 'b) -> 'a result -> 'b result
  val bind_result: ('a -> 'b result) -> 'a result -> 'b result
  val sequence_results: 'a result list -> 'a list result
  val traverse_results: ('a -> 'b result) -> 'a list -> 'b list result

  val transform: ('a -> 'b) -> ('b -> 'a) -> 'a codec -> 'b codec
  val encode: 'a codec -> 'a -> XML.tree
  val decode: 'a codec -> XML.tree -> 'a result
  val the_decode: 'a codec -> XML.tree -> 'a

  val basic: {encode: 'a -> XML.tree, decode: XML.tree -> 'a result} -> 'a codec

  val variant: ('a -> (int * XML.tree)) -> (int -> (XML.tree -> 'a result) option) -> string -> 'a codec
  val tagged: string -> 'a codec -> 'a codec

  val unit: unit codec
  val bool: bool codec
  val string: string codec
  val int: int codec
  val list: 'a codec -> 'a list codec
  val tuple: 'a codec -> 'b codec -> ('a * 'b) codec
  val triple: 'a codec -> 'b codec -> 'c codec -> ('a * 'b * 'c) codec
  val either: 'a codec -> 'b codec -> ('a, 'b) either codec
  val option: 'a codec -> 'a option codec
  val tree: XML.tree codec

  val sort: sort codec
  val typ: typ codec
  val term: term codec

  exception GENERIC of string
  val exn: exn codec
  val exn_result: 'a codec -> 'a Exn.result codec

 (* internal *)
  val id: XML.tree codec
end

structure Codec: CODEC = struct

datatype 'a result = Success of 'a | Failure of string * XML.body
datatype ('a, 'b) either = Left of 'a | Right of 'b

fun map_result f (Success a) = Success (f a)
  | map_result _ (Failure (msg, body)) = Failure (msg, body)

fun bind_result f (Success a) = f a
  | bind_result _ (Failure (msg, body)) = Failure (msg, body)

fun traverse_results _ [] = Success []
  | traverse_results f (x :: xs) =
      case f x of
        Success y => map_result (fn ys => y :: ys) (traverse_results f xs)
      | Failure (msg, body) => Failure (msg, body)

fun sequence_results xs = traverse_results I xs

fun the_success (Success a) = a
  | the_success _ = raise Fail "unexpected failure"

fun add_tag tag idx body =
  let
    val attrs = case idx of SOME i => [("idx", XML.Encode.int_atom i)] | _ => []
  in XML.Elem (("tag", ("type", tag) :: attrs), body) end

fun expect_tag tag tree =
  case tree of
    XML.Elem (("tag", [("type", tag')]), body) =>
      if tag = tag' then
        Success body
      else
        Failure ("tag mismatch: expected " ^ tag ^ ", got " ^ tag', [tree])
  | _ =>
      Failure ("tag " ^ tag ^ " expected", [tree])

fun expect_tag' tag tree =
  case tree of
    XML.Elem (("tag", [("type", tag'), ("idx", i)]), body) =>
      if tag = tag' then
        Success (XML.Decode.int_atom i, body)
          handle XML.XML_ATOM err => Failure (err, [tree])
      else
        Failure ("tag mismatch: expected " ^ tag ^ ", got " ^ tag', [tree])
  | _ =>
      Failure ("indexed tag " ^ tag ^ " expected", [tree])


abstype 'a codec = Codec of {encode: 'a -> XML.tree, decode: XML.tree -> 'a result} with

val basic = Codec

fun encode (Codec {encode, ...}) = encode
fun decode (Codec {decode, ...}) = decode

fun transform f g (Codec {encode, decode}) = Codec
  {encode = g #> encode,
   decode = decode #> map_result f}

fun list a = Codec
  {encode = map (encode a) #> add_tag "list" NONE,
   decode = expect_tag "list" #> bind_result (traverse_results (decode a))}

fun tuple a b = Codec
  {encode = (fn (x, y) => add_tag "tuple" NONE [encode a x, encode b y]),
   decode = expect_tag "tuple" #> bind_result (fn body =>
     case body of
       [x, y] => decode a x |> bind_result (fn x' => decode b y |> map_result (pair x'))
     | _ => Failure ("invalid structure", body))}

fun variant enc dec tag = Codec
  {encode = (fn a => let val (idx, tree) = enc a in add_tag tag (SOME idx) [tree] end),
   decode = (fn tree => expect_tag' tag tree |> bind_result (fn (idx, body) =>
     case (body, dec idx) of
       ([tree'], SOME res) => res tree'
     | (_, SOME _) => Failure ("invalid structure", [tree])
     | (_, NONE) => Failure ("invalid index " ^ print_int idx, [tree])))}

fun tagged tag a = Codec
  {encode = encode a #> single #> add_tag tag NONE,
   decode = expect_tag tag #> bind_result (fn body =>
     case body of
       [tree] => decode a tree
     | _ => Failure ("invalid structure", body))}

val unit = Codec
  {encode = K (add_tag "unit" NONE []),
   decode = expect_tag "unit" #> bind_result (fn body =>
     case body of
       [] => Success ()
     | _ => Failure ("expected nothing", body))}

fun text to from = Codec
  {encode = fn s => XML.Elem (("text", [("content", encode_string (to s))]), []),
   decode =
    (fn tree as XML.Elem (("text", [("content", s)]), []) =>
          (case from (decode_string s) of
            NONE => Failure ("decoding failed", [tree]) |
            SOME a => Success a)
      | tree => Failure ("expected text tree", [tree]))}

val id = Codec {encode = I, decode = Success}

end

fun the_decode c = the_success o decode c

val int = tagged "int" (text print_int (Exn.get_res o Exn.capture parse_int))
val bool = tagged "bool" (text print_bool (Exn.get_res o Exn.capture parse_bool))
val string = tagged "string" (text I SOME)

val tree = tagged "XML.tree" id

fun option a =
  let
    fun enc (SOME x) = (0, encode a x)
      | enc NONE = (1, encode unit ())
    fun dec 0 = SOME (decode a #> map_result SOME)
      | dec 1 = SOME (decode unit #> map_result (K NONE))
      | dec _ = NONE
  in variant enc dec "option" end

val content_of =
  XML.content_of o YXML.parse_body

(* slightly fishy codec, doesn't preserve exception type *)
exception GENERIC of string

fun string_of_exn (ERROR msg) = "ERROR " ^ content_of msg
  | string_of_exn exn = content_of (@{make_string} exn)

val exn = tagged "exn" (text string_of_exn (SOME o GENERIC))

fun exn_result a =
  let
    fun enc (Exn.Res t) = (0, encode a t)
      | enc (Exn.Exn e) = (1, encode exn e)
    fun dec _ = NONE
  in variant enc dec "Exn.result" end

fun triple a b c =
  tuple a (tuple b c)
  |> transform (fn (a, (b, c)) => (a, b, c)) (fn (a, b, c) => (a, (b, c)))

fun either a b =
  let
    fun enc (Left l)  = (0, encode a l)
      | enc (Right r) = (1, encode b r)
    fun dec 0 = SOME (decode a #> map_result Left)
      | dec 1 = SOME (decode b #> map_result Right)
      | dec _ = NONE
  in variant enc dec "either" end

val sort: sort codec = list string
val indexname: indexname codec = tuple string int

fun typ () =
  let
    fun typ_type () = tuple string (list (typ ()))
    val typ_tfree = tuple string sort
    val typ_tvar = tuple indexname sort

    fun enc (Type arg) =  (0, encode (typ_type ()) arg)
      | enc (TFree arg) = (1, encode typ_tfree arg)
      | enc (TVar arg) =  (2, encode typ_tvar arg)
    fun dec 0 = SOME (decode (typ_type ()) #> map_result Type)
      | dec 1 = SOME (decode typ_tfree #> map_result TFree)
      | dec 2 = SOME (decode typ_tvar #> map_result TVar)
      | dec _ = NONE
  in variant enc dec "typ" end

val typ = typ ()

fun term () =
  let
    val term_const = tuple string typ
    val term_free = tuple string typ
    val term_var = tuple indexname typ
    val term_bound = int
    fun term_abs () = triple string typ (term ())
    fun term_app () = tuple (term ()) (term ())

    fun enc (Const arg) = (0, encode term_const arg)
      | enc (Free arg) =  (1, encode term_free arg)
      | enc (Var arg) =   (2, encode term_var arg)
      | enc (Bound arg) = (3, encode term_bound arg)
      | enc (Abs arg) =   (4, encode (term_abs ()) arg)
      | enc (op $ arg) =  (5, encode (term_app ()) arg)
    fun dec 0 = SOME (decode term_const #> map_result Const)
      | dec 1 = SOME (decode term_free #> map_result Free)
      | dec 2 = SOME (decode term_var #> map_result Var)
      | dec 3 = SOME (decode term_bound #> map_result Bound)
      | dec 4 = SOME (decode (term_abs ()) #> map_result Abs)
      | dec 5 = SOME (decode (term_app ()) #> map_result op $)
      | dec _ = NONE
  in variant enc dec "term" end

val term = term ()

end

type 'a codec = 'a Codec.codec