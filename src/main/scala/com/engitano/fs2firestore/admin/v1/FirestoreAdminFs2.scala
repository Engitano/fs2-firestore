package com.engitano.fs2firestore.admin.v1

import cats.effect.{ConcurrentEffect, Resource}
import cats.implicits._
import com.engitano.fs2firestore.{Admin, CollectionFor, FirestoreConfig}
import com.google.firestore.admin.v1.Index.IndexField
import com.google.firestore.admin.v1.{CreateIndexRequest, Index, ListIndexesRequest}
import io.grpc.Metadata
import shapeless.{HList, Witness}

object SymbolHelpers {
  def keyOf[A](implicit wt:Witness.Aux[A]):String = asKeyName(wt.value)

  def asKeyName(a:Any):String = {
    a match {
      case sym:Symbol => sym.name
      case other => other.toString
    }
  }
}

trait FirestoreAdminFs2[F[_]] {
  def createIndex[C, R <: HList, K<: HList](c: CollectionFor[C], ix: IndexDefinition): F[Unit]
  def listIndexes(collectionName: String): F[Seq[IndexDefinition]]
}

object FirestoreAdminFs2 {

  import com.engitano.fs2firestore.constraints._

  private def metadata = new Metadata()

  def stream[F[_]: ConcurrentEffect](cfg: FirestoreConfig): fs2.Stream[F, FirestoreAdminFs2[F]] =
    fs2.Stream.resource(resource[F](cfg))

  def resource[F[_]: ConcurrentEffect](cfg: FirestoreConfig): Resource[F, FirestoreAdminFs2[F]] =
    Admin.create[F](cfg).map { client =>
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

        override def listIndexes(collectionName: String): F[Seq[IndexDefinition]] =
          client.listIndexes(ListIndexesRequest(cfg.collectionGroupPath(collectionName)), metadata)
            .map(_.indexes.map(_.toIndexDef))
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
    def indexField: IndexField = IndexField(ixf.name, if(ixf.isDescending)
      IndexField.ValueMode.Order(IndexField.Order.DESCENDING)
    else
      IndexField.ValueMode.Order(IndexField.Order.ASCENDING))
  }
}
