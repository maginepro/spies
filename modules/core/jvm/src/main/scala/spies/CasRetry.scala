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

import scala.concurrent.duration.FiniteDuration

/**
  * The retry decision of a [[CasRetryPolicy]]:
  *
  * - [[CasRetry.stop]] to stop and not keep retrying,
  * - [[CasRetry.wait]] to wait the specified duration.
  */
sealed abstract class CasRetry

object CasRetry {
  private[spies] case object Stop extends CasRetry

  private[spies] final case class Wait(duration: FiniteDuration) extends CasRetry

  /**
    * Retry decision to stop and not keep retrying.
    */
  val stop: CasRetry =
    Stop

  /**
    * Retry decision to keep retrying after
    * waiting the specified duration.
    */
  def wait(duration: FiniteDuration): CasRetry =
    Wait(duration)
}
