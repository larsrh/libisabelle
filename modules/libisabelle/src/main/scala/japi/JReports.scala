package info.hupel.isabelle.japi

import java.nio.file.Path

import scala.collection.JavaConverters._

import info.hupel.isabelle._
import info.hupel.isabelle.api._

final class JMarkup(markup: Markup) {
  def getName(): String = markup._1
  def getProperties(): java.util.Map[String, String] = markup._2.toMap.asJava
}

final class JReports(reports: Reports) {
  def interpret(env: Environment): java.util.Map[Path, JRegions] =
    reports.interpret(env).regions.mapValues(new JRegions(_)).asJava
}

final class JRegion(val region: Region) extends java.lang.Iterable[JMarkup] {
  def iterator(): java.util.Iterator[JMarkup] = region.markup.iterator.map(new JMarkup(_)).asJava
  def getBody(): java.util.List[XML.Tree] = region.body.asJava
  def getStart(): Int = region.range.start
  def getEnd(): Int = region.range.end
  def getSubRegions(): JRegions = new JRegions(region.subRegions)

  def subMap(f: java.util.function.Function[JRegion, JRegion]): JRegion =
    new JRegion(region.subMap(r => f.apply(new JRegion(r)).region))
  def subFilter(f: java.util.function.Predicate[JRegion]): JRegion =
    new JRegion(region.subFilter(r => f.test(new JRegion(r))))
}

final class JRegions(val regions: Regions) extends java.lang.Iterable[JRegion] {
  def iterator(): java.util.Iterator[JRegion] = regions.items.iterator.map(new JRegion(_)).asJava

  def map(f: java.util.function.Function[JRegion, JRegion]): JRegions =
    new JRegions(regions.map(r => f.apply(new JRegion(r)).region))
  def filter(f: java.util.function.Predicate[JRegion]): JRegions =
    new JRegions(regions.filter(r => f.test(new JRegion(r))))
}
