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

import scala.concurrent.duration.*

/**
  * Configuration for retrying check-and-set (CAS) operations,
  * e.g. as part of [[Memcached#modifyOption]] and the methods
  * built on top of it.
  *
  * Failed CAS attempts are retried using exponential backoff
  * with full jitter, starting at `baseDelay` and capped at
  * `maxDelay`. The overall retry loop is bounded by `timeout`.
  */
final case class CasRetryPolicy(
  baseDelay: FiniteDuration,
  maxDelay: FiniteDuration,
  timeout: FiniteDuration
)

object CasRetryPolicy {
  val default: CasRetryPolicy =
    CasRetryPolicy(
      baseDelay = 8.millis,
      maxDelay = 250.millis,
      timeout = 2.seconds
    )
}
