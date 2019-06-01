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

import cats.implicits._
import cats.effect.{ConcurrentEffect, Resource}
import com.google.firestore.admin.v1.FirestoreAdminFs2Grpc
import com.google.firestore.v1.{FirestoreFs2Grpc, Value}
import io.grpc.{CallOptions, Channel, ManagedChannel}
import org.lyranthe.fs2_grpc.java_runtime.implicits._
import shapeless.Witness

package object fs2firestore {

  type DocumentFields = Map[String, Value]


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

  private[fs2firestore] object SymbolHelpers {
    def keyOf[A](implicit wt: Witness.Aux[A]): String = asKeyName(wt.value)

    def asKeyName(a: Any): String = {
      a match {
        case sym: Symbol => sym.name
        case other       => other.toString
      }
    }
  }
}
