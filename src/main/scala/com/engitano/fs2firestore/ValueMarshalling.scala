package com.engitano.fs2firestore

import java.util.UUID

import cats.implicits._
import com.engitano.fs2firestore.ValueMarshaller.UnmarshalResult
import com.google.firestore.v1.Value
import com.google.firestore.v1.Value.ValueTypeOneof
import shapeless.labelled.{FieldBuilder, FieldType}
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

import scala.collection.immutable.Map
import scala.util.Try

object ToFirestoreValue {
  def apply[T](implicit tfv: ToFirestoreValue[T]) = tfv
}

trait ToFirestoreValue[T] {
  def to(t: T): Value
}

object FromFirestoreValue {
  def apply[T](implicit f: FromFirestoreValue[T]) = f
}

trait FromFirestoreValue[T] {
  def from(v: Value): UnmarshalResult[T]
}

trait ValueMarshaller[T]
  extends ToFirestoreValue[T]
    with FromFirestoreValue[T]


object ValueMarshaller {
  type UnmarshalResult[T] = Either[FirestoreUnmarshallingException, T]
  type MaybeValueToT[T]   = PartialFunction[ValueTypeOneof, T]

  def apply[T](implicit fm: ValueMarshaller[T]): ValueMarshaller[T] = fm

  def bimap[T](
                tf: T => ValueTypeOneof
              )(ff: MaybeValueToT[T]): ValueMarshaller[T] =
    bimapOr(tf) {
      case r => Right(ff(r))
    }

  def bimapOr[T](tf: T => ValueTypeOneof)(
    ff: PartialFunction[ValueTypeOneof, UnmarshalResult[T]]
  ): ValueMarshaller[T] = new ValueMarshaller[T] {
    override def to(t: T): Value = Value(tf(t))

    override def from(v: Value): UnmarshalResult[T] =
      ff.lift(v.valueType) match {
        case Some(s) => s
        case None =>
          Left(
            FirestoreUnmarshallingException(
              s"Cannot convert value $v to expected type"
            )
          )
      }
  }
}

trait LowPriorityValueMarshallers {

  import Value.ValueTypeOneof._

  implicit def stringMarshaller: ValueMarshaller[String] =
    ValueMarshaller.bimap[String](s => StringValue(s)) {
      case StringValue(s) => s
    }

  implicit def intMarshaller: ValueMarshaller[Int] =
    ValueMarshaller.bimap[Int](i => IntegerValue(i)) {
      case IntegerValue(s) => s.toInt
    }

  implicit def longMarshaller: ValueMarshaller[Long] =
    ValueMarshaller.bimap[Long](i => IntegerValue(i)) {
      case IntegerValue(s) => s
    }

  implicit def floatMarshaller: ValueMarshaller[Float] =
    ValueMarshaller.bimap[Float](i => DoubleValue(i)) {
      case DoubleValue(s) => s.toFloat
    }

  implicit def doubleMarshaller: ValueMarshaller[Double] =
    ValueMarshaller.bimap[Double](i => DoubleValue(i)) {
      case DoubleValue(s) => s
    }

  implicit def boolMarshaller: ValueMarshaller[Boolean] =
    ValueMarshaller.bimap[Boolean](i => BooleanValue(i)) {
      case BooleanValue(s) => s
    }

  implicit def uuidMarshaller: ValueMarshaller[UUID] =
    ValueMarshaller.bimapOr[UUID](i => StringValue(i.toString)) {
      case StringValue(s) =>
        Try(UUID.fromString(s)).toEither
          .leftMap(c => FirestoreUnmarshallingException(c.getMessage))
    }

  implicit def optionMarshaller[T](
                                    implicit fm: ValueMarshaller[T]
                                  ): ValueMarshaller[Option[T]] = new ValueMarshaller[Option[T]] {
    override def from(v: Value): UnmarshalResult[Option[T]] =
      v match {
        case Value(NullValue(_)) => Right(None)
        case v                   => fm.from(v).map(v => Some(v))
      }

    override def to(t: Option[T]): Value = t match {
      case None =>
        Value(NullValue(com.google.protobuf.struct.NullValue.NULL_VALUE))
      case Some(v) => fm.to(v)
    }
  }

  implicit def listMarshaller[T](
                                  implicit fm: ValueMarshaller[T]
                                ): ValueMarshaller[Seq[T]] = new ValueMarshaller[Seq[T]] {
    override def from(v: Value): UnmarshalResult[Seq[T]] = v match {
      case Value(ArrayValue(com.google.firestore.v1.ArrayValue(v))) =>
        v.toList.traverse[UnmarshalResult, T](x => fm.from(x))
      case v =>
        Left(
          FirestoreUnmarshallingException(s"Cannot marshall firestore value $v")
        )
    }

    override def to(t: Seq[T]): Value =
      Value(ArrayValue(com.google.firestore.v1.ArrayValue(t.map(fm.to))))
  }


  implicit val hnilMarshaller = new ValueMarshaller[HNil] {
    override def from(v: Value): UnmarshalResult[HNil] = v.valueType match {
      case MapValue(com.google.firestore.v1.MapValue(m)) if m.isEmpty =>
        Right(HNil)
    }

    override def to(t: HNil): Value =
      Value(MapValue(com.google.firestore.v1.MapValue(Map[String, Value]())))
  }

  implicit def hconsMarshaller[Key <: Symbol, Head, Tail <: HList](
                                                                    implicit key: Witness.Aux[Key],
                                                                    hm: Lazy[ValueMarshaller[Head]],
                                                                    tm: Lazy[ValueMarshaller[Tail]]
                                                                  ): ValueMarshaller[FieldType[Key, Head] :: Tail] =
    new ValueMarshaller[FieldType[Key, Head] :: Tail] {
      override def from(
                         v: Value
                       ): UnmarshalResult[FieldType[Key, Head] :: Tail] = v match {
        case Value(MapValue(com.google.firestore.v1.MapValue(m))) => {
          tm.value.from(
            Value(
              MapValue(com.google.firestore.v1.MapValue(m - key.value.name))
            )
          )
            .flatMap { t =>
              hm.value.from(m(key.value.name))
                .map(h => new FieldBuilder[Key].apply(h) :: t)
            }
        }
      }

      override def to(t: FieldType[Key, Head] :: Tail): Value =
        tm.value.to(t.tail) match {
          case Value(MapValue(com.google.firestore.v1.MapValue(m))) =>
            Value(
              MapValue(
                com.google.firestore.v1
                  .MapValue(m + (key.value.name -> hm.value.to(t.head)))
              )
            )
        }
    }

  implicit def fromGen[T, R](
                              implicit g: LabelledGeneric.Aux[T, R],
                              r: Lazy[ValueMarshaller[R]],
                            ): ValueMarshaller[T] = new ValueMarshaller[T] {
    override def from(v: Value): UnmarshalResult[T] =
      r.value.from(v).map(g.from)

    override def to(t: T): Value = r.value.to(g.to(t))
  }
}