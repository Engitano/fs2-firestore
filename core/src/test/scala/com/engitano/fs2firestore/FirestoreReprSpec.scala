package com.engitano.fs2firestore

import java.util.UUID

import org.scalatest.{FlatSpec, Matchers, WordSpec}

case class Person(id: UUID, name: String, isFemale: Option[Boolean], kids: Seq[Person])

class FirestoreReprSpec extends WordSpec with Matchers {

  "The Repr Code" should {
    "turn an entity into a value" in {
      val v = ValueMarshaller[Person]
      val testObj = Person(UUID.randomUUID(), "mark", Some(false), Seq())
      v.from(v.to(testObj)) shouldEqual Right(testObj)
    }

    "turn an entity into dock fields" in {
      val v = DocumentMarshaller[Person]
      val testObj = Person(UUID.randomUUID(), "mark", Some(false), Seq(Person(UUID.randomUUID(), "Iz", Some(true), Seq())))
      println(v.to(testObj))
      v.from(v.to(testObj)) shouldEqual Right(testObj)
    }
  }
}
