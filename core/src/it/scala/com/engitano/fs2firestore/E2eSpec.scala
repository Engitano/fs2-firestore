/*
 * Copyright 2019 Engitano
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.engitano.fs2firestore

import java.util.UUID

import cats.effect._
import com.google.firestore.v1.{
  CreateDocumentRequest,
  Document,
  GetDocumentRequest,
  Value
}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{
  DockerContainer,
  DockerFactory,
  DockerKit,
  DockerReadyChecker
}
import io.grpc.Metadata
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.ExecutionContext

class E2eSpec
    extends WordSpec
    with Matchers
    with DockerFirestoreService
    with BeforeAndAfterAll {

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  override def beforeAll(): Unit = {
    startAllOrFail()
  }

  override def afterAll(): Unit = {
    stopAllQuietly()
  }

  case class Person(id: UUID, name: String, isFemale: Option[Boolean], kids: Seq[Person])


  "The Generated clients" should {
    "be able to read and write to firestore" in {
      val testObj = Person(UUID.randomUUID(), "mark", Some(false), Seq(Person(UUID.randomUUID(), "Iz", Some(true), Seq())))
      val marshaller = DocumentMarshaller[Person]
      val fields = marshaller.to(testObj)
      val cfg = FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)

      val docId = UUID.randomUUID().toString
      val res = Client.create[IO](cfg).use { c =>
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
          saved <- c.createDocument(request, metadata)
          fetched <- c.getDocument(getReq, metadata)
        } yield saved == fetched
      }

      res.unsafeRunSync() shouldBe true
    }
  }

}

trait DockerFirestoreService extends DockerKit {

  val DefaultPubsubPort = 8080

  val DefaultGcpProject = "test-project"

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()

  override implicit def dockerFactory: DockerFactory =
    new SpotifyDockerFactory(client)

  val firestore = DockerContainer("pathmotion/firestore-emulator-docker:latest")
    .withPorts(DefaultPubsubPort -> Some(DefaultPubsubPort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("running"))
    .withEnv(s"FIRESTORE_PROJECT_ID=$DefaultGcpProject")
    .withCommand("--log-http")

  abstract override def dockerContainers = firestore +: super.dockerContainers
}
