/*  Title:      Pure/General/file.scala
    Author:     Makarius

File-system operations.
*/

package isabelle


import java.io.{BufferedWriter, OutputStreamWriter, FileOutputStream, BufferedOutputStream,
  OutputStream, InputStream, FileInputStream, BufferedInputStream, BufferedReader,
  InputStreamReader, File => JFile, IOException}
import java.nio.file.{StandardOpenOption, StandardCopyOption, Path => JPath,
  Files, SimpleFileVisitor, FileVisitOption, FileVisitResult, FileSystemException}
import java.nio.file.attribute.BasicFileAttributes
import java.net.{URL, MalformedURLException}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import java.util.regex.Pattern
import java.util.EnumSet

import org.tukaani.xz.{XZInputStream, XZOutputStream}

import scala.collection.mutable
import scala.util.matching.Regex


object File
{
  /* standard path (Cygwin or Posix) */

  def standard_path(path: Path): String = path.expand.implode

  def standard_path(platform_path: String): String =
    if (Platform.is_windows) {
      val Platform_Root = new Regex("(?i)" +
        Pattern.quote(Isabelle_System.cygwin_root()) + """(?:\\+|\z)(.*)""")
      val Drive = new Regex("""([a-zA-Z]):\\*(.*)""")

      platform_path.replace('/', '\\') match {
        case Platform_Root(rest) => "/" + rest.replace('\\', '/')
        case Drive(letter, rest) =>
          "/cygdrive/" + Word.lowercase(letter) +
            (if (rest == "") "" else "/" + rest.replace('\\', '/'))
        case path => path.replace('\\', '/')
      }
    }
    else platform_path

  def standard_path(file: JFile): String = standard_path(file.getPath)

  def standard_url(name: String): String =
    try {
      val url = new URL(name)
      if (url.getProtocol == "file" && Url.is_wellformed_file(name))
        standard_path(Url.parse_file(name))
      else name
    }
    catch { case _: MalformedURLException => standard_path(name) }


  /* platform path (Windows or Posix) */

  private val Cygdrive = new Regex("/cygdrive/([a-zA-Z])($|/.*)")
  private val Named_Root = new Regex("//+([^/]*)(.*)")

  def platform_path(standard_path: String): String =
    if (Platform.is_windows) {
      val result_path = new StringBuilder
      val rest =
        standard_path match {
          case Cygdrive(drive, rest) =>
            result_path ++= (Word.uppercase(drive) + ":" + JFile.separator)
            rest
          case Named_Root(root, rest) =>
            result_path ++= JFile.separator
            result_path ++= JFile.separator
            result_path ++= root
            rest
          case path if path.startsWith("/") =>
            result_path ++= Isabelle_System.cygwin_root()
            path
          case path => path
        }
      for (p <- space_explode('/', rest) if p != "") {
        val len = result_path.length
        if (len > 0 && result_path(len - 1) != JFile.separatorChar)
          result_path += JFile.separatorChar
        result_path ++= p
      }
      result_path.toString
    }
    else standard_path

  def platform_path(path: Path): String = platform_path(standard_path(path))
  def platform_file(path: Path): JFile = new JFile(platform_path(path))


  /* platform files */

  def absolute(file: JFile): JFile = file.toPath.toAbsolutePath.normalize.toFile
  def absolute_name(file: JFile): String = absolute(file).getPath

  def canonical(file: JFile): JFile = file.getCanonicalFile
  def canonical_name(file: JFile): String = canonical(file).getPath

  def path(file: JFile): Path = Path.explode(standard_path(file))
  def pwd(): Path = path(Path.current.absolute_file)


  /* relative paths */

  def relative_path(base: Path, other: Path): Option[Path] =
  {
    val base_path = base.file.toPath
    val other_path = other.file.toPath
    if (other_path.startsWith(base_path))
      Some(path(base_path.relativize(other_path).toFile))
    else None
  }

  def rebase_path(base: Path, other: Path): Option[Path] =
    relative_path(base, other).map(base + _)


  /* bash path */

  def bash_path(path: Path): String = Bash.string(standard_path(path))
  def bash_path(file: JFile): String = Bash.string(standard_path(file))


  /* directory entries */

  def check_dir(path: Path): Path =
    if (path.is_dir) path else error("No such directory: " + path)

  def check_file(path: Path): Path =
    if (path.is_file) path else error("No such file: " + path)


  /* directory content */

  def read_dir(dir: Path): List[String] =
  {
    if (!dir.is_dir) error("No such directory: " + dir.toString)
    val files = dir.file.listFiles
    if (files == null) Nil
    else files.toList.map(_.getName).sorted
  }

  def find_files(
    start: JFile,
    pred: JFile => Boolean = _ => true,
    include_dirs: Boolean = false,
    follow_links: Boolean = false): List[JFile] =
  {
    val result = new mutable.ListBuffer[JFile]
    def check(file: JFile) { if (pred(file)) result += file }

    if (start.isFile) check(start)
    else if (start.isDirectory) {
      val options =
        if (follow_links) EnumSet.of(FileVisitOption.FOLLOW_LINKS)
        else EnumSet.noneOf(classOf[FileVisitOption])
      Files.walkFileTree(start.toPath, options, Integer.MAX_VALUE,
        new SimpleFileVisitor[JPath] {
          override def preVisitDirectory(path: JPath, attrs: BasicFileAttributes): FileVisitResult =
          {
            if (include_dirs) check(path.toFile)
            FileVisitResult.CONTINUE
          }
          override def visitFile(path: JPath, attrs: BasicFileAttributes): FileVisitResult =
          {
            val file = path.toFile
            if (include_dirs || !file.isDirectory) check(file)
            FileVisitResult.CONTINUE
          }
        }
      )
    }

    result.toList
  }


  /* read */

  def read(file: JFile): String = Bytes.read(file).text
  def read(path: Path): String = read(path.file)


  def read_stream(reader: BufferedReader): String =
  {
    val output = new StringBuilder(100)
    var c = -1
    while ({ c = reader.read; c != -1 }) output += c.toChar
    reader.close
    output.toString
  }

  def read_stream(stream: InputStream): String =
    read_stream(new BufferedReader(new InputStreamReader(stream, UTF8.charset)))

  def read_gzip(file: JFile): String =
    read_stream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))
  def read_gzip(path: Path): String = read_gzip(path.file)

  def read_xz(file: JFile): String =
    read_stream(new XZInputStream(new BufferedInputStream(new FileInputStream(file))))
  def read_xz(path: Path): String = read_xz(path.file)


  /* read lines */

  def read_line(reader: BufferedReader): Option[String] =
  {
    val line =
      try { reader.readLine}
      catch { case _: IOException => null }
    if (line == null) None else Some(line)
  }

  def read_lines(reader: BufferedReader, progress: String => Unit): List[String] =
  {
    val result = new mutable.ListBuffer[String]
    var line: Option[String] = None
    while ({ line = read_line(reader); line.isDefined }) {
      progress(line.get)
      result += line.get
    }
    reader.close
    result.toList
  }


  /* write */

  def write_file(file: JFile, text: CharSequence, make_stream: OutputStream => OutputStream)
  {
    val stream = make_stream(new FileOutputStream(file))
    using(new BufferedWriter(new OutputStreamWriter(stream, UTF8.charset)))(_.append(text))
  }

  def write(file: JFile, text: CharSequence): Unit = write_file(file, text, s => s)
  def write(path: Path, text: CharSequence): Unit = write(path.file, text)

  def write_gzip(file: JFile, text: CharSequence): Unit =
    write_file(file, text, (s: OutputStream) => new GZIPOutputStream(new BufferedOutputStream(s)))
  def write_gzip(path: Path, text: CharSequence): Unit = write_gzip(path.file, text)

  def write_xz(file: JFile, text: CharSequence, options: XZ.Options): Unit =
    File.write_file(file, text, s => new XZOutputStream(new BufferedOutputStream(s), options))
  def write_xz(file: JFile, text: CharSequence): Unit = write_xz(file, text, XZ.options())
  def write_xz(path: Path, text: CharSequence, options: XZ.Options): Unit =
    write_xz(path.file, text, options)
  def write_xz(path: Path, text: CharSequence): Unit = write_xz(path, text, XZ.options())

  def write_backup(path: Path, text: CharSequence)
  {
    if (path.is_file) move(path, path.backup)
    write(path, text)
  }

  def write_backup2(path: Path, text: CharSequence)
  {
    if (path.is_file) move(path, path.backup2)
    write(path, text)
  }


  /* append */

  def append(file: JFile, text: CharSequence): Unit =
    Files.write(file.toPath, UTF8.bytes(text.toString),
      StandardOpenOption.APPEND, StandardOpenOption.CREATE)

  def append(path: Path, text: CharSequence): Unit = append(path.file, text)


  /* eq */

  def eq(file1: JFile, file2: JFile): Boolean =
    try { java.nio.file.Files.isSameFile(file1.toPath, file2.toPath) }
    catch { case ERROR(_) => false }

  def eq(path1: Path, path2: Path): Boolean = eq(path1.file, path2.file)


  /* eq_content */

  def eq_content(file1: JFile, file2: JFile): Boolean =
    if (eq(file1, file2)) true
    else if (file1.length != file2.length) false
    else Bytes.read(file1) == Bytes.read(file2)

  def eq_content(path1: Path, path2: Path): Boolean = eq_content(path1.file, path2.file)


  /* copy */

  def copy(src: JFile, dst: JFile)
  {
    val target = if (dst.isDirectory) new JFile(dst, src.getName) else dst
    if (!eq(src, target))
      Files.copy(src.toPath, target.toPath,
        StandardCopyOption.COPY_ATTRIBUTES,
        StandardCopyOption.REPLACE_EXISTING)
  }

  def copy(path1: Path, path2: Path): Unit = copy(path1.file, path2.file)


  /* move */

  def move(src: JFile, dst: JFile)
  {
    val target = if (dst.isDirectory) new JFile(dst, src.getName) else dst
    if (!eq(src, target))
      Files.move(src.toPath, target.toPath, StandardCopyOption.REPLACE_EXISTING)
  }

  def move(path1: Path, path2: Path): Unit = move(path1.file, path2.file)


  /* symbolic link */

  def link(src: Path, dst: Path, force: Boolean = false)
  {
    val src_file = src.file
    val dst_file = dst.file
    val target = if (dst_file.isDirectory) new JFile(dst_file, src_file.getName) else dst_file

    if (force) target.delete

    try { Files.createSymbolicLink(target.toPath, src_file.toPath) }
    catch {
      case _: UnsupportedOperationException if Platform.is_windows =>
        Cygwin.link(standard_path(src), target)
      case _: FileSystemException if Platform.is_windows =>
        Cygwin.link(standard_path(src), target)
    }
  }


  /* permissions */

  def is_executable(path: Path): Boolean =
  {
    if (Platform.is_windows) Isabelle_System.bash("test -x " + bash_path(path)).check.ok
    else path.file.canExecute
  }

  def set_executable(path: Path, flag: Boolean)
  {
    if (Platform.is_windows && flag) Isabelle_System.bash("chmod a+x " + bash_path(path)).check
    else if (Platform.is_windows) Isabelle_System.bash("chmod a-x " + bash_path(path)).check
    else path.file.setExecutable(flag, false)
  }
}
