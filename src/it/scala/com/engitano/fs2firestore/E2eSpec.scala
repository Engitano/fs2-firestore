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

import cats.syntax._
import cats.effect._
import com.engitano.fs2firestore.api.WriteOperation
import com.engitano.fs2firestore.queries.{CollectionOf, QueryBuilder}
import com.google.firestore.v1._
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}
import io.grpc.Metadata
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.ExecutionContext

class E2eSpec extends WordSpec with Matchers with DockerFirestoreService with BeforeAndAfterAll {

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

  case class Person(id: UUID, name: String, age: Int, isFemale: Option[Boolean], kids: Seq[String])

  implicit val idFor = new IdFor[Person] {
    override def getId(t: Person): String = t.id.toString
  }

  "The Generated clients" should {
    "be able to read and write to firestore" in {
      val testObj    = Person(UUID.randomUUID(), "mark", 30, Some(false), Seq("Iz"))
      val marshaller = DocumentMarshaller[Person]
      val fields     = marshaller.to(testObj)
      val cfg        = FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)

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
          saved   <- c.createDocument(request, metadata)
          fetched <- c.getDocument(getReq, metadata)
        } yield saved == fetched
      }

      res.unsafeRunSync() shouldBe true
    }
  }

  "The high level client" should {
    "return None when no document exists in a collection" in {
      import DocumentMarshaller._
      val personF = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        client.getDocument[Person]("freddo")
      }

      personF.unsafeRunSync() shouldBe None
    }

    "save and read and update" in {
      import DocumentMarshaller._
      val id         = UUID.randomUUID()
      val testPerson = Person(id, "Nugget", 30, None, Seq())
      val personF = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        for {
          _      <- client.createDocument(testPerson)
          nugget <- client.getDocument[Person](id.toString)
          updated = nugget.get.right.get.copy(name = "Fred")
          _    <- client.putDocument(updated)
          fred <- client.getDocument[Person](id.toString)
        } yield (nugget, fred)
      }
    }

    "Streams data into firestore" ignore {
      import com.engitano.fs2firestore.queries.syntax._
      import DocumentMarshaller._
      import ValueMarshaller._
      def id         = UUID.randomUUID()
      implicit val idFor = IdFor.fromFunction[Person](_.id.toString)
      val people = fs2.Stream.emits[IO, WriteOperation.Update]((1 to 20).map(_ => Person(id, "Nugget", 30, None, Seq())).map(p => WriteOperation.update(p)))
      val query = QueryBuilder.from(CollectionOf[Person]).where { pb =>
        import pb._
        ('name =:= "Nugget")
      }.build
      val write = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        client.write(people).compile.drain
      }
      write.unsafeRunSync()

      val personF = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        client.runQuery(query).compile.toList
      }
      val res = personF.unsafeRunSync()

      res.collect {
        case Right(r) => r
      }.forall(_.name == "Nugget") shouldBe true
    }

    "runs queries" in {
      import com.engitano.fs2firestore.queries.syntax._
      import ValueMarshaller._
      import DocumentMarshaller._
      val id         = UUID.randomUUID()
      val testPerson = Person(id, "Nugget", 30, None, Seq())
      val query = QueryBuilder.from(CollectionOf[Person]).where { pb =>
        import pb._
        ('name =:= "Nugget") &&
          ('age :> 29) &&
          ('age :< 31)
      }.build
      val personF = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        val result = for {
          _      <- fs2.Stream.eval(client.createDocument(testPerson))
          nugget <- client.runQuery(query)
        } yield nugget

        result.compile.toList
      }
      personF.unsafeRunSync().head.right.get shouldBe testPerson
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