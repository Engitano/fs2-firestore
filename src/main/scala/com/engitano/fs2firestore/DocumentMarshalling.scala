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

import com.engitano.fs2firestore.ValueMarshaller.UnmarshalResult
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeOneof.MapValue
import shapeless.{LabelledGeneric, Lazy}

import scala.collection.immutable.Map

object ToDocumentFields {
  def apply[T](implicit t: ToDocumentFields[T]) = t
}

trait ToDocumentFields[T] {
  def to(t: T): Map[String, Value]
}

trait DocumentMarshaller[T]
  extends ToDocumentFields[T]
    with FromDocumentFields[T]

object DocumentMarshaller {

  def apply[T](implicit dm: DocumentMarshaller[T]) = dm
}

object FromDocumentFields {
  def apply[T](implicit f: FromDocumentFields[T]) = f
}

trait FromDocumentFields[T] {
  def from(t: Map[String, Value]): UnmarshalResult[T]
}

trait LowPriorityDocumentMarshallers {

  import com.engitano.fs2firestore.Marshalling._

  implicit  def from[T, R](implicit lg: LabelledGeneric.Aux[T, R], vm: Lazy[ValueMarshaller[R]]) = {
    val marshaller = fromGen[T, R]
    new DocumentMarshaller[T] {
      override def from(t: Map[String, Value]): UnmarshalResult[T] =
        marshaller.from(Value(MapValue(com.google.firestore.v1.MapValue(t))))

      override def to(t: T): Map[String, Value] = marshaller.to(t) match {
        case Value(MapValue(com.google.firestore.v1.MapValue(m))) => m
      }
    }
  }
}