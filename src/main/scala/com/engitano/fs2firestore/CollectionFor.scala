package com.engitano.fs2firestore

import scala.reflect.ClassTag

object CollectionFor extends LowPriorityCollectionFor {
  def apply[T](implicit cf: CollectionFor[T]) = cf
}
trait CollectionFor[T] {
  def collectionName: String
}

trait LowPriorityCollectionFor {
  implicit def defaultCollectionFor[T: ClassTag] = new CollectionFor[T] {
    override def collectionName: String = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase
  }
}
