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

import munit.ScalaCheckSuite
import org.scalacheck.Prop.*

final class CodecSpec extends ScalaCheckSuite {
  def assertRoundtrip[A](
    a: A
  )(
    implicit codec: Codec[A]
  ) =
    assertEquals(codec.decode(codec.encode(a)), Right(a))

  test("Array[Byte]") {
    forAll((bytes: Array[Byte]) => assertRoundtrip(bytes))
  }

  test("Boolean") {
    forAll((boolean: Boolean) => assertRoundtrip(boolean))
  }

  test("Char") {
    forAll((char: Char) => assertRoundtrip(char))
  }

  test("Double") {
    forAll((double: Double) => assertRoundtrip(double))
  }

  test("Float") {
    forAll((float: Float) => assertRoundtrip(float))
  }

  test("Int") {
    forAll((int: Int) => assertRoundtrip(int))
  }

  test("Long") {
    forAll((long: Long) => assertRoundtrip(long))
  }

  test("Short") {
    forAll((long: Long) => assertRoundtrip(long))
  }

  test("String") {
    forAll((string: String) => assertRoundtrip(string))
  }
}
