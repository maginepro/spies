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

import cats.Invariant
import java.nio.charset.StandardCharsets

sealed abstract class Codec[A] {
  def encode(value: A): Array[Byte]

  def decode(bytes: Array[Byte]): Either[DecodeError, A]

  final def imap[B](f: A => B)(g: B => A): Codec[B] =
    Codec.instance(
      b => encode(g(b)),
      bytes => decode(bytes).map(f)
    )

  final def imapErr[B](f: A => Either[DecodeError, B])(g: B => A): Codec[B] =
    Codec.instance(
      b => encode(g(b)),
      bytes => decode(bytes).flatMap(f)
    )
}

object Codec {
  def apply[A](
    implicit codec: Codec[A]
  ): Codec[A] =
    codec

  def instance[A](
    encode: A => Array[Byte],
    decode: Array[Byte] => Either[DecodeError, A]
  ): Codec[A] = {
    val _encode = encode
    val _decode = decode
    new Codec[A] {
      override def encode(value: A): Array[Byte] =
        _encode(value)

      override def decode(bytes: Array[Byte]): Either[DecodeError, A] =
        _decode(bytes)

      override def toString: String =
        "Codec$" + System.identityHashCode(this)
    }
  }

  implicit val codecArrayByte: Codec[Array[Byte]] =
    Codec.instance(bytes => bytes, bytes => Right(bytes))

  implicit val codecBoolean: Codec[Boolean] =
    Codec.instance(
      value => Array(if (value) 1.asInstanceOf[Byte] else 0.asInstanceOf[Byte]),
      bytes =>
        if (bytes.length == 1)
          Right(bytes(0) == 1.asInstanceOf[Byte])
        else Left(DecodeError(s"unexpected length ${bytes.length}, expected 1"))
    )

  implicit val codecChar: Codec[Char] =
    Codec.instance(
      value =>
        Array(
          (value >>> 8).asInstanceOf[Byte],
          value.asInstanceOf[Byte]
        ),
      bytes =>
        if (bytes.length == 2)
          Right {
            ((bytes(0).asInstanceOf[Int] & 255) << 8 |
              bytes(1).asInstanceOf[Int] & 255)
              .asInstanceOf[Char]
          }
        else Left(DecodeError(s"unexpected length ${bytes.length}, expected 2"))
    )

  implicit val codecInt: Codec[Int] =
    Codec.instance(
      value =>
        Array(
          (value >>> 24).asInstanceOf[Byte],
          (value >>> 16).asInstanceOf[Byte],
          (value >>> 8).asInstanceOf[Byte],
          value.asInstanceOf[Byte]
        ),
      bytes =>
        if (bytes.length == 4)
          Right {
            (bytes(0).asInstanceOf[Int] & 255) << 24 |
              (bytes(1).asInstanceOf[Int] & 255) << 16 |
              (bytes(2).asInstanceOf[Int] & 255) << 8 |
              bytes(3).asInstanceOf[Int] & 255
          }
        else Left(DecodeError(s"unexpected length ${bytes.length}, expected 4"))
    )

  implicit val codecLong: Codec[Long] =
    Codec.instance(
      value =>
        Array(
          (value >>> 56).asInstanceOf[Byte],
          (value >>> 48).asInstanceOf[Byte],
          (value >>> 40).asInstanceOf[Byte],
          (value >>> 32).asInstanceOf[Byte],
          (value >>> 24).asInstanceOf[Byte],
          (value >>> 16).asInstanceOf[Byte],
          (value >>> 8).asInstanceOf[Byte],
          value.asInstanceOf[Byte]
        ),
      bytes =>
        if (bytes.length == 8)
          Right {
            (bytes(0).asInstanceOf[Long] & 255) << 56 |
              (bytes(1).asInstanceOf[Long] & 255) << 48 |
              (bytes(2).asInstanceOf[Long] & 255) << 40 |
              (bytes(3).asInstanceOf[Long] & 255) << 32 |
              (bytes(4).asInstanceOf[Long] & 255) << 24 |
              (bytes(5).asInstanceOf[Long] & 255) << 16 |
              (bytes(6).asInstanceOf[Long] & 255) << 8 |
              bytes(7).asInstanceOf[Long] & 255
          }
        else Left(DecodeError(s"unexpected length ${bytes.length}, expected 8"))
    )

  implicit val codecDouble: Codec[Double] =
    Codec.instance(
      value => codecLong.encode(java.lang.Double.doubleToLongBits(value)),
      bytes => codecLong.decode(bytes).map(java.lang.Double.longBitsToDouble)
    )

  implicit val codecFloat: Codec[Float] =
    Codec.instance(
      value => codecInt.encode(java.lang.Float.floatToIntBits(value)),
      bytes => codecInt.decode(bytes).map(java.lang.Float.intBitsToFloat)
    )

  implicit val codecInvariant: Invariant[Codec] =
    new Invariant[Codec] {
      override def imap[A, B](codec: Codec[A])(f: A => B)(g: B => A): Codec[B] =
        codec.imap(f)(g)
    }

  implicit val codecShort: Codec[Short] =
    Codec.instance(
      value =>
        Array(
          (value >>> 8).asInstanceOf[Byte],
          value.asInstanceOf[Byte]
        ),
      bytes =>
        if (bytes.length == 2)
          Right {
            ((bytes(0).asInstanceOf[Short] & 255) << 8 |
              bytes(1).asInstanceOf[Short] & 255)
              .asInstanceOf[Short]
          }
        else Left(DecodeError(s"unexpected length ${bytes.length}, expected 2"))
    )

  implicit val codecString: Codec[String] =
    Codec.instance(
      _.getBytes(StandardCharsets.UTF_8),
      bytes => Right(new String(bytes, StandardCharsets.UTF_8))
    )
}
