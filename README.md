# fs2 PubSub

##### A GCP PubSub client based on [fs2](https://fs2.io/guide.html)


Basic Usage:

```scala
      val config = GrpcPubsubConfig.local(DefaultGcpProject, DefaultPubsubPort)
      val publishStream = Stream.emits[IO, Int](1 to 1000).toPubSub(topicName, config))
      val subscribeStream = Subscriber.stream[IO, Int](testSubscription, config) { s =>
        s.map(_.peek(businessLogic))
      }

      def businessLogic(s: Int) = println(s)

      val result = (publishStream >> subscribeStream).compile.drain.attempt.unsafeRunSync()
```