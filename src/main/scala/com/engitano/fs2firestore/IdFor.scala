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

package com.engitano.fs2firestore

import shapeless._
import syntax.singleton._
import record._
import shapeless.labelled.FieldType
import shapeless.ops.hlist.{Intersection, IsHCons, SelectAll, SelectMany}
import shapeless.ops.record.Selector

object IdFor {

  def apply[T](implicit n: IdFor[T]) = n

  def fromFunction[T](f: T => String) = new IdFor[T] {
    override def getId(t: T): String = f(t)
  }
}

trait IdPropertyIdForInstances {

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
