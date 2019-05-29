package com.engitano.fs2firestore

import cats.syntax.option.none
import com.engitano.fs2firestore.api.Query
import com.engitano.fs2firestore.queries.{CollectionOf, QueryBuilder}
import com.google.`type`.LatLng
import com.google.firestore.v1.StructuredQuery.CompositeFilter.Operator.AND
import com.google.firestore.v1.StructuredQuery.FieldFilter.Operator.{ARRAY_CONTAINS, EQUAL, LESS_THAN_OR_EQUAL}
import com.google.firestore.v1.StructuredQuery.{FieldReference, Filter}
import com.google.firestore.v1.StructuredQuery.Filter.FilterType.{CompositeFilter, FieldFilter, UnaryFilter}
import com.google.firestore.v1.{StructuredQuery, Value}
import com.google.firestore.v1.Value.ValueTypeOneof.{GeoPointValue, IntegerValue, NullValue, StringValue}
import org.scalatest.{Matchers, WordSpec}
import com.engitano.fs2firestore.implicits._

class QuerySpec extends WordSpec with Matchers {

  "QueryBuild" should {
    "build compile a valid query" in {

      import ValueMarshaller._
      import com.engitano.fs2firestore.queries.syntax._

      val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
        import pb._
        ('id =:= "Nugget") &&
        ('name isNull) &&
        ('name isNan) &&
        ('age =:= none[Int]) &&
        ('kids contains "Iz")
      }

      nameQuery.build shouldBe Query[QueryTest](
        Filter(
          CompositeFilter(
            StructuredQuery.CompositeFilter(
              AND,
              List(
                Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("id")), EQUAL, Some(Value(StringValue("Nugget")))))),
                Filter(UnaryFilter(StructuredQuery.UnaryFilter(StructuredQuery.UnaryFilter.Operator.IS_NULL, StructuredQuery.UnaryFilter.OperandType.Field(FieldReference("name"))))),
                Filter(UnaryFilter(StructuredQuery.UnaryFilter(StructuredQuery.UnaryFilter.Operator.IS_NAN, StructuredQuery.UnaryFilter.OperandType.Field(FieldReference("name"))))),
                Filter(
                  FieldFilter(
                    StructuredQuery.FieldFilter(Some(FieldReference("age")), EQUAL, Some(Value(NullValue(com.google.protobuf.struct.NullValue.NULL_VALUE))))
                  )
                ),
                Filter(FieldFilter(StructuredQuery.FieldFilter(Some(FieldReference("kids")), ARRAY_CONTAINS, Some(Value(StringValue("Iz"))))))
              )
            )
          )
        )
      )
    }
    "Not compile invalud queries" in {

      assertCompiles("""
        import ValueMarshaller._
        import com.engitano.fs2firestore.queries.syntax._
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('name =:= "Nugget")
        }
      """)

      assertDoesNotCompile("""
        import ValueMarshaller._
        import com.engitano.fs2firestore.queries.syntax._
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('names =:= "Nugget")
        }
      """)

      assertCompiles("""
        import ValueMarshaller._
        import com.engitano.fs2firestore.queries.syntax._
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('age isNull)
        }
      """)

      assertDoesNotCompile("""
        import ValueMarshaller._
        import com.engitano.fs2firestore.queries.syntax._
        val nameQuery = QueryBuilder.from(CollectionFor[QueryTest]).where { pb =>
          import pb._
          ('ages isNull)
        }
      """)

    }
  }
}
