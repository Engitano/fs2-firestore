package com.engitano.fs2firestore

import java.util.UUID

import org.scalatest.{Matchers, WordSpec}

class IdForSpec extends WordSpec with Matchers {
  "The default implicit IdFor" should {
    "return an Id for case classes container Id properties" in {
      import IdFor._
      case class LowerId(id: String)
      case class CamelId(Id: String)
      case class UpperId(ID: String)
      case class Complex(_v: String, a: Int, b: UUID, id: String)
      case class NoId(name: String)
      case class WrongType(id: Int)
      val lower = LowerId("42")
      val camel = CamelId("42")
      val upper = UpperId("42")
      val complex = Complex("1", 2, UUID.randomUUID(), "42")
      val nada = NoId("42")
      val wrongType = WrongType(42)
      IdFor[LowerId].getId(lower) shouldBe "42"
      IdFor[CamelId].getId(camel) shouldBe "42"
      IdFor[UpperId].getId(upper) shouldBe "42"
      IdFor[Complex].getId(complex) shouldBe "42"

      assertDoesNotCompile(
        """
         IdFor[NoId].getId(nada) shouldBe "42"
        """)

      assertDoesNotCompile(
        """
         IdFor[WrongType].getId(wrongType) shouldBe "42"
        """)

    }
  }
}
