[![CircleCI](https://circleci.com/gh/Engitano/fs2-firestore.svg?style=svg)](https://circleci.com/gh/Engitano/fs2-firestore)
[![Coverage Status](https://coveralls.io/repos/github/Engitano/fs2-firestore/badge.svg)](https://coveralls.io/github/Engitano/fs2-firestore)
# fs2 FireStore client

##### A GCP Firestore client based on [fs2](https://fs2.io/guide.html)


Usage:

```sbtshell
resolvers ++= Seq(Resolver.bintrayRepo("engitano", "maven"))
libraryDependencies += "com.engitano" %% "fs2-firestore" % FirestoreVersion
```
See tags for latest version


### Basic Usage
```scala
      import DocumentMarshaller._
      val id = UUID.randomUUID()
      val testPerson = Person(id, "Nugget", None, Seq())
      val personF = FirestoreFs2.resource[IO](FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)).use { client =>
        for {
          _ <- client.createDocument(testPerson)
        nugget <- client.getDocument[Person](id.toString)
        } yield nugget
      }

      personF.unsafeRunSync() shouldBe Some(Right(testPerson))
```

### Query API
Firestore FS2 makes heavy use of Shapeless to ensure type safety in query definitions.
For examples see [QuerySpec.scala](./src/test//scala/com/engitano/fs2firestore/QuerySpec.scala)

```scala
"QueryBuilder" should {
    "build a valid query" in {

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

```

Still very much a WIP. Contributions welcome.

ToDo:
* Tidy up streaming APIS
* Add more tests
* Add more Admin functionality
