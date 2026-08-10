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
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Gen
import org.scalacheck.effect.PropF
import scala.concurrent.duration.*
import scala.math.pow

final class CasRetryPolicySpec extends CatsEffectSuite with ScalaCheckEffectSuite {
  test("default.baseDelay") {
    val gen = Gen.chooseNum(1, 10)
    PropF.forAllNoShrinkF(gen) { attempts =>
      for {
        delay <- CasRetryPolicy.default[IO].delay(attempts)
        baseLimit = 8.millis * pow(2.0, attempts.toDouble)
        _ <- IO(assert(delay.exists(_ <= baseLimit)))
      } yield ()
    }
  }

  test("default.maxDelay") {
    val gen = Gen.chooseNum(1, 10)
    PropF.forAllNoShrinkF(gen) { attempts =>
      for {
        delay <- CasRetryPolicy.default[IO].delay(attempts)
        _ <- IO(assert(delay.exists(_ <= 250.millis)))
      } yield ()
    }
  }

  test("default.maxRetries") {
    val gen = Gen.chooseNum(11, Int.MaxValue)
    PropF.forAllNoShrinkF(gen) { attempts =>
      for {
        delay <- CasRetryPolicy.default[IO].delay(attempts)
        _ <- IO(assertEquals(delay, None))
      } yield ()
    }
  }
}
