package info.hupel.isabelle

import java.nio.file.{Path, Paths}

import scala.io.Source
import scala.util.Try

import info.hupel.isabelle.api._

import scalatags.Text.all._
import shapeless.tag

private[isabelle] final case class CodepointIterator(string: String, offset: Int) {
  def get: Option[(Int, CodepointIterator)] =
    if (offset < string.length) {
      val codepoint = string.codePointAt(offset)
      Some((codepoint, new CodepointIterator(string, offset + Character.charCount(codepoint))))
    }
    else
      None

  def advanceUntil(target: Int): (String, CodepointIterator) =
    if (offset < target)
      get match {
        case Some((c, next)) =>
          next.advanceUntil(target) match {
            case (str, iter) => (new String(Character.toChars(c)) + str, iter)
          }
        case None => ("", this)
      }
    else
      ("", this)
}

private[isabelle] final case class PreHTML(classes: List[String], parts: List[Either[String, PreHTML]]) {

  def toHTML: HTML = {
    def htmlParts = parts.map {
      case Left(text) => span(text)
      case Right(pre) => pre.toHTML
    }

    span(`class` := classes.mkString(" "))(htmlParts)
  }

}

final case class Range(start: Int, end: Int) {
  def pretty: String = s"[$start, $end]"
}

final case class Region(range: Range, markup: Vector[Markup], body: Vector[XML.Tree], subRegions: Regions = Regions.empty) {

  private[isabelle] def toPreHTML(iter: CodepointIterator): (PreHTML, CodepointIterator) = {
    val (parts, iter2) = subRegions.toPreHTML(iter, range.end)
    (PreHTML("region" :: markup.map(_._1).toList.distinct, parts), iter2)
  }

  private[isabelle] def insert(that: Region): Region =
    if (this.range == that.range)
      Region(range, this.markup ++ that.markup, this.body ++ that.body, this.subRegions merge that.subRegions)
    else
      copy(subRegions = subRegions.insert(that))

  def pretty(indent: Int): String = {
    val header = " " * indent + range.pretty + ": " + markup.map(_._1).mkString("{", ",", "}")
    if (subRegions.items.isEmpty)
      header
    else
      header + "\n" + subRegions.pretty(indent + 2)
  }

  def pretty: String = pretty(0)

  def subMap(f: Region => Region): Region = copy(subRegions = subRegions.map(f))
  def subFilter(f: Region => Boolean): Region = copy(subRegions = subRegions.filter(f))

}

object Regions {
  def empty: Regions = Regions(Nil)
}

final case class Regions(items: List[Region]) {

  private[isabelle] def toPreHTML(iter: CodepointIterator, end: Int): (List[Either[String, PreHTML]], CodepointIterator) = {
    def consume(iter: CodepointIterator, items: List[Region]): (List[Either[String, PreHTML]], CodepointIterator) = items match {
      case Nil =>
        val (str, iter2) = iter.advanceUntil(end)
        (List(Left(str)), iter2)
      case r :: rs =>
        val (prefix, iter2) = iter.advanceUntil(r.range.start)
        val (pre, iter3) = r.toPreHTML(iter2)
        val (rest, iter4) = consume(iter3, rs)
        (Left(prefix) :: Right(pre) :: rest, iter4)
    }

    consume(iter, items)
  }

  def map(f: Region => Region): Regions = Regions(items map f)
  def filter(f: Region => Boolean): Regions = Regions(items filter f)

  def pretty(indent: Int): String =
    items.map(_.pretty(indent)).mkString("\n")

  def pretty: String = pretty(0)

  def merge(that: Regions): Regions =
    that.items.foldLeft(this) { (regions, region) => regions.insert(region) }

  def insert(you: Region): Regions = {
    def aux(items: List[Region]): List[Region] = items match {
      case Nil => List(you)
      case me :: more =>
        // | me |     | you |
        if (you.range.start >= me.range.end)
          me :: aux(more)

        // | you |    | me |
        else if (you.range.end <= me.range.start)
          you :: me :: more

        // |      me       |
        //    | you |
        else if (me.range.start <= you.range.start && you.range.end <= me.range.end)
          me.insert(you) :: more

        // |    you               |
        //    | me | | me2 | ...
        else if (you.range.start <= me.range.start && me.range.end <= you.range.end)
          aux(you.insert(me) :: more)

        // |    you    |
        //        |   me   |
        // (or the other way round)
        else
          sys.error(s"overlapping ranges: $me vs. $you")
    }

    Regions(aux(items))
  }

}

final case class Model(env: Environment, regions: Map[Path, Regions] = Map.empty) {

  def pretty: String =
    regions.map { case (path, regions) => s"$path\n${regions.pretty(2)}" }.mkString("\n")

  private def findRange(props: Properties): Option[(Range, Path)] = {
    val map = props.toMap

    for {
      s <- map.get("offset")
      start <- Try(s.toInt).toOption
      e <- map.get("end_offset")
      end <- Try(e.toInt).toOption
      f <- map.get("file")
      file = if (f.startsWith("~~/")) env.home.resolve(f.stripPrefix("~~/")) else Paths.get(f)
    }
    yield
      // Isabelle starts counting with 1
      (Range(start - 1, end - 1), file)
  }

  def withRegions(regions: Map[Path, Regions]) = copy(regions = regions)

  def update(tree: XML.Tree): Model = tree match {
    case XML.Elem((name, props), body) =>
      findRange(props) match {
        case Some((range, path)) =>
          val old = regions.getOrElse(path, Regions.empty)
          val region = Region(range, Vector((name, props)), body.toVector)
          withRegions(regions.updated(path, old.insert(region)))
        case _ => this
      }
    case _ => this
  }

  private def section(path: Path, regions: Regions) = {
    val name = path.getName(path.getNameCount - 1).toString
    val content = env.decode(tag[Environment.Raw].apply(Source.fromFile(path.toFile, "US-ASCII").mkString))
    val length = content.codePointCount(0, content.length - 1)
    val iter = CodepointIterator(content, 0)

    div(
      h1(name),
      pre(PreHTML(List("file"), regions.toPreHTML(iter, length)._1).toHTML)
    )
  }

  def toHTML: HTML = {
    val sections = regions.map { case (path, regions) => section(path, regions) }.toList

    html(
      head(
        meta(charset := "UTF-8"),
        link(rel := "stylesheet", `type` := "text/css", href := "libisabelle/src/main/resources/xray.css")
      ),
      body(sections)
    )
  }

}

object Reports {
  def empty: Reports = Reports(Vector.empty)
}

final case class Reports(items: Vector[XML.Tree]) {

  def update(tree: XML.Tree): Reports = tree match {
    case XML.Elem(("report", _), body) => copy(items = items ++ body)
    case _ => this
  }

  def +(tree: XML.Tree): Reports = update(tree)

  def interpret(env: Environment): Model =
    items.foldLeft(Model(env)) { (model, report) => model.update(report) }

}
