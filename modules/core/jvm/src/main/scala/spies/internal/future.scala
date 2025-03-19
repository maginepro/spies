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

import cats.effect.kernel.Async
import cats.syntax.all.*
import net.spy.memcached.CASResponse
import net.spy.memcached.internal.GetCompletionListener
import net.spy.memcached.internal.GetFuture
import net.spy.memcached.internal.OperationCompletionListener
import net.spy.memcached.internal.OperationFuture
import net.spy.memcached.ops.StatusCode
import scala.util.control.NonFatal

private[spies] object future {
  def asyncGet[F[_], A](
    f: (Either[Throwable, A] => Unit) => GetFuture[?]
  )(
    implicit F: Async[F]
  ): F[A] =
    F.async { cb =>
      F.delay(f(cb)).map { future =>
        Some(
          F.delay(future.cancel(false))
            .ifM(
              F.unit,
              F.async[Unit] { cb =>
                F.delay {
                  val listener: GetCompletionListener = _ => cb(Right(()))
                  future.addListener(listener)
                  Some(F.delay(future.removeListener(listener)).void)
                }
              }
            )
        )
      }
    }

  def asyncOperation[F[_], A](
    f: (Either[Throwable, A] => Unit) => OperationFuture[?]
  )(
    implicit F: Async[F]
  ): F[A] =
    F.async { cb =>
      F.delay(f(cb)).map { future =>
        Some(
          F.delay(future.cancel())
            .ifM(
              F.unit,
              F.async[Unit] { cb =>
                F.delay {
                  val listener: OperationCompletionListener = _ => cb(Right(()))
                  future.addListener(listener)
                  Some(F.delay(future.removeListener(listener)).void)
                }
              }
            )
        )
      }
    }

  implicit final class GetFutureSyntax(val future: GetFuture[?]) extends AnyVal {
    def hasStatus(statusCode: StatusCode): Boolean =
      Option(future.getStatus).exists(_.getStatusCode == statusCode)

    def isSuccess: Boolean =
      Option(future.getStatus).exists(_.isSuccess)

    def onComplete[F[_], A](
      cb: Either[Throwable, A] => Unit
    )(
      f: GetFuture[?] => Unit
    ): GetFuture[?] =
      future.addListener { completedFuture =>
        try
          f(completedFuture)
        catch {
          case e if NonFatal(e) =>
            cb(Left(e))
        }
      }
  }

  implicit final class OperationFutureSyntax(val future: OperationFuture[?]) extends AnyVal {
    def hasCasResponse(casResponse: CASResponse): Boolean =
      try
        future.get.asInstanceOf[CASResponse] == casResponse
      catch {
        case e if NonFatal(e) =>
          false
      }

    def hasStatus(statusCode: StatusCode): Boolean =
      Option(future.getStatus).exists(_.getStatusCode == statusCode)

    def isSuccess: Boolean =
      Option(future.getStatus).exists(_.isSuccess)

    def onComplete[F[_], B](
      cb: Either[Throwable, B] => Unit
    )(
      f: OperationFuture[?] => Unit
    ): OperationFuture[?] =
      future.addListener { completedFuture =>
        try
          f(completedFuture)
        catch {
          case e if NonFatal(e) =>
            cb(Left(e))
        }
      }
  }
}
