package com.engitano.fs2firestore

import shapeless._
import syntax.singleton._
import record._
import shapeless.labelled.FieldType
import shapeless.ops.hlist.{Intersection, IsHCons, SelectAll, SelectMany}
import shapeless.ops.record.Selector

object IdFor extends LowPriorityIdFor {

  def apply[T](implicit n: IdFor[T]) = n

  def fromFunction[T](f: T => String) = new IdFor[T] {
    override def getId(t: T): String = f(t)
  }
}

trait LowPriorityIdFor {

  private val lowerId = Witness('id)
  private val upperId = Witness('ID)
  private val camelId = Witness('Id)

  private type IdType = FieldType[lowerId.T, String] :: FieldType[upperId.T, String] :: FieldType[camelId.T, String] :: HNil

  implicit def hasIdProperty[T, R <: HList, I <: HList, H <: String](
      implicit gen: LabelledGeneric.Aux[T, R],
      i: Intersection.Aux[R, IdType, I],
      l: IsHCons.Aux[I, H, _]
  ) = new IdFor[T] {
    override def getId(t: T): String = l.head(i.apply(gen.to(t)))
  }
}

trait IdFor[T] {
  def getId(t: T): String
}
