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

import cats.effect.kernel.Ref
import cats.effect.kernel.Temporal
import cats.kernel.Order
import cats.syntax.all.*
import java.time.Instant
import scala.concurrent.duration.*
import spies.TestMemcached.Entry
import spies.internal.expiry.*

trait TestMemcached[F[_]] extends Memcached[F] {

  /**
    * Returns the non-expired entries in the cache.
    */
  def cached: F[Map[String, Entry]]
}

object TestMemcached {

  /**
    * The bytes and expiry time for some key in the cache.
    *
    * An `expiresAt` of `None` is used to denote no expiry time.
    */
  final case class Entry(bytes: Array[Byte], expiresAt: Option[Instant])

  def empty[F[_]](
    implicit F: Temporal[F]
  ): F[TestMemcached[F]] =
    Ref.of(Map.empty[String, Entry]).map(fromRef[F])

  /**
    * Returns an in-memory cache based on the specified `Ref`.
    *
    * Cleanup of expired entries is only done whenever updates
    * occur. Specifically, there is no automatic or otherwise
    * scheduled cleanup.
    */
  def fromRef[F[_]](
    ref: Ref[F, Map[String, Entry]]
  )(
    implicit F: Temporal[F]
  ): TestMemcached[F] =
    new TestMemcached[F] {
      private implicit val instantOrder: Order[Instant] =
        Order.fromLessThan((x, y) => x.isBefore(y))

      private def cleanup(entries: Map[String, Entry], now: Instant): Map[String, Entry] =
        entries.collect {
          case (key, entry) if entry.expiresAt.fold(true)(_ >= now) =>
            (key, entry)
        }

      private def decode[A](
        entries: Map[String, Entry],
        key: String,
        now: Instant
      )(
        implicit codec: Codec[A]
      ): Either[DecodeError, Option[A]] =
        entries
          .get(key)
          .filter(_.expiresAt.fold(true)(_ >= now))
          .traverse(entry => codec.decode(entry.bytes))

      private def expiresAt(expiry: Duration, now: Instant): F[Option[Instant]] =
        expiryTime(expiry, now).map { expiryTime =>
          if (expiryTime != 0)
            Some {
              if (expiryTime <= 60 * 60 * 24 * 30)
                now.plusSeconds(expiryTime.toLong)
              else Instant.ofEpochSecond(expiryTime.toLong)
            }
          else None
        }

      private def unsafeExpiresAt(expiry: Duration, now: Instant): Option[Instant] = {
        val expiryTime = unsafeExpiryTime(expiry, now)
        if (expiryTime != 0)
          Some {
            if (expiryTime <= 60 * 60 * 24 * 30)
              now.plusSeconds(expiryTime.toLong)
            else Instant.ofEpochSecond(expiryTime.toLong)
          }
        else None
      }

      /**
        * Unsafe version of [[spies.internal.expiryTime]].
        */
      private def unsafeExpiryTime(duration: Duration, now: Instant): Int =
        if (duration == Duration.Inf || duration == Duration.Zero)
          0
        else if (duration < 1.second || duration.eq(Duration.Undefined))
          throw MemcachedError(s"invalid memcached expiry time: $duration")
        else if (duration < 30.days + 1.second)
          duration.toSeconds.toInt
        else
          Either
            .catchNonFatal(now.plusSeconds(duration.toSeconds).toEpochMilli / 1000L)
            .leftMap(error =>
              MemcachedError(
                s"error while generating epoch for memcached expiry time $duration: ${error.getMessage}"
              )
            )
            .flatMap {
              case epoch if (30.days + 1.second).toSeconds <= epoch && epoch <= Int.MaxValue.toLong =>
                Right(epoch.toInt)
              case epoch =>
                Left(MemcachedError(s"invalid epoch $epoch for memcached expiry time: $duration"))
            }
            .fold(throw _, identity)

      override def access[A](
        key: String
      )(
        implicit codec: Codec[A]
      ): F[(Option[A], (A, Duration) => F[Boolean])] =
        F.realTimeInstant.flatMap { now =>
          ref.access.map { case (entries, update) =>
            decode(entries, key, now).map { oa =>
              val updateEntry = (a: A, expiry: Duration) =>
                expiresAt(expiry, now).flatMap { expiresAt =>
                  val entry = Entry(codec.encode(a), expiresAt)
                  update(cleanup(entries.updated(key, entry), now))
                }

              (oa, updateEntry)
            }
          }.rethrow
        }

      override def add[A](
        key: String,
        value: A,
        expiry: Duration
      )(
        implicit codec: Codec[A]
      ): F[Boolean] =
        F.realTimeInstant.flatMap { now =>
          expiresAt(expiry, now).flatMap { expiresAt =>
            ref.modify { entries =>
              val entry =
                entries
                  .get(key)
                  .filter(_.expiresAt.fold(true)(_ >= now))

              entry match {
                case Some(_) =>
                  (cleanup(entries, now), false)
                case None =>
                  val entry = Entry(codec.encode(value), expiresAt)
                  (cleanup(entries.updated(key, entry), now), true)
              }
            }
          }
        }

      override def cached: F[Map[String, Entry]] =
        F.realTimeInstant.flatMap { now =>
          ref.get.map { entries =>
            entries.collect {
              case (key, entry) if entry.expiresAt.fold(true)(_ >= now) =>
                (key, entry)
            }
          }
        }

      override def delete(key: String): F[Boolean] =
        F.realTimeInstant.flatMap { now =>
          ref.modify { entries =>
            val exists = entries.get(key).exists(_.expiresAt.fold(true)(_ >= now))
            (cleanup(entries - key, now), exists)
          }
        }

      override def get[A](
        key: String
      )(
        implicit codec: Codec[A]
      ): F[Option[A]] =
        F.realTimeInstant.flatMap(now => ref.get.map(decode(_, key, now)).rethrow)

      override def getAndSet[A](
        key: String,
        value: A,
        expiry: Duration
      )(
        implicit codec: Codec[A]
      ): F[Option[A]] =
        getAndUpdate(key, expiry)(_ => value)

      override def getAndUpdate[A](
        key: String,
        expiry: Duration
      )(
        f: Option[A] => A
      )(
        implicit codec: Codec[A]
      ): F[Option[A]] =
        modify(key, expiry)((oa: Option[A]) => (f(oa), oa))

      override def getOrElse[A](
        key: String,
        default: => A
      )(
        implicit codec: Codec[A]
      ): F[A] =
        get(key).map(_.getOrElse(default))

      override def modify[A, B](
        key: String,
        expiry: Duration
      )(
        f: Option[A] => (A, B)
      )(
        implicit codec: Codec[A]
      ): F[B] =
        modifyOption(key, expiry) { (oa: Option[A]) =>
          val (fa, fb) = f(oa)
          (Some(fa), fb)
        }

      override def modifyOption[A, B](
        key: String,
        expiry: Duration
      )(
        f: Option[A] => (Option[A], B)
      )(
        implicit codec: Codec[A]
      ): F[B] =
        modifyOption(key) { (oa: Option[A]) =>
          val (fa, fb) = f(oa)
          (fa.tupleRight(expiry), fb)
        }

      override def modifyOption[A, B](
        key: String
      )(
        f: Option[A] => (Option[(A, Duration)], B)
      )(
        implicit codec: Codec[A]
      ): F[B] =
        F.realTimeInstant.flatMap { now =>
          ref.modify { entries =>
            decode(entries, key, now) match {
              case Right(oa) =>
                f(oa) match {
                  case (Some((fa, expiry)), fb) =>
                    val expiresAt = unsafeExpiresAt(expiry, now)
                    val entry = Entry(codec.encode(fa), expiresAt)
                    (cleanup(entries.updated(key, entry), now), fb)
                  case (None, fb) =>
                    (cleanup(entries, now), fb)
                }

              case Left(e) =>
                throw e
            }
          }
        }

      override def set[A](
        key: String,
        value: A,
        expiry: Duration
      )(
        implicit codec: Codec[A]
      ): F[Unit] =
        F.realTimeInstant.flatMap { now =>
          expiresAt(expiry, now).flatMap { expiresAt =>
            val entry = Entry(codec.encode(value), expiresAt)
            ref.update(entries => cleanup(entries.updated(key, entry), now))
          }
        }

      override def touch(key: String, expiry: Duration): F[Boolean] =
        F.realTimeInstant.flatMap { now =>
          expiresAt(expiry, now).flatMap { expiresAt =>
            ref.modify { entries =>
              val updatedEntry =
                entries
                  .get(key)
                  .filter(_.expiresAt.fold(true)(_ >= now))
                  .map(_.copy(expiresAt = expiresAt))

              val updatedEntries =
                updatedEntry.fold(entries)(entries.updated(key, _))

              val entryUpdated =
                updatedEntry.isDefined

              (cleanup(updatedEntries, now), entryUpdated)
            }
          }
        }

      override def tryModify[A, B](
        key: String,
        expiry: Duration
      )(
        f: Option[A] => (A, B)
      )(
        implicit codec: Codec[A]
      ): F[Option[B]] =
        F.realTimeInstant.flatMap { now =>
          expiresAt(expiry, now).flatMap { expiresAt =>
            ref.tryModify { entries =>
              decode(entries, key, now) match {
                case Right(oa) =>
                  val (fa, fb) = f(oa)
                  val entry = Entry(codec.encode(fa), expiresAt)
                  (cleanup(entries.updated(key, entry), now), fb)
                case Left(e) =>
                  throw e
              }
            }
          }
        }

      override def tryUpdate[A](
        key: String,
        expiry: Duration
      )(
        f: Option[A] => A
      )(
        implicit codec: Codec[A]
      ): F[Boolean] =
        tryModify(key, expiry)((oa: Option[A]) => (f(oa), true))
          .map(_.getOrElse(false))

      override def update[A](
        key: String,
        expiry: Duration
      )(
        f: Option[A] => A
      )(
        implicit codec: Codec[A]
      ): F[Unit] =
        modify(key, expiry)((oa: Option[A]) => (f(oa), ()))

      override def updateOption[A](
        key: String,
        expiry: Duration
      )(
        f: Option[A] => Option[A]
      )(
        implicit codec: Codec[A]
      ): F[Unit] =
        modifyOption(key, expiry)((oa: Option[A]) => (f(oa), ()))

      override def updateOption[A](
        key: String
      )(
        f: Option[A] => Option[(A, Duration)]
      )(
        implicit codec: Codec[A]
      ): F[Unit] =
        modifyOption(key)((oa: Option[A]) => (f(oa), ()))

      override def updateAndGet[A](
        key: String,
        expiry: Duration
      )(
        f: Option[A] => A
      )(
        implicit codec: Codec[A]
      ): F[A] =
        modify(key, expiry) { (oa: Option[A]) =>
          val foa = f(oa)
          (foa, foa)
        }
    }
}
