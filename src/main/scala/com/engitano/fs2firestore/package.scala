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

package com.engitano

import java.io.{File, FileInputStream}

import cats.effect.{ConcurrentEffect, Resource}
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.firestore.admin.v1.FirestoreAdminFs2Grpc
import com.google.firestore.v1.{FirestoreFs2Grpc, Value}
import io.grpc.{CallCredentials, CallOptions, Channel, ManagedChannel, ManagedChannelBuilder}
import io.grpc.auth.MoreCallCredentials
import org.lyranthe.fs2_grpc.java_runtime.implicits._

package object fs2firestore {

  type DocumentFields = Map[String, Value]

  private val FIRESTORE_SERVICE_ID = "firestore.googleapis.com"

  object FirestoreConfig {
    def local(project: String, port: Int) : FirestoreConfig =
      new FirestoreConfig(project, s"localhost:$port", None, true)

    def apply(project: String): FirestoreConfig =
      new FirestoreConfig(project, FIRESTORE_SERVICE_ID,
        Some(MoreCallCredentials.from(GoogleCredentials.getApplicationDefault)))


    def apply(project: String, creds: File): FirestoreConfig =
      new FirestoreConfig(project, FIRESTORE_SERVICE_ID,
        Some(MoreCallCredentials.from(GoogleCredentials.fromStream(new FileInputStream(creds)))))

    def apply(project: String, credentials: Credentials): FirestoreConfig =
      new FirestoreConfig(project, FIRESTORE_SERVICE_ID, Some(MoreCallCredentials.from(credentials)))

    def apply(project: String, credentials: CallCredentials): FirestoreConfig =
      new FirestoreConfig(project, FIRESTORE_SERVICE_ID, Some(credentials))
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


  private def buildStub[F[_]: ConcurrentEffect, A[?[_]]](cfg: FirestoreConfig)(ctr: (Channel, CallOptions) => A[F]): Resource[F, A[F]] = {
    type MananagedChannelResourse[A] = Resource[F, A]

    val res: MananagedChannelResourse[ManagedChannel] = cfg.channelBuilder.resource[F]

    res.map(channel => ctr(channel, cfg.callOps))
  }

  object Client {
    def create[F[_]: ConcurrentEffect](cfg: FirestoreConfig): Resource[F, FirestoreFs2Grpc[F, io.grpc.Metadata]] =
      buildStub[F, FirestoreFs2Grpc[?[_],io.grpc.Metadata]](cfg)((ch, o) => FirestoreFs2Grpc.stub[F](ch, o))
  }

  object Admin {
    def create[F[_]: ConcurrentEffect](cfg: FirestoreConfig): Resource[F, FirestoreAdminFs2Grpc[F, io.grpc.Metadata]] =
      buildStub[F, FirestoreAdminFs2Grpc[?[_],io.grpc.Metadata]](cfg)((ch, o) => FirestoreAdminFs2Grpc.stub[F](ch, o))
  }
}
