package com.engitano.fs2firestore

import java.io.{File, FileInputStream}

import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import io.grpc.{CallCredentials, CallOptions, ManagedChannelBuilder}
import io.grpc.auth.MoreCallCredentials

object FirestoreConfig {

  private val FIRESTORE_SERVICE_ID = "firestore.googleapis.com"

  def local(project: String, port: Int) : FirestoreConfig =
    FirestoreConfig(project, s"localhost:$port", None, true)

  def apply(project: String): FirestoreConfig =
    FirestoreConfig(project, FIRESTORE_SERVICE_ID,
      Some(MoreCallCredentials.from(GoogleCredentials.getApplicationDefault)))

  def apply(project: String, creds: File): FirestoreConfig =
    FirestoreConfig(project, FIRESTORE_SERVICE_ID,
      Some(MoreCallCredentials.from(GoogleCredentials.fromStream(new FileInputStream(creds)))))

  def apply(project: String, credentials: Credentials): FirestoreConfig =
    FirestoreConfig(project, FIRESTORE_SERVICE_ID, Some(MoreCallCredentials.from(credentials)))

  def apply(project: String, credentials: CallCredentials): FirestoreConfig =
    FirestoreConfig(project, FIRESTORE_SERVICE_ID, Some(credentials))


  implicit def pathHelpersFromConfig(cfg: FirestoreConfig) = new PathHelpers(cfg)

  private[fs2firestore] class PathHelpers(cfg: FirestoreConfig) {
    def rootDb = s"projects/${cfg.project}/databases/${cfg.database}"

    def rootDocuments = s"projects/${cfg.project}/databases/${cfg.database}/documents"

    def collectionGroupPath(collectionId: String) =
      s"$rootDb/collectionGroups/$collectionId"

    def collectionPath(collectionId: String) =
      s"${rootDocuments}/$collectionId"

    def collectionPath[T: CollectionFor]: String = collectionPath(CollectionFor[T].collectionName)

    def documentName[T: CollectionFor : IdFor](t: T): String =
      documentName[T](IdFor[T].getId(t))

    def documentName[T: CollectionFor](id: String): String = documentName(CollectionFor[T].collectionName, id)

    def documentName(collectionId: String, id: String) =
      s"$rootDocuments/$collectionId/$id"
  }
}

case class FirestoreConfig(project: String, host: String, credentials: Option[CallCredentials], plainText: Boolean = false, database: String = "(default)") {


  private[fs2firestore] def callOps = credentials.foldLeft(CallOptions.DEFAULT)(_ withCallCredentials _)
  private[fs2firestore] def channelBuilder = {
    val channel = ManagedChannelBuilder.forTarget(host)
    if(plainText) {
      channel.usePlaintext()
    } else {
      channel
    }
  }
}