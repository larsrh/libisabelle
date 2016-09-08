signature REF_TABLE = sig
  type t
  val read: serial -> t
  val write: serial -> t -> unit
  val delete: serial -> unit

  (* internal *)
  val peek: unit -> t Inttab.table
end

signature REF_TABLE_ARGS = sig
  type t
  val name: string
end

functor Ref_Table(Args : REF_TABLE_ARGS) : REF_TABLE where type t = Args.t = struct

type t = Args.t

type table = t Inttab.table
val empty_table : table = Inttab.empty

val table = Synchronized.var ("Ref_Table." ^ Args.name) empty_table

fun read ser = the (Inttab.lookup (Synchronized.value table) ser)
fun write ser t = Synchronized.change table (Inttab.update (ser, t))
val delete = Synchronized.change table o Inttab.delete

fun peek () = Synchronized.value table

end
