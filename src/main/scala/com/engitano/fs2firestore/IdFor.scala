package com.engitano.fs2firestore

object IdFor {

  def apply[T](implicit n: IdFor[T]) = n

  def fromFunction[T](f: T => String) = new IdFor[T] {
    override def getId(t: T): String = f(t)
  }
}

trait IdFor[T] {
  def getId(t: T): String
}