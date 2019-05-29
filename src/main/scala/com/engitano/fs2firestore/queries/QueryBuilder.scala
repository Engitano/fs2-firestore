package com.engitano.fs2firestore.queries

import cats.syntax.option._
import com.engitano.fs2firestore.CollectionFor
import com.engitano.fs2firestore.api.Query
import com.engitano.fs2firestore.queries.QueryBuilder._
import com.google.firestore.v1.StructuredQuery.Filter
import shapeless.{HList, LabelledGeneric}

import scala.language.experimental.macros

object QueryBuilder {

  sealed trait IsConfigured
  case class Configured private ()   extends IsConfigured
  case class UnConfigured private () extends IsConfigured

  class PredicateBuilder[C, R <: HList](implicit lg: LabelledGeneric.Aux[C, R]) {
    implicit def toColumnOps(s: Symbol): syntax.ColumnOps = macro QueryMacros.buildOps[R]
  }

  def from[T: CollectionFor: LabelledGeneric.Aux[?, R], R <: HList](c: CollectionFor[T]) = {
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