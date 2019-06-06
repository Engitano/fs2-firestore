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

import shapeless.{::, BasisConstraint, HList, HNil, NotContainsConstraint}
import shapeless.labelled.FieldType
import shapeless.ops.record.Keys

import scala.annotation.implicitNotFound

object constraints {

  trait ImplicitHelpers {
    implicit def defaultHasProperty[ColRepr <: HList, K, V](implicit ev: BasisConstraint[FieldType[K, V] :: HNil, ColRepr]) =
      new HasProperty[ColRepr, K, V] {}

    implicit def defaultHasKey[ColRepr <: HList, TKeys <: HList, K](implicit ke: Keys.Aux[ColRepr, TKeys], ev: BasisConstraint[K :: HNil, TKeys]) =
      new HasKey[ColRepr, K] {}

    implicit def defaultNotHasKey[Keys <: HList,  K](implicit ev: NotContainsConstraint[Keys, K]) =
      new NotHasKey[Keys, K] {}
  }

  @implicitNotFound("""Implicit not found: HasProperty[ColRepr, K, V]
Cannot prove that your collection type contains a property with key ${K} with of value type: ${V}.
Check query keys and types.
Check that you have imported com.engitano.fs2firestore.queries.syntax._""")
  sealed abstract class HasProperty[ColRepr, K, V] protected ()

  @implicitNotFound("""Implicit not found: HasKey[ColRepr, K, V]
Cannot prove that your collection type contains a property with key ${K}.
Check query key.
Check that you have imported com.engitano.fs2firestore.queries.syntax._""")
  sealed abstract class HasKey[ColRepr, K] protected ()

  @implicitNotFound("""Implicit not found: NotHasKey[ColRepr, K, V]
Cannot prove that your collection does not already contain a property with key ${K}.
Check all index keys.
Check that you have imported com.engitano.fs2firestore.queries.syntax._""")
  sealed abstract class NotHasKey[Keys, K] protected ()


}
