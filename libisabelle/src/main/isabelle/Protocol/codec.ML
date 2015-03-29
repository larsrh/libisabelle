signature CODEC = sig
  datatype 'a result = Success of 'a | Failure of string * XML.body
  type 'a codec

  val the_success : 'a result -> 'a

  val map_result : ('a -> 'b) -> 'a result -> 'b result
  val bind_result : ('a -> 'b result) -> 'a result -> 'b result
  val sequence_results : 'a result list -> 'a list result
  val traverse_results : ('a -> 'b result) -> 'a list -> 'b list result

  val transform : ('a -> 'b) -> ('b -> 'a) -> 'a codec -> 'b codec
  val encode : 'a codec -> 'a -> XML.tree
  val decode : 'a codec -> XML.tree -> 'a result

  val unit : unit codec
  val string : string codec
  val int : int codec
  val list : 'a codec -> 'a list codec
  val tuple : 'a codec -> 'b codec -> ('a * 'b) codec
  val variant : ('a -> (int * XML.tree)) -> (int -> (XML.tree -> 'a result) option) -> string -> 'a codec
  val tagged : string -> 'a codec -> 'a codec
  val tree : XML.tree codec
  val body : XML.body codec
  val option : 'a codec -> 'a option codec
  val result : 'a codec -> 'a result codec
end

structure Codec : CODEC = struct

datatype 'a result = Success of 'a | Failure of string * XML.body

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

fun expect_tag tag elem =
  case elem of
    XML.Elem (("tag", [("type", tag')]), body) =>
      if tag = tag' then Success body else Failure ("tag mismatch", [elem])
  | _ =>
      Failure ("tag expected", [elem])

fun expect_tag' tag elem =
  case elem of
    XML.Elem (("tag", [("type", tag'), ("idx", i)]), [tree]) =>
      if tag = tag' then
        Success (XML.Decode.int_atom i, tree)
          handle XML.XML_ATOM err => Failure (err, [elem])
      else
        Failure ("tag mismatch", [elem])
  | _ =>
      Failure ("indexed tag expected", [elem])


abstype 'a codec = Codec of { encode : 'a -> XML.tree , decode: XML.tree -> 'a result } with

fun encode (Codec {encode, ...}) = encode
fun decode (Codec {decode, ...}) = decode

fun transform f g (Codec {encode, decode}) = Codec
  {encode = g #> encode,
   decode = decode #> map_result f}

fun from_lib (e, d) tag = Codec
  {encode = e #> add_tag tag NONE,
   decode = (fn body => expect_tag tag body |> map_result d
      handle XML.XML_ATOM err => Failure (err, [])
           | XML.XML_BODY err => Failure ("decoding failed", err))}

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
   decode = (fn tree => expect_tag' tag tree |> bind_result (fn (idx, tree') =>
     case dec idx of
       SOME res => res tree'
     | NONE => Failure ("invalid index " ^ Markup.print_int idx, [tree])))}

fun tagged tag a = Codec
  {encode = encode a #> single #> add_tag tag NONE,
   decode = expect_tag tag #> bind_result (fn body =>
     case body of
       [tree] => decode a tree
     | _ => Failure ("invalid structure", body))}

val tree = tagged "XML.tree" (Codec {encode = I, decode = Success})

val unit = Codec
  {encode = K (add_tag "unit" NONE []),
   decode = expect_tag "unit" #> bind_result (fn body =>
     case body of
       [] => Success ()
     | _ => Failure ("expected nothing", body))}

end

val int = from_lib (XML.Encode.int, XML.Decode.int) "int"
val string = from_lib (XML.Encode.string, XML.Decode.string) "string"
val body = list tree

fun result a =
  let
    val b = tuple string body
    fun enc (Success x) = (0, encode a x)
      | enc (Failure err) = (1, encode b err)
    fun dec 0 = SOME (decode a #> map_result Success)
      | dec 1 = SOME (decode b #> map_result Failure)
      | dec _ = NONE
  in variant enc dec "Codec.result" end

fun option a =
  let
    fun enc (SOME x) = (0, encode a x)
      | enc NONE = (1, encode unit ())
    fun dec 0 = SOME (decode a #> map_result SOME)
      | dec 1 = SOME (decode unit #> map_result (K NONE))
      | dec _ = NONE
  in variant enc dec "option" end

end

type 'a codec = 'a Codec.codec
