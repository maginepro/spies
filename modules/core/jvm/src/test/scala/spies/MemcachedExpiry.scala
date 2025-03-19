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

import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import scala.concurrent.duration.*

final case class MemcachedExpiry(value: Duration)

object MemcachedExpiry {
  val memcachedExpiryGen: Gen[MemcachedExpiry] =
    Gen
      .frequency(
        1 -> Gen.const(Duration.Inf),
        1 -> Gen.const(Duration.Zero),
        5 -> Gen.chooseNum(10L, 30.days.toSeconds).map(_.seconds),
        3 -> Gen.chooseNum(30.days.toSeconds + 1L, 365.days.toSeconds).map(_.seconds)
      )
      .map(apply)

  implicit val memcachedExpiryArbitrary: Arbitrary[MemcachedExpiry] =
    Arbitrary(memcachedExpiryGen)
}
