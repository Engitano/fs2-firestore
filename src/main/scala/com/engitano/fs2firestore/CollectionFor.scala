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
