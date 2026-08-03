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

import cats.Applicative
import cats.effect.std.Random
import cats.syntax.all.*
import scala.concurrent.duration.*
import scala.math.min
import scala.math.pow

/**
  * Retry policy for check-and-set (CAS) failures.
  */
trait CasRetryPolicy[F[_]] {

  /**
    * Returns the [[CasRetry]] retry decision when
    * the specified number of check-and-set (CAS)
    * attempts have failed.
    *
    * @param attempt the number of failed attempts, starting at 1
    */
  def apply(attempt: Int): F[CasRetry]
}

object CasRetryPolicy {

  /**
    * Returns the default retry policy, which uses
    * a [[CasRetryPolicy.exponentialBackoff]] with
    * a max wait of 250 millis and no retry limit.
    */
  def default[F[_]: Applicative: Random]: CasRetryPolicy[F] =
    exponentialBackoff(250.millis, Int.MaxValue)

  /**
    * Returns a retry policy using jittered exponential
    * backoff with the specified maximum wait time and
    * maximum number of retries.
    *
    * @param maxWait the maximum time between retries
    * @param maxRetries the maximum number of retries
    */
  def exponentialBackoff[F[_]: Applicative: Random](
    maxWait: Duration,
    maxRetries: Int
  ): CasRetryPolicy[F] =
    new CasRetryPolicy[F] {
      private val maxWaitMillis: Double =
        maxWait.toMillis.toDouble

      override def apply(attempt: Int): F[CasRetry] =
        if (attempt > maxRetries)
          CasRetry.stop.pure
        else
          Random[F].nextDouble.map { jitter =>
            val millis = (pow(2.0, attempt.toDouble) - 1.0) * 1000.0
            val limitedMillis = min(millis, maxWaitMillis)
            val jitteredMillis = (jitter * limitedMillis).toLong
            val duration = FiniteDuration(jitteredMillis, MILLISECONDS)
            CasRetry.wait(duration)
          }
    }

  /**
    * Returns a retry policy which stops and does not retry.
    */
  def stop[F[_]: Applicative]: CasRetryPolicy[F] =
    new CasRetryPolicy[F] {
      override def apply(attempt: Int): F[CasRetry] =
        CasRetry.stop.pure
    }

  /**
    * Returns a retry policy that always waits
    * the specified duration between retries.
    */
  def wait[F[_]: Applicative](duration: FiniteDuration): CasRetryPolicy[F] =
    new CasRetryPolicy[F] {
      override def apply(attempt: Int): F[CasRetry] =
        CasRetry.wait(duration).pure
    }
}
