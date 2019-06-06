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
