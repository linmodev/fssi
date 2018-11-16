package fssi.interpreter.scp

import fssi.scp.types._
import fssi.store.mpt.Hash
import fssi.types.TransactionSet
import fssi.types.biz._
import fssi.types.implicits._
import fssi.store.implicits._

case class BlockValue(block: Block) extends Value {

  override def rawBytes: Array[Byte] = block.asBytesValue.bytes

  override def compare(v: Value): Int =
    v match {
      case that: BlockValue =>
        val heightOrder = Ordering[BigInt].compare(this.block.height, that.block.height)
        if (heightOrder != 0) heightOrder
        else {
          val tsOrder =
            Ordering[Long].compare(this.block.timestamp.value, that.block.timestamp.value)
          if (tsOrder != 0) tsOrder
          else {
            val thisEncoding = rawBytes.asBytesValue.bcBase58
            val thatEncoding = that.rawBytes.asBytesValue.bcBase58
            Ordering[String].compare(thisEncoding, thatEncoding)
          }
        }
    }

}

object BlockValue {

  object implicits {
    import io.circe._
    import io.circe.generic.auto._
    import io.circe.syntax._
    import fssi.types.json.implicits._

    implicit val valueEncoder: Encoder[Value] = {
      case blockValue: BlockValue => blockValue.asJson
    }

    implicit val valueDecoder: Decoder[Value] = (hCursor: HCursor) => hCursor.as[BlockValue]
  }

}
