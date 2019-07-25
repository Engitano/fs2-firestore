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

import cats.syntax.option._
import com.engitano.fs2firestore.api.Query
import com.engitano.fs2firestore.implicits._
import com.engitano.fs2firestore.queries.{FieldOrder, QueryBuilder}
import com.google.firestore.v1.StructuredQuery.CompositeFilter.Operator.AND
import com.google.firestore.v1.StructuredQuery.FieldFilter.Operator.{ARRAY_CONTAINS, EQUAL, LESS_THAN}
import com.google.firestore.v1.StructuredQuery.Filter.FilterType.{CompositeFilter, FieldFilter, UnaryFilter}
import com.google.firestore.v1.StructuredQuery.{FieldReference, Filter}
import com.google.firestore.v1.Value.ValueTypeOneof.{IntegerValue, StringValue}
import com.google.firestore.v1.{StructuredQuery, Value}
import org.scalatest.{Matchers, WordSpec}
import shapeless.HNil
import shapeless.syntax.singleton._
import com.engitano.fs2firestore.queries.syntax._

class QuerySpec extends WordSpec with Matchers {

  case class User(id: String, email: String, name: String, dob: Long, accountIds: Seq[String])

  "QueryBuilder" should {
    "build compile a valid query with tupled paging" in {

      val queryByNameAndAge = QueryBuilder
        .from(CollectionFor[User])
        .addOrderBy('name)
        .addOrderBy('dob)
        .withStartAt("Allan", 15L)
        .withEndAt("Zeta-Jones", 95L)
        .where({ pb =>
          import pb._
          ('name =:= "Nugget") &&
          ('dob :< 18L) &&
          ('email isNull) &&
          ('accountIds contains "123")
        })

      queryByNameAndAge.build shouldBe Query[User](
        Some(
          Filter(
            CompositeFilter(
              StructuredQuery.CompositeFilter(
                AND,
                List(
                  Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("name")), EQUAL, Some(Value(StringValue("Nugget")))))),
                  Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("dob")), LESS_THAN, Some(Value(IntegerValue(18)))))),
                  Filter(
                    UnaryFilter(
                      StructuredQuery
                        .UnaryFilter(StructuredQuery.UnaryFilter.Operator.IS_NULL, StructuredQuery.UnaryFilter.OperandType.Field(FieldReference("email")))
                    )
                  ),
                  Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("accountIds")), ARRAY_CONTAINS, Some(Value(StringValue("123"))))))
                )
              )
            )
          )
        ),
        Seq(FieldOrder("name", false), FieldOrder("dob", false)),
        Seq(Value(StringValue("Allan")), Value(IntegerValue(15))),
        Seq(Value(StringValue("Zeta-Jones")), Value(IntegerValue(95))),
        None,
        None
      )
    }
    "build compile a valid query with scalar paging" in {

      val queryByNameAndAge = QueryBuilder
        .from(CollectionFor[User])
        .addOrderBy('name, true)
        .withStartAt("Allan")
        .withEndAt("Zeta-Jones")
        .where({ pb =>
          import pb._
          ('name =:= "Nugget") &&
          ('dob :< 18L) &&
          ('email isNull) &&
          ('accountIds contains "123")
        })

      queryByNameAndAge.build shouldBe Query[User](
        Some(
          Filter(
            CompositeFilter(
              StructuredQuery.CompositeFilter(
                AND,
                List(
                  Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("name")), EQUAL, Some(Value(StringValue("Nugget")))))),
                  Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("dob")), LESS_THAN, Some(Value(IntegerValue(18)))))),
                  Filter(
                    UnaryFilter(
                      StructuredQuery
                        .UnaryFilter(StructuredQuery.UnaryFilter.Operator.IS_NULL, StructuredQuery.UnaryFilter.OperandType.Field(FieldReference("email")))
                    )
                  ),
                  Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("accountIds")), ARRAY_CONTAINS, Some(Value(StringValue("123"))))))
                )
              )
            )
          )
        ),
        Seq(FieldOrder("name", true)),
        Seq(Value(StringValue("Allan"))),
        Seq(Value(StringValue("Zeta-Jones"))),
        None,
        None
      )
    }
  }
  "PredicateBuilder constraints" should {
    "Not compile invalid property names" when {
      "using conditional operators" in {
        assertCompiles("""
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('name =:= "Nugget")
        }
      """)

        assertDoesNotCompile("""
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('names =:= "Nugget")
        }
      """)
      }

      "using unary operators" in {
        assertCompiles("""
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('age isNull)
        }
      """)

        assertDoesNotCompile("""
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('ages isNull)
        }
      """)
      }

    }
    "Not compile invalid types" when {
      "using conditional operators" in {

        assertCompiles("""
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('name =:= "Nugget")
        }
      """)

        assertDoesNotCompile("""
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('name =:= 123)
        }
      """)
      }
    }
    "Paging constraints" should {
      "prevent compilation" when {
        "order key not present in collection" in {
          assertCompiles("""
          QueryBuilder
            .from(CollectionFor[QueryTest])
            .addOrderBy('name)
          """)

          assertDoesNotCompile("""
          QueryBuilder
            .from(CollectionFor[QueryTest])
            .addOrderBy('name123)
          """)
        }
        "paging start properties not present in order clause" in {
          assertCompiles("""
          QueryBuilder
            .from(CollectionFor[QueryTest])
            .addOrderBy('name)
            .addOrderBy('age)
            .withStartAt(('name ->> "Alpha") :: ('age ->> Some(1)) :: HNil)
          """)

          assertDoesNotCompile("""
           QueryBuilder
             .from(CollectionFor[QueryTest])
             .addOrderBy('name)
             .addOrderBy('age)
             .withStartAt(('names ->> "Alpha") :: ('age ->> Some(1)) :: HNil)
          """)
        }

        "paging start properties do not cover full order clause" in {
          assertCompiles("""
          QueryBuilder
            .from(CollectionFor[QueryTest])
            .addOrderBy('name)
            .addOrderBy('age)
            .withStartAt(('name ->> "Alpha") :: ('age ->> Some(1)) :: HNil)
          """)

          assertDoesNotCompile("""
           QueryBuilder
             .from(CollectionFor[QueryTest])
             .addOrderBy('name)
             .addOrderBy('age)
             .withStartAt(('name ->> "Alpha") :: HNil)
          """)
        }

        "paging start propertiy types are not compatible with collection types" in {
          assertCompiles("""
          QueryBuilder
            .from(CollectionFor[QueryTest])
            .addOrderBy('name)
            .addOrderBy('age)
            .withStartAt(('name ->> "Alpha") :: ('age ->> Some(1)) :: HNil)
          """)

          assertDoesNotCompile("""
           QueryBuilder
             .from(CollectionFor[QueryTest])
             .addOrderBy('name)
             .addOrderBy('age)
             .withStartAt(('name ->> 1) :: ('age ->> Some(1)) :: HNil)
          """)
        }
      }
    }
  }
}
