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