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

package spies.internal

import cats.effect.IO
import java.time.Instant
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Gen
import org.scalacheck.effect.PropF.*
import scala.concurrent.duration.*
import spies.MemcachedError
import spies.internal.expiry.*

final class ExpirySpec extends CatsEffectSuite with ScalaCheckEffectSuite {
  def assertFail(duration: Duration): IO[Unit] =
    expiryTime[IO](duration)
      .intercept[MemcachedError]
      .as(true)
      .assert

  def assertSuccess(duration: Duration): IO[Unit] =
    expiryTime[IO](duration)
      .as(true)
      .assert

  def assertSuccess(duration: Duration, expected: Int): IO[Unit] =
    expiryTime[IO](duration)
      .map(_.toInt)
      .assertEquals(expected)

  test("Duration.Inf") {
    assertSuccess(Duration.Inf, 0)
  }

  test("Duration.MinusInf") {
    assertFail(Duration.MinusInf)
  }

  test("Duration.Undefined") {
    assertFail(Duration.Undefined)
  }

  test("Duration < 0") {
    val gen = Gen.chooseNum(Long.MinValue + 1, -1L).map(Duration.fromNanos)
    forAllNoShrinkF(gen)(assertFail)
  }

  test("Duration.Zero") {
    assertSuccess(Duration.Zero, 0)
  }

  test("0 < Duration < 1 Second") {
    val gen = Gen.chooseNum(1L, (1.second - 1.nano).toNanos).map(Duration.fromNanos)
    forAllNoShrinkF(gen)(assertFail)
  }

  test("1 Second <= Duration <= 30 Days") {
    val gen =
      Gen
        .chooseNum(
          1.second.toNanos,
          (30.days + 1.second - 1.nano).toNanos
        )
        .map(Duration.fromNanos)

    forAllNoShrinkF(gen)(duration => assertSuccess(duration, duration.toSeconds.toInt))
  }

  test("30 Days < Duration <= Int.MaxValue - Epoch") {
    val epoch =
      Instant.now().toEpochMilli.millis

    val margin =
      10.seconds

    val gen =
      Gen
        .chooseNum(
          (30.days + 1.second).toNanos,
          (Int.MaxValue.seconds - epoch - margin).toNanos
        )
        .map(Duration.fromNanos)

    forAllNoShrinkF(gen)(assertSuccess)
  }

  test("Duration > Int.MaxValue - Epoch") {
    val epoch =
      Instant.now().toEpochMilli.millis

    val margin =
      10.seconds

    val gen: Gen[Duration] =
      Gen
        .chooseNum(
          (Int.MaxValue.seconds - epoch + margin).toNanos,
          Long.MaxValue
        )
        .map(Duration.fromNanos)

    forAllNoShrinkF(gen)(assertFail)
  }
}
