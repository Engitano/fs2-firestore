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