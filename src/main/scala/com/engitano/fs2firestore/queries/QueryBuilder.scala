/*
 * Copyright (c) 2019 Engitano
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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
import shapeless.ops.hlist.Tupler
import shapeless.ops.tuple.Collect
import shapeless.ops.hlist.ZipWithKeys
import shapeless.ops.record.Keys
import shapeless.ops.record.Selector
import shapeless.{BasisConstraint, HList, HNil, LabelledGeneric, Witness, :: => :-:, Generic}

import scala.language.experimental.macros
import shapeless.ops.hlist.Zip
import shapeless.ops.hlist.IsHCons
import shapeless.ops.record.Values

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

  def withStartAt[A, Keys <: HList, Zipped <: HList](sa: A)
    (implicit keys: Keys.Aux[ColOrders, Keys], zip: ZipWithKeys.Aux[Keys, A :-: HNil, Zipped], ev: Zipped <:< ColOrders) =
    QueryBuilder[Col, ColRepr, ColOrders, Locked](filter, order, Some(ev(zip(sa :: HNil))), endAt, offset, skip)

  def withEndAt[A, Keys <: HList, Zipped <: HList](ea: A)
    (implicit keys: Keys.Aux[ColOrders, Keys], zip: ZipWithKeys.Aux[Keys, A :-: HNil, Zipped], ev: Zipped <:< ColOrders) =
    QueryBuilder[Col, ColRepr, ColOrders, Locked](filter, order, startAt, Some(ev(zip(ea :: HNil))), offset, skip)

  def withEndAt(ea: ColOrders)(implicit bc: BasisConstraint[ColOrders, ColRepr]) =
    QueryBuilder[Col, ColRepr, ColOrders, Locked](filter, order, startAt, Some(ea), offset, skip)

  def withStartAt[Tup <: Product, Repr <: HList, Keys <: HList, Zipped <: HList](sa: Tup)
    (implicit tup: Generic.Aux[Tup, Repr], keys: Keys.Aux[ColOrders, Keys], zip: ZipWithKeys.Aux[Keys, Repr, Zipped], ev: Zipped <:< ColOrders) =
    QueryBuilder[Col, ColRepr, ColOrders, Locked](filter, order, Some(ev(zip(tup.to(sa)))), endAt, offset, skip)

  def withEndAt[Tup <: Product, Repr <: HList, Keys <: HList, Zipped <: HList](eq: Tup)
    (implicit tup: Generic.Aux[Tup, Repr], keys: Keys.Aux[ColOrders, Keys], zip: ZipWithKeys.Aux[Keys, Repr, Zipped], ev: Zipped <:< ColOrders) =
    QueryBuilder[Col, ColRepr, ColOrders, Locked](filter, order, startAt, Some(ev(zip(tup.to(eq)))), offset, skip)

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
