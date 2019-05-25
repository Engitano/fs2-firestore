[![CircleCI](https://circleci.com/gh/Engitano/fs2-firestore.svg?style=svg)](https://circleci.com/gh/Engitano/fs2-firestore)

# fs2 FireStore client

##### A GCP Firesotre client based on [fs2](https://fs2.io/guide.html)


Usage:

```sbtshell
resolvers ++= Seq(Resolver.bintrayRepo("engitano", "maven"))
libraryDependencies += "com.engitano" %% "fs2-firestore" % FirestoreVersion
```

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



ToDo:
* Implement StructuredQuery wrapper and runQuery and subscribe to queries
* Tidy up streaming APIS
* Add more tests
* Rationalise packages/files
    
    
