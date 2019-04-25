/*  Title:      Pure/General/sha1.scala
    Author:     Makarius

Digest strings according to SHA-1 (see RFC 3174).
*/

package isabelle


import java.io.{File => JFile, FileInputStream}
import java.security.MessageDigest


object SHA1
{
  final class Digest private[SHA1](val rep: String)
  {
    override def hashCode: Int = rep.hashCode
    override def equals(that: Any): Boolean =
      that match {
        case other: Digest => rep == other.rep
        case _ => false
      }
    override def toString: String = rep
  }

  private def make_result(digest: MessageDigest): Digest =
  {
    val result = new StringBuilder
    for (b <- digest.digest()) {
      val i = b.asInstanceOf[Int] & 0xFF
      if (i < 16) result += '0'
      result ++= Integer.toHexString(i)
    }
    new Digest(result.toString)
  }

  def fake(rep: String): Digest = new Digest(rep)

  def digest(file: JFile): Digest =
  {
    val digest = MessageDigest.getInstance("SHA")

    using(new FileInputStream(file))(stream =>
    {
      val buf = new Array[Byte](65536)
      var m = 0
      try {
        do {
          m = stream.read(buf, 0, buf.length)
          if (m != -1) digest.update(buf, 0, m)
        } while (m != -1)
      }
    })

    make_result(digest)
  }

  def digest(path: Path): Digest = digest(path.file)

  def digest(bytes: Array[Byte]): Digest =
  {
    val digest = MessageDigest.getInstance("SHA")
    digest.update(bytes)

    make_result(digest)
  }

  def digest(bytes: Bytes): Digest = bytes.sha1_digest
  def digest(string: String): Digest = digest(Bytes(string))

  val digest_length: Int = digest("").rep.length
}
