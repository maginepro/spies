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

package spies.circe

import munit.ScalaCheckSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop
import spies.Codec

final class CirceSpec extends ScalaCheckSuite {
  test("decode.error") {
    assert(Codec[Value].decode(Codec[String].encode("{}")).isLeft)
  }

  test("roundtrip") {
    Prop.forAll((value: Value) => assertEquals(Codec[Value].decode(Codec[Value].encode(value)), Right(value)))
  }

  case class Value(value: Int)

  object Value {
    implicit val valueCirceCodec: io.circe.Codec[Value] =
      io.circe.Codec.forProduct1("value")(apply)(_.value)

    val valueGen: Gen[Value] =
      arbitrary[Int].map(apply)

    implicit val valueArbitrary: Arbitrary[Value] =
      Arbitrary(valueGen)
  }
}
