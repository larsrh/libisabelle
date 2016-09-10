structure Refs = struct
  structure Thy = Ref_Table(type t = theory; val name = "Refs.Thy")
  structure Ctxt = Ref_Table(type t = Proof.context; val name = "Refs.Ctxt")
  structure State = Ref_Table(type t = Proof.state; val name = "Refs.State")
  structure Thm = Ref_Table(type t = thm; val name = "Refs.Thm")
  structure Cterm = Ref_Table(type t = cterm; val name = "Refs.Cterm")
end