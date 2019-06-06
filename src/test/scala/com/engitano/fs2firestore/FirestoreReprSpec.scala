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
