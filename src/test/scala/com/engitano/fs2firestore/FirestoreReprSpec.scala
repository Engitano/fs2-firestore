package com.engitano.fs2firestore

import java.util.UUID

import com.google.`type`.LatLng
import org.scalatest.{Matchers, WordSpec}
import com.engitano.fs2firestore.implicits._


class FirestoreReprSpec extends WordSpec with Matchers {

  "The Repr Code" should {
    "turn an entity into a value" in {
      val v = ValueMarshaller[TestPerson]
      val testObj = TestPerson(UUID.randomUUID(), "mark", Some(false), LatLng(-33.8688, 151.2093), Seq())
      v.from(v.to(testObj)) shouldEqual Right(testObj)
    }

    "turn an entity into dock fields" in {
      val v = DocumentMarshaller[TestPerson]
      val testObj = TestPerson(UUID.randomUUID(), "mark", Some(false), LatLng(-33.8688, 151.2093), Seq(TestPerson(UUID.randomUUID(), "Iz", Some(true), LatLng(-33.8688, 151.2093), Seq())))
      v.from(v.to(testObj)) shouldEqual Right(testObj)
    }
  }
}
