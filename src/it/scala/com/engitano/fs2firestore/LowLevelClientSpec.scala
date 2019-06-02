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

import java.io.File
import java.util.UUID

import cats.effect._
import cats.implicits._
import com.engitano.fs2firestore.implicits._
import com.google.firestore.v1._
import io.grpc.Metadata
import org.scalatest._

import scala.concurrent.ExecutionContext
import scala.util.Try


class LowLevelClientSpec extends WordSpec with Matchers with DockerFirestoreService with BeforeAndAfterAll {

  val remoteConfig =
    (Option(System.getenv("GCP_PROJECT")), Try(new File(System.getenv("GCP_CREDENTIALS"))).toOption).mapN((proj, file) => FirestoreConfig(proj, file))
  object WhenRemoteConfigAvailable extends Tag(if (remoteConfig.isDefined) "" else classOf[Ignore].getName)

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  override def beforeAll(): Unit = {
    if (!Option(System.getenv("CIRCLECI")).exists(_.nonEmpty)) {
      startAllOrFail()
    }
  }

  override def afterAll(): Unit = {
    if (!Option(System.getenv("CIRCLECI")).exists(_.nonEmpty)) {
      stopAllQuietly()
    }
  }


  "The Generated clients" should {
    "be able to read and write to firestore" in {
      val testObj    = Person(UUID.randomUUID(), "mark", 30, Some(false), Seq("Iz"))
      val marshaller = DocumentMarshaller[Person]
      val fields     = marshaller.to(testObj)
      val cfg        = FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)

      val docId = UUID.randomUUID().toString
      val res = Client.resource[IO](cfg).use { c =>
        val doc = Document(fields = fields)

        val request = CreateDocumentRequest(
          documentId = docId,
          collectionId = "people",
          document = Some(doc),
          parent = s"projects/$DefaultGcpProject/databases/(default)/documents"
        )
        val getReq = GetDocumentRequest(
          s"projects/$DefaultGcpProject/databases/(default)/documents/people/$docId"
        )
        def metadata = new Metadata()

        for {
          saved   <- c.createDocument(request, metadata)
          fetched <- c.getDocument(getReq, metadata)
        } yield saved == fetched
      }

      res.unsafeRunSync() shouldBe true
    }
  }


}


