/*  Title:      Pure/Tools/doc.scala
    Author:     Makarius

Access to Isabelle documentation.
*/

package isabelle


import scala.util.matching.Regex


object Doc
{
  /* dirs */

  def dirs(): List[Path] =
    Path.split(Isabelle_System.getenv("ISABELLE_DOCS"))


  /* contents */

  private def contents_lines(): List[(Path, String)] =
    for {
      dir <- dirs()
      catalog = dir + Path.basic("Contents")
      if catalog.is_file
      line <- split_lines(Library.trim_line(File.read(catalog)))
    } yield (dir, line)

  sealed abstract class Entry
  case class Section(text: String, important: Boolean) extends Entry
  case class Doc(name: String, title: String, path: Path) extends Entry
  case class Text_File(name: String, path: Path) extends Entry

  private val Variable_Path = new Regex("""^\$[^/]+/+(.+)$""")

  def text_file(path: Path): Option[Text_File] =
  {
    if (path.is_file) {
      val name =
        path.implode match {
          case Variable_Path(name) => name
          case name => name
        }
      Some(Text_File(name, path))
    }
    else None
  }

  private val Section_Entry = new Regex("""^(\S.*)\s*$""")
  private val Doc_Entry = new Regex("""^\s+(\S+)\s+(.+)\s*$""")

  private def release_notes(): List[Entry] =
    Section("Release Notes", true) ::
      Path.split(Isabelle_System.getenv_strict("ISABELLE_DOCS_RELEASE_NOTES")).flatMap(text_file(_))

  private def examples(): List[Entry] =
    Section("Examples", true) ::
      Path.split(Isabelle_System.getenv_strict("ISABELLE_DOCS_EXAMPLES")).map(file =>
        text_file(file) match {
          case Some(entry) => entry
          case None => error("Bad entry in ISABELLE_DOCS_EXAMPLES: " + file)
        })

  def contents(): List[Entry] =
  {
    val main_contents =
      for {
        (dir, line) <- contents_lines()
        entry <-
          line match {
            case Section_Entry(text) =>
              Library.try_unsuffix("!", text) match {
                case None => Some(Section(text, false))
                case Some(txt) => Some(Section(txt, true))
              }
            case Doc_Entry(name, title) => Some(Doc(name, title, dir + Path.basic(name)))
            case _ => None
          }
      } yield entry

    examples() ::: release_notes() ::: main_contents
  }

  def doc_names(): List[String] =
    for (Doc(name, _, _) <- contents()) yield name


  /* view */

  def view(path: Path)
  {
    if (path.is_file) Output.writeln(Library.trim_line(File.read(path)), stdout = true)
    else {
      val pdf = path.ext("pdf")
      if (pdf.is_file) Isabelle_System.pdf_viewer(pdf)
      else error("Bad Isabelle documentation file: " + pdf)
    }
  }


  /* Isabelle tool wrapper */

  val isabelle_tool = Isabelle_Tool("doc", "view Isabelle documentation", args =>
  {
    val getopts = Getopts("""
Usage: isabelle doc [DOC ...]

  View Isabelle documentation.
""")
    val docs = getopts(args)

    val entries = contents()
    if (docs.isEmpty) Output.writeln(cat_lines(contents_lines().map(_._2)), stdout = true)
    else {
      docs.foreach(doc =>
        entries.collectFirst { case Doc(name, _, path) if doc == name => path } match {
          case Some(path) => view(path)
          case None => error("No Isabelle documentation entry: " + quote(doc))
        }
      )
    }
  })
}
