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

import cats.implicits._
import cats.effect.{ContextShift, IO}
import com.engitano.fs2firestore.admin.v1.{FirestoreAdminFs2, IndexBuilder}
import com.engitano.fs2firestore.api.WriteOperation
import com.engitano.fs2firestore.queries.QueryBuilder
import com.engitano.fs2firestore.implicits._
import com.engitano.fs2firestore.queries.syntax._
import org.scalatest.{BeforeAndAfterAll, Ignore, Matchers, Tag, WordSpec}
import shapeless._
import shapeless.syntax.singleton._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext
import scala.util.Try

class FirestoreFs2Spec extends WordSpec with Matchers with DockerFirestoreService with BeforeAndAfterAll {

  val remoteConfig =
    (Option(System.getenv("GCP_PROJECT")), Try(new File(System.getenv("GCP_CREDENTIALS"))).toOption)
      .mapN((proj, file) => FirestoreConfig(proj, file))
  object whenRemoteConfigAvailable extends Tag(if (remoteConfig.isDefined) "" else classOf[Ignore].getName)

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

  "The high level client" should {
    "return None when no document exists in a collection" in {
      val personF = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        client.getDocument[Person]("freddo")
      }

      personF.unsafeRunSync() shouldBe None
    }

    "save and read and update" in {
      val id         = UUID.randomUUID()
      val person = Person(id, "Nugget", 30, None, Seq())
      val personF = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        for {
          _      <- client.createDocument(person)
          nugget <- client.getDocument[Person](id.toString)
          updated = nugget.get.right.get.copy(name = "Fred")
          _    <- client.putDocument(updated)
          fred <- client.getDocument[Person](id.toString)
        } yield (nugget, fred)
      }
      val run = personF.unsafeRunSync()
      run._1.get.right.get shouldBe person
      run._2.get.right.get shouldBe person.copy(name = "Fred")
    }

    "Streams data into firestore" taggedAs whenRemoteConfigAvailable in {
      def id             = UUID.randomUUID()
      implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)

      val people = fs2.Stream.emits[IO, WriteOperation.Update](
        (1 to 100)
          .map(i => Person(id, "Nugget", i, None, Seq()))
          .map(p => WriteOperation.update(p))
      )

      val query = QueryBuilder
        .from(CollectionFor[Person])
        .where({ pb =>
          import pb._
          ('name =:= "Nugget") &&
          ('age :>= 90) &&
          ('age :< 99)
        })
        .addOrderBy('age)
        .withStartAt(('age ->> 95) :: HNil)
        .build

      val index = IndexBuilder.withColumn(CollectionFor[Person], 'name).withColumn('age).build

      val personF = (FirestoreAdminFs2.resource[IO](remoteConfig.get), FirestoreFs2.resource[IO](remoteConfig.get)).tupled.use {
        case (admin, client) =>
          for {
            indexes <- admin.listIndexes[Person]()
            _ <- if (indexes.exists(_.fieldsWithout__name__.sameElements(index.fieldsWithout__name__)))
              IO.unit
            else
              admin
                .createIndex(CollectionFor[Person], index) *> admin.waitForIndex(CollectionFor[Person], index, 250 millis, 2 minutes)
            peeps <- (client.write(people, 50) >> client.runQuery(query)).compile.toList
          } yield peeps
      }

      val res = personF.unsafeRunSync()

      val okVals = res
        .collect {
          case Right(r) => r
        }
      okVals.nonEmpty shouldBe true
      okVals.forall(p => p.name == "Nugget" && p.age >= 95 && p.age < 99) shouldBe true
    }

    "runs queries" in {
      val id         = UUID.randomUUID()
      val person = Person(id, "Nugget", 30, None, Seq())

      val personF = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        val query = QueryBuilder
          .from(CollectionFor[Person])
          .where { pb =>
            import pb._
            ('name =:= "Nugget") &&
            ('age :> 29) &&
            ('age :< 31)
          }
          .build

        val result = for {
          _      <- fs2.Stream.eval(client.createDocument(person))
          nugget <- client.runQuery(query)
        } yield nugget

        result.compile.toList
      }
      personF.unsafeRunSync().head.right.get shouldBe person
    }
  }
}
