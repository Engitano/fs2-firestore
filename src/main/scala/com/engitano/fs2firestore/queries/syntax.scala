package com.engitano.fs2firestore.queries

import com.engitano.fs2firestore.ToFirestoreValue
import com.engitano.fs2firestore.constraints.{HasKey, HasProperty, ImplicitHelpers}
import com.google.firestore.v1.StructuredQuery
import com.google.firestore.v1.StructuredQuery.Filter.FilterType.{CompositeFilter, FieldFilter, UnaryFilter}
import com.google.firestore.v1.StructuredQuery.{FieldReference, Filter}
import shapeless.HList
import shapeless.record._

object syntax extends ImplicitHelpers {

  trait ColumnOps {

    type R <: HList
    type Col
    val columnName: String

    def :<[V: ToFirestoreValue](v: V)(implicit ev: HasProperty[R, Col, V]) =
      Comparison(columnName, v, ComparisonOp.<)
    def :<=[V: ToFirestoreValue](v: V)(implicit ev: HasProperty[R, Col, V]) =
      Comparison(columnName, v, ComparisonOp.<=)
    def =:=[V: ToFirestoreValue](v: V)(implicit ev: HasProperty[R, Col, V]) =
      Comparison(columnName, v, ComparisonOp.==)
    def :>=[V: ToFirestoreValue](v: V)(implicit ev: HasProperty[R, Col, V]) =
      Comparison(columnName, v, ComparisonOp.>=)
    def :>[V: ToFirestoreValue](v: V)(implicit ev: HasProperty[R, Col, V]) =
      Comparison(columnName, v, ComparisonOp.>)
    def contains[V: ToFirestoreValue](v: V)(implicit ev: HasProperty[R, Col, Seq[V]]) =
      Comparison(columnName, v, ComparisonOp.contains)

    def isNull(implicit hk: HasKey[R, Col]) =
      Unary(columnName, UnaryOp.isNull)
    def isNan(implicit hk: HasKey[R, Col]) =
      Unary(columnName, UnaryOp.isNan)
  }

  implicit class PimpedFilter[A: ToFilter](a: A) {
    def &&[B: ToFilter](b: B) = CompoundPredicate(a, b)
  }

  implicit def comparisonToFilterInstance[V](implicit tfv: ToFirestoreValue[V]): ToFilter[Comparison[V]] = new ToFilter[Comparison[V]] {
    override def to(a: Comparison[V]): Filter = {
      val op = a.op match {
        case ComparisonOp.<        => StructuredQuery.FieldFilter.Operator.LESS_THAN
        case ComparisonOp.<=       => StructuredQuery.FieldFilter.Operator.LESS_THAN_OR_EQUAL
        case ComparisonOp.==       => StructuredQuery.FieldFilter.Operator.EQUAL
        case ComparisonOp.>=       => StructuredQuery.FieldFilter.Operator.GREATER_THAN_OR_EQUAL
        case ComparisonOp.>        => StructuredQuery.FieldFilter.Operator.GREATER_THAN
        case ComparisonOp.contains => StructuredQuery.FieldFilter.Operator.ARRAY_CONTAINS
      }
      StructuredQuery.Filter(
        FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference(a.k)), op, Some(tfv.to(a.v))))
      )
    }
  }

  implicit def unaryToFilterInstance = new ToFilter[Unary] {
    override def to(u: Unary): Filter = {
      val op = u.op match {
        case UnaryOp.isNull => StructuredQuery.UnaryFilter.Operator.IS_NULL
        case UnaryOp.isNan  => StructuredQuery.UnaryFilter.Operator.IS_NAN
      }

      StructuredQuery.Filter(
        UnaryFilter(
          StructuredQuery.UnaryFilter(op, StructuredQuery.UnaryFilter.OperandType.Field(FieldReference(u.field)))
        )
      )
    }
  }

  implicit def compountToFilterInstance[A, B] = new ToFilter[CompoundPredicate[A, B]] {
    override def to(c: CompoundPredicate[A, B]): Filter = {
      def buildSeq[A: ToFilter](p: A, s: Seq[Filter]): Seq[Filter] = p match {
        case cp @ CompoundPredicate(a, b) => buildSeq(a, s)(cp.filterA) ++ buildSeq(b, s)(cp.filterB)
        case f                            => ToFilter[A].to(f) +: s
      }
      val filters = buildSeq(c.a, Seq())(c.filterA) ++ buildSeq(c.b, Seq())(c.filterB)
      StructuredQuery.Filter(CompositeFilter(StructuredQuery.CompositeFilter(StructuredQuery.CompositeFilter.Operator.AND, filters)))
    }
  }
}
