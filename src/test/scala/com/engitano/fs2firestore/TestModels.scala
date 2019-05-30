package com.engitano.fs2firestore

import java.util.UUID

import com.google.`type`.LatLng

case class QueryTest(id: String, name: String, age: Option[Int], kids: Seq[String])


case class TestPerson(id: UUID, name: String, isFemale: Option[Boolean], homeLocation: LatLng, kids: Seq[TestPerson])