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

package com.engitano.fs2firestore.admin.v1

import com.engitano.fs2firestore.{CollectionFor, SymbolHelpers}
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
