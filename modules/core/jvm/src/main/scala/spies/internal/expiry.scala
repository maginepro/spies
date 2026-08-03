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

import cats.MonadThrow
import cats.effect.kernel.Temporal
import cats.syntax.all.*
import java.time.Instant
import scala.concurrent.duration.*
import spies.MemcachedError

private[spies] object expiry {
  def expiryTime[F[_]](
    duration: Duration
  )(
    implicit F: Temporal[F]
  ): F[Int] =
    F.realTimeInstant.flatMap(expiryTime(duration, _))

  /**
    * Returns an effect with an expiration value accepted by Memcached.
    *
    * If the duration could not be converted to a valid expiry time, a
    * [[MemcachedError]] will be raised.
    *
    * Memcached uses second precision and duration values will be
    * rounded down to the nearest second.
    *
    * - `Duration.Inf` and `Duration.Zero` are both treated as no expiry,
    *   since Memcached uses `0` to denote no expiration.
    * - `Duration.Undefined`, `Duration.MinusInf` and any other duration
    *   less than 1 second results in a [[MemcachedError]].
    * - Any duration which results in an epoch time which would exceed
    *  `Int.MaxValue` also results in a [[MemcachedError]].
    */
  def expiryTime[F[_]](
    duration: Duration,
    now: Instant
  )(
    implicit F: MonadThrow[F]
  ): F[Int] =
    if (duration == Duration.Inf || duration == Duration.Zero)
      F.pure(0)
    else if (duration < oneSecond || duration.eq(Duration.Undefined))
      F.raiseError(MemcachedError(s"invalid memcached expiry time: $duration"))
    else if (duration < thirtyDaysOneSecond)
      F.pure(duration.toSeconds.toInt)
    else
      F.catchNonFatal(now.plusSeconds(duration.toSeconds).toEpochMilli / 1000L)
        .adaptErr { case error =>
          MemcachedError(
            s"error while generating epoch for memcached expiry time $duration: ${error.getMessage}"
          )
        }
        .flatMap {
          case epoch if epochMinValue <= epoch && epoch <= epochMaxValue =>
            F.pure(epoch.toInt)
          case epoch =>
            F.raiseError(
              MemcachedError(s"invalid epoch $epoch for memcached expiry time: $duration")
            )
        }

  private[expiry] val oneSecond: FiniteDuration =
    1.second

  private[expiry] val thirtyDaysOneSecond: FiniteDuration =
    30.days + oneSecond

  private[expiry] val epochMinValue: Long =
    thirtyDaysOneSecond.toSeconds

  private[expiry] val epochMaxValue: Long =
    Int.MaxValue.toLong
}
