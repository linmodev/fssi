package fssi
package scp
package types

import fssi.scp.types.implicits._

/** Quorum Slices
  */
sealed trait Slices {
  final def nest(threshold: Int, validators: NodeID*): Slices = this match {
    case Slices.Flat(threshold1, validators1) =>
      Slices.nest(threshold1,
                  validators1,
                  Slices.flat(threshold, validators: _*).asInstanceOf[Slices.Flat])
    case Slices.Nest(threshold1, validators1, inners1) =>
      Slices.nest(threshold1,
                  validators1,
                  (inners1 :+ Slices.flat(threshold, validators: _*).asInstanceOf[Slices.Flat]): _*)
  }

  final def allNodes: Set[NodeID] = this match {
    case Slices.Flat(_, validators) => validators.toSet
    case Slices.Nest(_, validators, inners) =>
      inners.foldLeft(validators.toSet) {(acc, n) =>
        acc ++ n.validators.toSet
      }
  }
}

object Slices {
  def flat(threshold: Int, validators: NodeID*): Slices =
    Flat(threshold, validators.toVector)

  def nest(threshold: Int, validators: Vector[NodeID], inners: Flat*): Slices =
    Nest(threshold, validators, inners.toVector)

  case class Flat(threshold: Int, validators: Vector[NodeID]) extends Slices {
    override def toString: String = {
      val nodes = validators.map(_.asBytesValue.bcBase58).mkString(",")
      s"{$threshold|$nodes}"
    }
  }

  case class Nest(threshold: Int, validators: Vector[NodeID], inners: Vector[Flat]) extends Slices {
    override def toString: String = {
      val nodes = validators.map(_.asBytesValue.bcBase58).mkString(",")
      s"{$threshold|$nodes,${inners.map(_.toString).mkString(",")}}"
    }
  }
}
