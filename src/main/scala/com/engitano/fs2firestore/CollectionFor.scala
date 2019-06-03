package com.engitano.fs2firestore

import org.atteo.evo.inflector.English

import scala.reflect.ClassTag
import scala.util.Try

object CollectionFor extends LowPriorityCollectionFor {
  def apply[T](implicit cf: CollectionFor[T]) = cf
}
trait CollectionFor[T] {
  def collectionName: String
}

trait LowPriorityCollectionFor {
  implicit def defaultCollectionFor[T: ClassTag] = new CollectionFor[T] {
    override def collectionName: String = {
      val className = implicitly[ClassTag[T]].runtimeClass.getSimpleName.replace("$1","").toLowerCase()
      Try(English.plural(className)).toEither.fold(_ => className, identity)
    }
  }
}
