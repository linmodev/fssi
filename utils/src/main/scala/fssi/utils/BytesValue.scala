package fssi.utils

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

import fssi.utils.BytesValue.Converter

trait BytesValue {
  def bytes: Array[Byte]
  def value: Array[Byte] = bytes

  def hex: String        = bytes.map("%02x" format _).mkString("")
  def utf8String: String = new String(bytes, "utf-8")
  def base64: String     = java.util.Base64.getEncoder.encodeToString(bytes)

  override def equals(obj: scala.Any): Boolean = obj match {
    case x: BytesValue => x.bytes sameElements bytes
    case _             => false
  }

  def toByteArrayInputStream: ByteArrayInputStream =
    new ByteArrayInputStream(bytes)

  override def toString: String = s"Hex($hex)"

  def convertTo[A](implicit converter: Converter[A]): A = converter.to(this)

  def ++(other: BytesValue): BytesValue = BytesValue.combine(this, other)

  def md5: String = {
    val bs = java.security.MessageDigest.getInstance("md5").digest(bytes)
    bs.map("%02x" format _).mkString("")
  }
}

object BytesValue {
  case class SimpleBytesValue(bytes: Array[Byte]) extends BytesValue
  def apply(xs: Array[Byte]): BytesValue = SimpleBytesValue(xs)
  def apply(str: String): BytesValue     = SimpleBytesValue(str.getBytes("utf-8"))

  def valueOf(i: Int): BytesValue = {
    val bb = ByteBuffer.allocate(4)
    bb.putInt(i)
    apply(bb.array())
  }
  def valueOf(i: Long): BytesValue = {
    val bb = ByteBuffer.allocate(8)
    bb.putLong(i)
    apply(bb.array())
  }

  def combine(b1: BytesValue, b2: BytesValue): BytesValue = BytesValue(b1.bytes ++ b2.bytes)


  def decodeHex(hex: String): BytesValue = {

    // seq size should be
    def loop(seq: Vector[Char], bytes: Vector[Byte]): Vector[Byte] = {
      seq.splitAt(2) match {
        case (Vector(), _) => bytes
        case (Vector(c1, c2), t) =>
          loop(t,
               bytes :+ ((Integer.parseInt(c1.toString, 16) << 4) | Integer.parseInt(c2.toString,
                                                                                     16)).toByte)
      }
    }

    apply(loop(hex.toVector, Vector.empty).toArray)

    // jdk9+ can't work
    //apply(javax.xml.bind.DatatypeConverter.parseHexBinary(hex))
  }

  def decodeBase64(base64: String): BytesValue = apply(java.util.Base64.getDecoder.decode(base64))

  val Empty: BytesValue = SimpleBytesValue(Array.emptyByteArray)

  def empty: BytesValue = Empty

  trait Converter[A] {
    def to(bv: BytesValue): A
  }

  object Converter {
    def summon[A](f: BytesValue => A): Converter[A] = (bv: BytesValue) => f(bv)
  }

  object converters {
    implicit val toIntConverter: Converter[Int] =
      Converter.summon(bv => ByteBuffer.wrap(bv.bytes).getInt)
  }

  trait Syntax {
    implicit def arrayBytesToBytesValue(value: Array[Byte]): BytesValue = BytesValue(value)
  }

}
