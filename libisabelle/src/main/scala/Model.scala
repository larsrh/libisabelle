package edu.tum.cs.isabelle

import java.nio.file.{Path, Paths}

import scala.util.Try

import edu.tum.cs.isabelle.api._

import acyclic.file

final case class Range(start: Long, end: Long) {
  def pretty: String = s"[$start, $end]"
}

final case class Region(range: Range, markup: List[Markup], body: XML.Body, subRegions: Regions = Regions.empty) {

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

}

object Regions {
  def empty: Regions = Regions(Nil)
}

final case class Regions(items: List[Region]) {

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

final case class Model(home: Path, regions: Map[Path, Regions] = Map.empty) {

  def pretty: String =
    regions.map { case (path, regions) => s"$path\n${regions.pretty(2)}" }.mkString("\n")

  private def findRange(props: Properties): Option[(Range, Path)] = {
    val map = props.toMap

    for {
      s <- map.get("offset")
      start <- Try(s.toLong).toOption
      e <- map.get("end_offset")
      end <- Try(e.toLong).toOption
      f <- map.get("file")
      file = if (f.startsWith("~~/")) home.resolve(f.stripPrefix("~~/")) else Paths.get(f)
    }
    yield
      (Range(start, end), file)
  }

  def withRegions(regions: Map[Path, Regions]) = copy(regions = regions)

  def update(tree: XML.Tree): Model = tree match {
    case XML.Elem((name, props), body) =>
      findRange(props) match {
        case Some((range, path)) =>
          val old = regions.getOrElse(path, Regions.empty)
          val region = Region(range, List((name, props)), body)
          withRegions(regions.updated(path, old.insert(region)))
        case _ => this
      }
    case _ => this
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

  def interpret(home: Path): Model =
    items.foldLeft(Model(home)) { (model, report) => model.update(report) }

}
