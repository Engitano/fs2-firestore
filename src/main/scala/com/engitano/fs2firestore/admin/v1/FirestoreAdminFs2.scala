package com.engitano.fs2firestore.admin.v1

import cats.effect.{ConcurrentEffect, Resource}
import cats.implicits._
import com.engitano.fs2firestore.{Admin, FirestoreConfig}
import com.google.firestore.admin.v1.Index.IndexField
import com.google.firestore.admin.v1.{CreateIndexRequest, Index}
import io.grpc.Metadata

trait FirestoreAdminFs2[F[_]] {
  def createSimpleIndex(collectionName: String, field: String, descending: Boolean = false, indexName: Option[String] = None): F[Unit]
}

object FirestoreAdminFs2 {

  private def metadata = new Metadata()

  def resource[F[_]: ConcurrentEffect](cfg: FirestoreConfig): Resource[F, FirestoreAdminFs2[F]] =
    Admin.create[F](cfg).map { client =>
      new FirestoreAdminFs2[F] {
        override def createSimpleIndex(collectionName: String, field: String, descending: Boolean = false, indexName: Option[String] = None): F[Unit] =
          client
            .createIndex(
              CreateIndexRequest(
                indexName.getOrElse(s"${collectionName}_$field"),
                Some(
                  Index(
                    queryScope = Index.QueryScope.COLLECTION,
                    fields = Seq(
                      IndexField(
                        field,
                        IndexField.ValueMode.Order(
                          if (descending) IndexField.Order.ASCENDING
                          else IndexField.Order.DESCENDING
                        )
                      )
                    )
                  )
                )
              ),
              metadata
            )
            .as(())
      }
    }

}
