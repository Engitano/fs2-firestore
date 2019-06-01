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

import java.util.UUID

import cats.implicits._
import cats.effect._
import cats.effect.Resource._
import com.engitano.fs2firestore.ValueMarshaller.UnmarshalResult
import com.engitano.fs2firestore.api._
import com.engitano.fs2firestore.queries.FieldOrder
import com.google.firestore.v1.BatchGetDocumentsResponse.Result
import com.google.firestore.v1.ListDocumentsRequest.ConsistencySelector
import com.google.firestore.v1.ListenRequest.TargetChange.AddTarget
import com.google.firestore.v1.ListenResponse.ResponseType
import com.google.firestore.v1.RunQueryRequest.QueryType
import com.google.firestore.v1.StructuredQuery.Filter
import com.google.firestore.v1.Target.DocumentsTarget
import com.google.firestore.v1._
import com.google.protobuf.ByteString
import fs2.{Chunk, Stream}
import fs2.concurrent.Queue
import io.grpc.{Metadata, StatusRuntimeException}

trait FirestoreFs2[F[_]] {
  def getDocument[T: FromDocumentFields: CollectionFor](docName: String): F[Option[UnmarshalResult[T]]]
  def listDocuments[T: FromDocumentFields: CollectionFor](
      pageSize: Option[Int] = None,
      pageToken: Option[String] = None,
      orderBy: Option[String] = None,
      mask: Option[DocumentMask] = None,
      showMissing: Boolean = false,
      consistencySelector: ConsistencySelector = ConsistencySelector.Empty
  ): F[Seq[UnmarshalResult[T]]]
  def createDocument[T: ToDocumentFields: CollectionFor: IdFor](t: T): F[Unit]
  def putDocument[T: ToDocumentFields: CollectionFor: IdFor](t: T): F[Unit]
  def deleteDocument(docName: String): F[Unit]
  def deleteDocument(collection: String, docId: String): F[Unit]
  def batchGetDocuments[T: FromDocumentFields: CollectionFor](docNames: List[String]): fs2.Stream[F, UnmarshalResult[T]]
  def beginTransaction(mode: TransactionOptions.Mode = TransactionOptions.Mode.Empty): F[ByteString]
  def commit(txId: ByteString, operations: Seq[WriteOperation]): F[Unit]
  def rollback(txId: ByteString): F[Unit]
  def runQuery[T: FromDocumentFields: CollectionFor](f: Query[T]): fs2.Stream[F, UnmarshalResult[T]]
  def write(request: fs2.Stream[F, WriteOperation], chunkSize: Int): fs2.Stream[F, Unit]
  def listenForDocChanges[T: FromDocumentFields](docIds: Seq[String]): fs2.Stream[F, UnmarshalResult[DocumentChanged[T]]]
  def listCollectionIds(): F[Seq[String]]
}

object api {

  case class Query[T: CollectionFor](predicate: Option[Filter],
                                     orderBy: Seq[FieldOrder],
                                     startAt: Seq[Value],
                                     endAt: Seq[Value],
                                     offset: Option[Int],
                                     skip: Option[Int]
                                     ) {
    def build = QueryType.StructuredQuery(
      StructuredQuery(
        None,
        Seq(StructuredQuery.CollectionSelector(CollectionFor[T].collectionName)),
        predicate,
        orderBy.map(
          p =>
            StructuredQuery.Order(
              Some(StructuredQuery.FieldReference(p.name)),
              if (p.isDescending) StructuredQuery.Direction.DESCENDING
              else StructuredQuery.Direction.ASCENDING
            )
        ),
        startAt.headOption.map(_ => Cursor(startAt)),
        endAt.headOption.map(_ => Cursor(endAt))
      )
    )
  }

  sealed trait WriteOperation
  object WriteOperation {

    case class Update(collection: String, docId: String, fields: Map[String, Value]) extends WriteOperation

    case class Delete(collection: String, docId: String) extends WriteOperation

    def update[T: ToDocumentFields: CollectionFor: IdFor](t: T) =
      WriteOperation.Update(CollectionFor[T].collectionName, IdFor[T].getId(t), ToDocumentFields[T].to(t))

    def delete[T: CollectionFor: IdFor](t: T) =
      WriteOperation.Delete(CollectionFor[T].collectionName, IdFor[T].getId(t))
  }

  sealed trait DocumentChangeType
  object DocumentChangeType {
    case object Updated extends DocumentChangeType
    case object Deleted extends DocumentChangeType
  }
  case class DocumentChanged[T](name: String, t: Option[T], changeType: DocumentChangeType)
}

object FirestoreFs2 {

  import constraints._

  def stream[F[_]: ConcurrentEffect](cfg: FirestoreConfig): Stream[F, FirestoreFs2[F]] =
    Stream.resource(resource[F](cfg))

  def resource[F[_]: ConcurrentEffect](cfg: FirestoreConfig): Resource[F, FirestoreFs2[F]] = {

    Client.create[F](cfg).map { client =>
      new FirestoreFs2[F] {

        private def metadata = new Metadata()

        override def getDocument[T: FromDocumentFields: CollectionFor](docName: String): F[Option[UnmarshalResult[T]]] =
          client
            .getDocument(GetDocumentRequest(cfg.documentName(docName)), metadata)
            .map(d => FromDocumentFields[T].from(d.fields).some)
            .recover {
              case e: StatusRuntimeException if e.getMessage.contains("NOT_FOUND") => None
            }

        override def listDocuments[T: FromDocumentFields: CollectionFor](
            pageSize: Option[Int] = None,
            pageToken: Option[String] = None,
            orderBy: Option[String] = None,
            mask: Option[DocumentMask] = None,
            showMissing: Boolean = false,
            consistencySelector: ConsistencySelector = ConsistencySelector.Empty
        ): F[Seq[UnmarshalResult[T]]] =
          client
            .listDocuments(
              new ListDocumentsRequest(
                cfg.rootDocuments,
                CollectionFor[T].collectionName,
                pageSize.getOrElse(0),
                pageToken.getOrElse(""),
                orderBy.getOrElse(""),
                mask,
                showMissing,
                consistencySelector
              ),
              metadata
            )
            .map(r => r.documents.map(d => FromDocumentFields[T].from(d.fields)))

        override def createDocument[T: ToDocumentFields: CollectionFor: IdFor](t: T): F[Unit] =
          client
            .createDocument(
              CreateDocumentRequest(
                cfg.rootDocuments,
                CollectionFor[T].collectionName,
                IdFor[T].getId(t),
                Some(Document(fields = ToDocumentFields[T].to(t))),
                Some(DocumentMask.fromFieldsMap(Map()))
              ),
              metadata
            )
            .as(())

        override def putDocument[T: ToDocumentFields: CollectionFor: IdFor](t: T): F[Unit] =
          client
            .updateDocument(
              UpdateDocumentRequest(
                Some(Document(cfg.documentName(t), ToDocumentFields[T].to(t)))
              ),
              metadata
            )
            .as(())

        override def deleteDocument(docName: String): F[Unit] =
          client
            .deleteDocument(DeleteDocumentRequest(docName), metadata)
            .as(())

        override def deleteDocument(collection: String, docId: String): F[Unit] =
          deleteDocument(cfg.documentName(collection, docId))
            .as(())

        override def batchGetDocuments[T: FromDocumentFields: CollectionFor](docNames: List[String]): fs2.Stream[F, UnmarshalResult[T]] =
          client
            .batchGetDocuments(BatchGetDocumentsRequest(cfg.database, docNames), metadata)
            .map(
              r =>
                r.result match {
                  case Result.Empty | _: Result.Missing => None
                  case Result.Found(d)                  => Some(d)
                }
            )
            .collect {
              case Some(d) => FromDocumentFields[T].from(d.fields)
            }

        override def beginTransaction(mode: TransactionOptions.Mode = TransactionOptions.Mode.Empty): F[ByteString] =
          client
            .beginTransaction(BeginTransactionRequest(cfg.database, Some(TransactionOptions(mode))), metadata)
            .map(_.transaction)

        override def commit(txId: ByteString, operations: Seq[WriteOperation]): F[Unit] = {
          client.commit(CommitRequest(cfg.database, operations.map(toWrite), txId), metadata).as(())
        }

        override def rollback(txId: ByteString): F[Unit] =
          client.rollback(RollbackRequest(cfg.database, txId), metadata).as(())

        override def runQuery[T: FromDocumentFields: CollectionFor](f: Query[T]): fs2.Stream[F, UnmarshalResult[T]] = {
          val q = RunQueryRequest(
            cfg.rootDocuments,
            f.build
          )
          client.runQuery(q, metadata).map(r => r.document.map(d => FromDocumentFields[T].from(d.fields))).collect {
            case Some(v) => v
          }
        }

        override def write(request: fs2.Stream[F, WriteOperation], chunkSize: Int): fs2.Stream[F, Unit] = {
          Stream.eval(Queue.unbounded[F, WriteRequest]).flatMap { queue =>
            def queueNext(w: WriteRequest): F[Unit] =
              queue.enqueue1(w).as(())

            val startup = Sync[F]
              .delay(UUID.randomUUID().toString)
              .flatMap(sid => queueNext(WriteRequest(cfg.rootDb, sid)))
            val doWrite = client.write(queue.dequeue, metadata).handleErrorWith {
              case t: StatusRuntimeException if t.getStatus.getCode == io.grpc.Status.CANCELLED.getCode => Stream.empty
              case t                                                                                    => Stream.raiseError[F](t)
            }

            (Stream.eval(startup) >> doWrite).zip(request.chunkN(chunkSize, true) ++ Stream.emit(Chunk.empty[WriteOperation])).evalMap {
              case (resp, req) => queueNext(WriteRequest(cfg.rootDb, streamToken = resp.streamToken, writes = req.map(toWrite).toList))
            }
          }
        }

        override def listenForDocChanges[T: FromDocumentFields](docIds: Seq[String]): fs2.Stream[F, UnmarshalResult[DocumentChanged[T]]] =
          client
            .listen(
              Stream.emit[F, ListenRequest](
                ListenRequest(cfg.database, targetChange = AddTarget(Target(targetType = Target.TargetType.Documents(DocumentsTarget(docIds)))))
              ),
              metadata
            )
            .collect {
              case ListenResponse(ResponseType.DocumentChange(DocumentChange(Some(document), _, _))) =>
                FromDocumentFields[T].from(document.fields).map { d =>
                  DocumentChanged[T](document.name, Some(d), DocumentChangeType.Updated)
                }
              case ListenResponse(ResponseType.DocumentDelete(DocumentDelete(name, _, _))) =>
                Right(DocumentChanged[T](name, None, DocumentChangeType.Deleted))

            }

        override def listCollectionIds(): F[Seq[String]] =
          client.listCollectionIds(ListCollectionIdsRequest(cfg.rootDocuments), metadata).map(_.collectionIds)

        private def toWrite(wo: WriteOperation): Write = wo match {
          case WriteOperation.Update(c, id, f) => Write(operation = Write.Operation.Update(Document(name = cfg.documentName(c, id), fields = f)))
          case WriteOperation.Delete(c, id)    => Write(operation = Write.Operation.Delete(cfg.documentName(c, id)))
        }
      }
    }
  }
}
