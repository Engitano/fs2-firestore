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
      import com.engitano.fs2firestore.implicits._
      val personF     = IO(UUID.randomUUID())
                          .map(i => Person(i, "Nugget", None, Seq()))
      val config      = FirestoreConfig.local(DefaultGcpProject, DefaultPubsubPort)
      val personF     = FirestoreFs2.resource[IO](config).use { client =>
        for {
        testPerson <- personF
          _        <- client.createDocument(testPerson)
        nugget     <- client.getDocument[Person](id.toString)
        } yield nugget
      }

      personF.unsafeRunSync() shouldBe Some(Right(testPerson))
```

### Query API
Firestore FS2 makes heavy use of Shapeless to ensure type safety in query definitions.
For examples see [QuerySpec.scala](./src/test//scala/com/engitano/fs2firestore/QuerySpec.scala)

```scala
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

```

Still very much a WIP. Contributions welcome.
