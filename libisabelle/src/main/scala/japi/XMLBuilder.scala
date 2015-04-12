package edu.tum.cs.isabelle.japi

import scala.collection.JavaConverters._

import isabelle._

case class TreeBuilder(markup: Markup, body: XML.Body) {

  def this(name: String) = this(Markup(name, Nil), Nil)

  def addAttribute(key: String, value: String) =
    copy(markup = markup.copy(properties = markup.properties :+ ((key, value))))

  def addAttributes(map: java.util.Map[String, String]) =
    copy(markup = markup.copy(properties = markup.properties ++ map.asScala))

  def addChild(text: String) =
    copy(body = body :+ XML.Text(text))

  def addChild(node: XML.Tree) =
    copy(body = body :+ node)

  def addChild(builder: TreeBuilder) =
    copy(body = body :+ builder.toTree)

  def addChildren(nodes: java.util.List[XML.Tree]) =
    copy(body = body ++ nodes.asScala)

  def addChildren(builder: XMLBuilder) =
    copy(body = body ++ builder.toBody)

  def toTree: XML.Tree =
    XML.Elem(markup, body)

}

case class XMLBuilder(body: XML.Body) {

  def this() = this(Nil)

  def append(text: String) =
    copy(body = body :+ XML.Text(text))

  def append(node: XML.Tree) =
    copy(body = body :+ node)

  def append(builder: TreeBuilder) =
    copy(body = body :+ builder.toTree)

  def append(nodes: java.util.List[XML.Tree]) =
    copy(body = body ++ nodes.asScala)

  def append(builder: XMLBuilder) =
    copy(body = body ++ builder.toBody)

  def toBody: XML.Body =
    body

}
