package edu.tum.cs.isabelle.api

sealed trait XMLTree {
  def toGeneric(env: Environment): env.XMLTree
}

object XMLTree {

  def fromGeneric(env: Environment)(tree: env.XMLTree): XMLTree =
    env.foldTree(
      text = Text.apply,
      elem = Elem.apply
    )(tree)

  case class Elem(markup: Markup, body: List[XMLTree]) extends XMLTree {
    def toGeneric(env: Environment) =
      env.elem(markup, body.map(_.toGeneric(env)))
  }

  case class Text(content: String) extends XMLTree {
    def toGeneric(env: Environment) =
      env.text(content)
  }

}
