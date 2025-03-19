# 🕵️ Spies

Spies is a functional [Memcached](https://www.memcached.org) library based on [SpyMemcached](https://github.com/amazonwebservices/aws-elasticache-cluster-client-memcached-for-java) and [Cats Effect](https://github.com/typelevel/cats-effect).<br>
The library is inspired by [Shade](https://github.com/monix/shade) and [ScalaCache](https://github.com/cb372/scalacache) but with a pure functional interface.

## Usage

You can add the following lines to `build.sbt` to use the library.

```scala
libraryDependencies ++= Seq(
  "com.magine" %% "spies" % spiesVersion,
  "com.magine" %% "spies-circe" % spiesVersion
)
```

Make sure to replace `spiesVersion` with a [release version](https://github.com/maginepro/spies/releases).

## Quick Example

Following is an example of how to count to 100 using 100 parallel increment-by-one updates.

```scala
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all._
import scala.concurrent.duration._

object Main extends IOApp.Simple {
  override def run: IO[Unit] =
    Memcached.localhost[IO].use { memcached =>
      val increment = memcached.update[Int]("key", 10.seconds)(_.fold(1)(_ + 1))
      val updates = List.fill(100)(increment).parSequence_
      val get = memcached.get[Int]("key")
      updates >> get.flatMap(IO.println)
    }
}
```

The example demonstrates support for check-and-set (CAS) operations. Some updates will fail on the first attempt (since a different update was performed in-between the get and the update), but these updates will automatically be retried.

Note the example communicates with a local Memcached instance, using the default protocol. When dealing with Memcached clusters, you can use `Memcached.ketama` to use the [Ketama](https://www.last.fm/user/RJ/journal/2007/04/10/rz_libketama_-_a_consistent_hashing_algo_for_memcache_clients) consistent hashing algorithm to consistently map keys to servers, even when Memcached servers are added or removed from the cluster (only a small proportion of keys end up mapping to different servers).

Spies is based on the [Amazon ElastiCache Cluster Client](https://github.com/awslabs/aws-elasticache-cluster-client-memcached-for-java) which supports node [auto discovery](https://docs.aws.amazon.com/AmazonElastiCache/latest/mem-ug/AutoDiscovery.html) by default.
