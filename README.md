# fs2 PubSub

##### A GCP Firesotre client based on [fs2](https://fs2.io/guide.html)


Usage:
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
    
    
