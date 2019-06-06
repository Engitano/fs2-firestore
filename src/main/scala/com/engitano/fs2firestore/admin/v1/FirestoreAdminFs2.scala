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

package com.engitano.fs2firestore.admin.v1

import cats.implicits._
import cats.effect.{ConcurrentEffect, Resource, Sync, Timer}
import cats.implicits._
import com.engitano.fs2firestore.{Admin, CollectionFor, FirestoreConfig}
import com.google.firestore.admin.v1.Index.IndexField
import com.google.firestore.admin.v1.{CreateIndexRequest, Index, ListIndexesRequest}
import io.grpc.Metadata
import shapeless.{HList, Witness}

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

trait FirestoreAdminFs2[F[_]] {
  def createIndex[C, R <: HList, K <: HList](c: CollectionFor[C], ix: IndexDefinition): F[Unit]
  def listIndexes[T: CollectionFor](): F[Seq[IndexDefinition]]
  def waitForIndex[T](c: CollectionFor[T], indexDefinition: IndexDefinition, pollInterval: FiniteDuration, timeout: FiniteDuration)(
    implicit T: Timer[F]
  ): F[Unit]
}

case class IndexNotFoundException(ix: IndexDefinition)         extends Exception
case class IndexInBadStateException(state: Index.State)        extends Exception
case class WaitForIndexTimeoutException(name: IndexDefinition) extends Exception

object FirestoreAdminFs2 {

  import com.engitano.fs2firestore.constraints._

  private def metadata = new Metadata()

  def stream[F[_]: ConcurrentEffect](cfg: FirestoreConfig): fs2.Stream[F, FirestoreAdminFs2[F]] =
    fs2.Stream.resource(resource[F](cfg))

  def resource[F[_]: ConcurrentEffect](cfg: FirestoreConfig): Resource[F, FirestoreAdminFs2[F]] =
    Admin.resource[F](cfg).map { client =>
      new FirestoreAdminFs2[F] {
        override def createIndex[C, R <: HList, K <: HList](c: CollectionFor[C], ix: IndexDefinition): F[Unit] =
          client
            .createIndex(
              CreateIndexRequest(
                cfg.collectionGroupPath(c.collectionName),
                Some(ix.toIndex)
              ),
              metadata
            )
            .as(())

        override def listIndexes[T: CollectionFor](): F[Seq[IndexDefinition]] =
          client
            .listIndexes(ListIndexesRequest(cfg.collectionGroupPath(CollectionFor[T].collectionName)), metadata)
            .map(_.indexes.map(_.toIndexDef))

        override def waitForIndex[T](c: CollectionFor[T], indexDefinition: IndexDefinition, pollInterval: FiniteDuration, timeout: FiniteDuration)(
            implicit T: Timer[F]
        ): F[Unit] =
          if (timeout.toMillis <= 0) {
            Sync[F].raiseError(WaitForIndexTimeoutException(indexDefinition))
          } else {
            getIndexes(c.collectionName).flatMap { ixs =>
              ixs.indexes
                .find(_.toIndexDef.fieldsWithout__name__.sameElements(indexDefinition.fieldsWithout__name__))
                .toRight(IndexNotFoundException(indexDefinition))
                .liftTo[F]
            } flatMap { ix =>
              ix.state match {
                case Index.State.READY    => Sync[F].delay(())
                case Index.State.CREATING => T.sleep(pollInterval).flatMap(_ => waitForIndex(c, indexDefinition, pollInterval, timeout - pollInterval))
                case s: Index.State       => Sync[F].raiseError(IndexInBadStateException(s))
              }
            }
          }

        private def getIndexes(collectionName: String) =
          client.listIndexes(ListIndexesRequest(cfg.collectionGroupPath(collectionName)), metadata)
      }
    }

  private implicit class PimpedIndex(ix: Index) {
    def toIndexDef = IndexDefinition(ix.fields.map(_.toIndexFieldDef))
  }

  private implicit class PimpedIndexField(ixf: IndexField) {
    def toIndexFieldDef =
      IndexFieldDefinition(ixf.fieldPath, ixf.valueMode == IndexField.ValueMode.Order(IndexField.Order.DESCENDING))
  }

  private implicit class PimpedIndexDef(ix: IndexDefinition) {
    def toIndex = Index(
      queryScope = Index.QueryScope.COLLECTION,
      fields = ix.fields.map(_.indexField)
    )
  }

  private implicit class PimpedIndexFieldDef(ixf: IndexFieldDefinition) {
    def indexField: IndexField =
      IndexField(
        ixf.name,
        if (ixf.isDescending)
          IndexField.ValueMode.Order(IndexField.Order.DESCENDING)
        else
          IndexField.ValueMode.Order(IndexField.Order.ASCENDING)
      )
  }
}
