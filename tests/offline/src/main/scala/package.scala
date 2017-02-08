package info.hupel.isabelle

import org.scalacheck._

import info.hupel.isabelle.api.{Markup, XML}

package object tests {

  private def asciiString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
  private def attribs: Gen[List[(String, String)]] = Gen.listOf(
    for { name <- asciiString; value <- asciiString } yield (name, value)
  )
  private def markup: Gen[Markup] =
    for { name <- asciiString; attribs <- attribs } yield (name, attribs)

  private def tree(n: Int): Gen[XML.Tree] = {
    val text = asciiString.map(XML.text)
    if (n == 0)
      text
    else
      for {
        count <- Gen.choose(1, 10)
        size = n / count
        children = tree(size)
        results <- Gen.listOfN(count, children)
        markup <- markup
      }
      yield XML.elem(markup, results)
  }

  implicit val arbitraryTree: Arbitrary[XML.Tree] = Arbitrary(Gen.sized(tree))

}
