package com.engitano.fs2firestore.admin.v1

import com.engitano.fs2firestore.CollectionFor
import com.engitano.fs2firestore.admin.v1.IndexBuilder._
import com.engitano.fs2firestore.constraints.{HasKey, NotHasKey}
import shapeless.{::, HList, HNil, LabelledGeneric, Witness}

case class IndexDefinition(fields: Seq[IndexFieldDefinition]) {

  def fieldsWithout__name__ = this.fields.filterNot(_.name == "__name__")
}
case class IndexFieldDefinition(name: String, isDescending: Boolean = false)

object IndexBuilder {
  sealed trait IsConfigured
  case class Configured()   extends IsConfigured
  case class UnConfigured() extends IsConfigured

  def withColumn[Collection: CollectionFor, K, Repr <: HList](
      cf: CollectionFor[Collection],
      w: Witness.Aux[K],
      descending: Boolean = false
  )(implicit gen: LabelledGeneric.Aux[Collection, Repr], hk: HasKey[Repr, K]): IndexBuilder[Collection, Repr, w.T :: HNil, UnConfigured] = {

    IndexBuilder[Collection, Repr, w.T :: HNil, UnConfigured](
      CollectionFor[Collection].collectionName,
      List(IndexFieldDefinition(SymbolHelpers.keyOf(w), descending))
    )
  }
}

case class IndexBuilder[Collection, Repr <: HList, Ix <: HList, S <: IsConfigured](collection: String, fields: List[IndexFieldDefinition]) {

  def withColumn[K, R <: HList](
      w: Witness.Aux[K],
      descending: Boolean = false
  )(implicit gen: LabelledGeneric.Aux[Collection, R], hk: HasKey[R, K], nhk: NotHasKey[Ix, K]): IndexBuilder[Collection, Repr, w.T :: Ix, Configured] = {
    val newFields = fields :+ IndexFieldDefinition(SymbolHelpers.keyOf(w), descending)
    IndexBuilder[Collection, Repr, w.T :: Ix, Configured](collection, newFields)
  }

  def build(implicit ev: S =:= Configured): IndexDefinition = IndexDefinition(fields)

}
