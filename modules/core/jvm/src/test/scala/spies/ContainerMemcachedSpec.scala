/*
 * Copyright 2025 Magine Pro
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spies

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import munit.AnyFixture
import net.spy.memcached.ClientMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

final class ContainerMemcachedSpec extends MemcachedSuite {
  val memcachedFixture: AnyFixture[Memcached[IO]] = {
    val disableLogging: Resource[IO, Unit] =
      Resource.eval {
        IO(System.getProperty("net.spy.log.LoggerImpl") == null).ifM(
          IO(System.setProperty("net.spy.log.LoggerImpl", "spies.NopLogger")).void,
          IO.unit
        )
      }

    val memcachedContainer: Resource[IO, GenericContainer[?]] =
      Resource.make {
        IO.blocking {
          val container = new GenericContainer(DockerImageName.parse("memcached:1.6.15-alpine"))
          container.addExposedPorts(11211)
          container.start()
          container
        }
      }(container => IO.blocking(container.stop()))

    val memcachedClient: GenericContainer[?] => Resource[IO, Memcached[IO]] =
      container => {
        val address = s"${container.getHost()}:${container.getFirstMappedPort()}"
        Memcached.builder[IO](address)(_.setClientMode(ClientMode.Static))
      }

    ResourceSuiteLocalFixture(
      "memcached",
      disableLogging >> memcachedContainer.flatMap(memcachedClient)
    )
  }

  override def munitFixtures: Seq[AnyFixture[?]] =
    memcachedFixture +: super.munitFixtures

  override def memcached: IO[Memcached[IO]] =
    IO(memcachedFixture())
}
