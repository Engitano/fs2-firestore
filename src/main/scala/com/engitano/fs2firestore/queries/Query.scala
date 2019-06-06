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

import com.google.firestore.v1.StructuredQuery

import scala.language.experimental.macros

object ToFilter {
  def apply[A](implicit tf: ToFilter[A]) = tf
}

trait ToFilter[A] {
  def to(a: A): StructuredQuery.Filter
}

sealed trait CollectionOf[C]

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

case class CompoundPredicate[A: ToFilter, B: ToFilter](a: A, b: B) {
  def filterA: ToFilter[A] = ToFilter[A]
  def filterB: ToFilter[B] = ToFilter[B]
}





