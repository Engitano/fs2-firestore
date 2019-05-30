package com.engitano.fs2firestore.queries

import cats.syntax.option._
import com.engitano.fs2firestore.{CollectionFor, SymbolHelpers, ToFirestoreValue}
import com.engitano.fs2firestore.api.Query
import com.engitano.fs2firestore.constraints.{HasKey, NotHasKey}
import com.engitano.fs2firestore.queries.QueryBuilder._
import com.engitano.fs2firestore.implicits._
import com.google.firestore.v1.StructuredQuery.Filter
import com.google.firestore.v1.Value.ValueTypeOneof
import com.google.firestore.v1.{MapValue, Value}
import scalapb.KeyValue
import shapeless.labelled.{FieldType, KeyTag}
import shapeless.ops.hlist.Prepend
import shapeless.ops.hlist.Prepend._
import shapeless.ops.record.Selector
import shapeless.{BasisConstraint, HList, HNil, LabelledGeneric, Witness, :: => :-:}

import scala.language.experimental.macros

case class FieldOrder(name: String, isDescending: Boolean)

object QueryBuilder {

  sealed trait IsOrderLocked
  case class Locked() extends IsOrderLocked
  case class Open()   extends IsOrderLocked

  class PredicateBuilder[C, R <: HList](implicit lg: LabelledGeneric.Aux[C, R]) {
    implicit def toColumnOps(s: Symbol): syntax.ColumnOps = macro QueryMacros.buildOps[R]
  }

  def from[T: CollectionFor: LabelledGeneric.Aux[?, R], R <: HList: ToFirestoreValue](c: CollectionFor[T]) = {
    new QueryBuilder[T, R, HNil, Open](None, Seq(), None, None, None, None)
  }
}

case class QueryBuilder[Col: CollectionFor, ColRepr <: HList: ToFirestoreValue, ColOrders <: HList: ToFirestoreValue, CanOrder <: IsOrderLocked] private (
    filter: Option[Filter],
    order: Seq[FieldOrder],
    startAt: Option[ColOrders],
    endAt: Option[ColOrders],
    offset: Option[Int],
    skip: Option[Int]
)(
    implicit lg: LabelledGeneric.Aux[Col, ColRepr]
) {

  def where[P: ToFilter](pb: PredicateBuilder[Col, ColRepr] => P) =
    QueryBuilder[Col, ColRepr, ColOrders, CanOrder](ToFilter[P].to(pb(new PredicateBuilder[Col, ColRepr]())).some, order, startAt, endAt, offset, skip)

  def addOrderBy[K, V, RL <: HList](
      k: Witness.Aux[K],
      descending: Boolean = false
  )(
      implicit
      s: Selector.Aux[ColRepr, K, V],
      p: Prepend.Aux[ColOrders, FieldType[K, V] :-: HNil, RL],
      tf: ToFirestoreValue[RL],
      hk: HasKey[ColRepr, K],
      nhk: NotHasKey[ColOrders, K],
      canOrder: CanOrder =:= Open
  ): QueryBuilder[Col, ColRepr, RL, CanOrder] =
    QueryBuilder[Col, ColRepr, RL, CanOrder](filter, order :+ FieldOrder(SymbolHelpers.keyOf(k), descending), None, None, offset, skip)

  def withStartAt(sa: ColOrders)(implicit bc: BasisConstraint[ColOrders, ColRepr]) =
    QueryBuilder[Col, ColRepr, ColOrders, Locked](filter, order, Some(sa), endAt, offset, skip)

  def withEndAt(ea: ColOrders)(implicit bc: BasisConstraint[ColOrders, ColRepr]) =
    QueryBuilder[Col, ColRepr, ColOrders, Locked](filter, order, startAt, Some(ea), offset, skip)

  def build: Query[Col] = {

    def buildCursor(saOpt: Option[ColOrders]) = {
      saOpt
        .map(ToFirestoreValue[ColOrders].to(_))
        .map {
          case Value(ValueTypeOneof.MapValue(com.google.firestore.v1.MapValue(sa))) =>
            order.foldLeft(List[Value]()) { (p, n) =>
              val startVal = sa(n.name)
              p :+ startVal
            }
        }
        .getOrElse(List())
    }

    Query[Col](filter, order, buildCursor(startAt), buildCursor(endAt), offset, skip)
  }
}
