package com.engitano.fs2firestore

import com.engitano.fs2firestore.api.Query
import com.engitano.fs2firestore.implicits._
import com.engitano.fs2firestore.queries.{FieldOrder, QueryBuilder}
import com.google.firestore.v1.StructuredQuery.CompositeFilter.Operator.AND
import com.google.firestore.v1.StructuredQuery.FieldFilter.Operator.{ARRAY_CONTAINS, EQUAL}
import com.google.firestore.v1.StructuredQuery.Filter.FilterType.{CompositeFilter, FieldFilter, UnaryFilter}
import com.google.firestore.v1.StructuredQuery.{FieldReference, Filter}
import com.google.firestore.v1.Value.ValueTypeOneof.{IntegerValue, StringValue}
import com.google.firestore.v1.{StructuredQuery, Value}
import org.scalatest.{Matchers, WordSpec}
import shapeless.HNil
import shapeless.syntax.singleton._

import com.engitano.fs2firestore.queries.syntax._

class QuerySpec extends WordSpec with Matchers {

  "QueryBuild" should {
    "build compile a valid query" in {


      val nameQuery = QueryBuilder
        .from(CollectionFor[QueryTest])
        .addOrderBy('name)
        .addOrderBy('age)
        .withStartAt(('name ->> "Alpha") :: ('age ->> Some(1)) :: HNil)
        .withEndAt(('name ->> "Zeta") :: ('age ->> Some(25)) :: HNil)
        .where({ pb =>
          import pb._
          ('name =:= "Nugget") &&
            ('age isNull) &&
            ('kids contains "Iz")
        })

      nameQuery.build shouldBe Query[QueryTest](
        Some(
          Filter(
            CompositeFilter(
              StructuredQuery.CompositeFilter(
                AND,
                List(
                  Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("name")), EQUAL, Some(Value(StringValue("Nugget")))))),
                  Filter(
                    UnaryFilter(
                      StructuredQuery
                        .UnaryFilter(StructuredQuery.UnaryFilter.Operator.IS_NULL, StructuredQuery.UnaryFilter.OperandType.Field(FieldReference("age")))
                    )
                  ),
                  Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("kids")), ARRAY_CONTAINS, Some(Value(StringValue("Iz"))))))
                )
              )
            )
          )
        ),
        Seq(FieldOrder("name", false), FieldOrder("age", false)),
        Seq(Value(StringValue("Alpha")), Value(IntegerValue(1))),
        Seq(Value(StringValue("Zeta")), Value(IntegerValue(25))),
        None,
        None
      )
    }
  }
  "PredicateBuilder constraints" should {
    "Not compile invalid property names" when {
      "using conditional operators" in {
        assertCompiles(
          """
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('name =:= "Nugget")
        }
      """)

        assertDoesNotCompile(
          """
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

        assertCompiles(
          """
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('name =:= "Nugget")
        }
      """)

        assertDoesNotCompile(
          """
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
