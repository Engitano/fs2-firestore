[![CircleCI](https://circleci.com/gh/Engitano/fs2-firestore.svg?style=svg)](https://circleci.com/gh/Engitano/fs2-firestore)

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
For examples see [./src/test/QuerySpec.scala](./src/test/QuerySpec.scala)

```scala
val query = QueryBuilder
              .from(CollectionFor[Person])
              .where { pb =>
                import pb._
                ('name =:= "Nugget") &&
                  ('age :> 29) &&
                  ('age :< 31)
              }
              .build
val nuggetStream = client.runQuery(query)
nuggetStream.compile.toList.unsafeRunSync().head should matchPattern {
  case Some(Right(Person(_, "Nugget", _, _))) =>
}

```

Still very much a WIP. Contributions welcome.

ToDo:
* Tidy up streaming APIS
* Add more tests
* Add more Admin functionality
