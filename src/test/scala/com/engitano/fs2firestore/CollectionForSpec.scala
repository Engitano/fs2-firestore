package com.engitano.fs2firestore

import org.scalatest.{Matchers, WordSpec}

class CollectionForSpec extends WordSpec with Matchers {
  "The Default CollectionFor" should {
    "return sensible collection names" in {
      case class Camel(id: String)
      CollectionFor[Camel].collectionName shouldBe "camels"
    }
    "return sensible collection names for irregular plurals" in {
      case class GreenTooth(id: String)
      CollectionFor[GreenTooth].collectionName shouldBe "greenteeth"
    }
  }

}
