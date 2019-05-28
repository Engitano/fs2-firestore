package com.engitano.fs2firestore.queries

import cats.syntax.option._
import com.engitano.fs2firestore.api.Query
import com.engitano.fs2firestore.queries.QueryBuilder.{Configured, IsConfigured, PredicateBuilder, UnConfigured}
import com.engitano.fs2firestore.{CollectionFor, ToFirestoreValue}
import com.google.firestore.v1.StructuredQuery
import com.google.firestore.v1.StructuredQuery.Filter.FilterType.{CompositeFilter, FieldFilter, UnaryFilter}
import com.google.firestore.v1.StructuredQuery.{FieldReference, Filter}
import shapeless.LabelledGeneric.Aux
import shapeless._
import shapeless.the
import shapeless.labelled.FieldType
import shapeless.ops.record.Keys

import scala.annotation.implicitNotFound
import scala.language.experimental.macros

object ToFilter {
  def apply[A](implicit tf: ToFilter[A]) = tf
}

trait ToFilter[A] {
  def to(a: A): StructuredQuery.Filter
}

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

object CollectionOf {
  def apply[C] = new CollectionOf[C] {}
}

sealed trait CollectionOf[C]

case class CompoundPredicate[A : ToFilter, B : ToFilter](a: A, b: B) {
  def filterA: ToFilter[A] = ToFilter[A]
  def filterB: ToFilter[B] = ToFilter[B]
}

trait TypedSyntax {
  implicit def defaultHasProperty[ColRepr <: HList, K, V](implicit ev: BasisConstraint[FieldType[K, V] :: HNil, ColRepr]) =
    new HasProperty[ColRepr, K, V] {}


  implicit def defaultHasKey[ColRepr <: HList, TKeys <: HList, K](implicit ke: Keys.Aux[ColRepr, TKeys], ev: BasisConstraint[K :: HNil, TKeys]) =
    new HasKey[ColRepr, K] {}
}

@implicitNotFound(
  """Implicit not found: HasProperty[ColRepr, K, V]
Cannot prove that your collection type contains a property with key ${K} with of value type: ${V}.
Check query keys and types.
Check that you have imported com.engitano.fs2firestore.queries.syntax._""")
sealed abstract class HasProperty[ColRepr, K, V] protected ()

@implicitNotFound(
  """Implicit not found: HasKey[ColRepr, K, V]
Cannot prove that your collection type contains a property with key ${K}.
Check query key.
Check that you have imported com.engitano.fs2firestore.queries.syntax._""")
sealed abstract class HasKey[ColRepr, K] protected ()

object QueryBuilder {

  sealed trait IsConfigured
  case class Configured private ()   extends IsConfigured
  case class UnConfigured private () extends IsConfigured

  class PredicateBuilder[C, R <: HList](implicit lg: LabelledGeneric.Aux[C, R]) {
    implicit def toColumnOps(s: Symbol): ColumnOps = macro QueryMacros.buildOps

    trait ColumnOps {

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
      def :>[V: ToFirestoreValue](v: V)(implicit ev: HasProperty[R, Col, V])=
        Comparison(columnName, v, ComparisonOp.>)
      def contains[V: ToFirestoreValue](v: V)(implicit ev: HasProperty[R, Col, Seq[V]]) =
        Comparison(columnName, v, ComparisonOp.contains)

      def isNull(implicit hk: HasKey[R, Col]) =
        Unary(columnName, UnaryOp.isNull)
      def isNan(implicit hk: HasKey[R, Col]) =
        Unary(columnName, UnaryOp.isNan)
    }
  }

  def from[T: CollectionFor: LabelledGeneric.Aux[?,R], R <: HList](c: CollectionOf[T]) = {
    new QueryBuilder[T, R, UnConfigured](None)
  }
}

case class QueryBuilder[Col: CollectionFor, ColRepr <: HList, HasFilter <: IsConfigured] private (filter: Option[Filter])(
    implicit lg: LabelledGeneric.Aux[Col, ColRepr]
) {

  def where[P: ToFilter](pb: PredicateBuilder[Col, ColRepr] => P)(implicit hf: HasFilter =:= UnConfigured) =
    QueryBuilder[Col, ColRepr, Configured](ToFilter[P].to(pb(new PredicateBuilder[Col, ColRepr]())).some)

  def build(implicit hf: HasFilter =:= Configured): Query[Col] = Query[Col](filter.get)
}

object syntax extends TypedSyntax {

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
      def buildSeq[A  : ToFilter](p: A, s: Seq[Filter]): Seq[Filter] = p match {
        case cp@CompoundPredicate(a,b) => buildSeq(a, s)(cp.filterA) ++ buildSeq(b,s)(cp.filterB)
        case f => ToFilter[A].to(f) +: s
      }
      val filters = buildSeq(c.a, Seq())(c.filterA) ++ buildSeq(c.b, Seq())(c.filterB)
      StructuredQuery.Filter(CompositeFilter(StructuredQuery.CompositeFilter(StructuredQuery.CompositeFilter.Operator.AND, filters)))
    }
  }
}