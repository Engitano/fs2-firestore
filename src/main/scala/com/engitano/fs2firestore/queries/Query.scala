package com.engitano.fs2firestore.queries

import com.google.firestore.v1.StructuredQuery

import scala.language.experimental.macros

object ToFilter {
  def apply[A](implicit tf: ToFilter[A]) = tf
}

trait ToFilter[A] {
  def to(a: A): StructuredQuery.Filter
}

sealed trait CollectionOf[C]

sealed trait ComparisonOp
object ComparisonOp {
  case object <        extends ComparisonOp
  case object <=       extends ComparisonOp
  case object ==       extends ComparisonOp
  case object >=       extends ComparisonOp
  case object >        extends ComparisonOp
  case object contains extends ComparisonOp
}

case class Comparison[V](k: String, v: V, op: ComparisonOp)

case class Unary(field: String, op: UnaryOp)
sealed trait UnaryOp
object UnaryOp {
  case object isNull extends UnaryOp
  case object isNan  extends UnaryOp
}

case class CompoundPredicate[A: ToFilter, B: ToFilter](a: A, b: B) {
  def filterA: ToFilter[A] = ToFilter[A]
  def filterB: ToFilter[B] = ToFilter[B]
}





