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
import scala.math.pow

/**
  * Retry policy for check-and-set (CAS) failures.
  */
trait CasRetryPolicy[F[_]] {

  /**
    * Returns the delay to wait before the next retry.
    *
    * The number of failed retries is `attempts - 1`.
    *
    * If `None` is returned, retries should cease.
    *
    * @param attempts number of failed attempts, starting at 1
    */
  def delay(attempts: Int): F[Option[FiniteDuration]]
}

object CasRetryPolicy {

  /**
    * Returns a retry policy that always returns the specified duration.
    */
  def always[F[_]: Applicative](duration: Option[FiniteDuration]): CasRetryPolicy[F] =
    lift(_ => duration.pure)

  /**
    * Returns the default retry policy, which uses
    * a [[CasRetryPolicy.exponentialBackoff]] with
    * a max delay of 250 millis and max 10 retries.
    */
  def default[F[_]: Applicative: Random]: CasRetryPolicy[F] =
    exponentialBackoff(
      baseDelay = 8.millis,
      maxDelay = 250.millis,
      maxRetries = 10
    )

  /**
    * Returns a retry policy using jittered exponential
    * backoff with the specified base and maximum delay
    * and maximum number of retries.
    *
    * @param baseDelay the exponentially increasing time
    * @param maxDelay the maximum time between retries
    * @param maxRetries the maximum number of retries
    */
  def exponentialBackoff[F[_]: Applicative: Random](
    baseDelay: FiniteDuration,
    maxDelay: Duration,
    maxRetries: Int
  ): CasRetryPolicy[F] =
    lift {
      case attempts if attempts > maxRetries => none.pure
      case attempts =>
        Random[F].nextDouble.map { jitter =>
          val delay = (baseDelay * pow(2, attempts)).min(maxDelay) * jitter
          delay match {
            case finite: FiniteDuration => Some(finite)
            case _ => None
          }
        }
    }

  /**
    * Returns a retry policy that always waits the specified duration between retries.
    */
  def fixed[F[_]: Applicative](duration: FiniteDuration): CasRetryPolicy[F] =
    always(Some(duration))

  /**
    * Returns a retry policy from the specified function.
    */
  def lift[F[_]](f: Int => F[Option[FiniteDuration]]): CasRetryPolicy[F] =
    new CasRetryPolicy[F] {
      override def delay(attempts: Int): F[Option[FiniteDuration]] =
        f(attempts)
    }

  /**
    * Returns a retry policy that does not retry.
    */
  def never[F[_]: Applicative]: CasRetryPolicy[F] =
    always(None)
}
