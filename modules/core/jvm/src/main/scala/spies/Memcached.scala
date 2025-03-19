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

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.syntax.all.*
import net.spy.memcached.*
import net.spy.memcached.internal.GetFuture
import net.spy.memcached.internal.OperationFuture
import net.spy.memcached.ops.OperationStatus
import net.spy.memcached.ops.StatusCode
import scala.concurrent.duration.Duration
import spies.internal.expiry.*
import spies.internal.future.*

trait Memcached[F[_]] {

  /**
    * Gets the current value for the specified key,
    * along with a function for updating the value
    * and expiry.
    *
    * The returned function is using check-and-set,
    * so it might not succeed. It returns `true` if
    * the key was updated; `false` otherwise.
    */
  def access[A](
    key: String
  )(
    implicit codec: Codec[A]
  ): F[(Option[A], (A, Duration) => F[Boolean])]

  /**
    * Sets the specified key to the provided value and
    * expiry, but only if the key is currently unset.
    *
    * Returns `true` if the specified key was set to the
    * provided value; `false` otherwise.
    */
  def add[A](
    key: String,
    value: A,
    expiry: Duration
  )(
    implicit codec: Codec[A]
  ): F[Boolean]

  /**
    * Deletes the value for the specified key.
    *
    * Returns `true` if the key was set
    * before deletion; `false` otherwise.
    */
  def delete(key: String): F[Boolean]

  /**
    * Gets the current value for the specified key.
    *
    * Returns `Some` with the current value, or
    * `None` if the key is currently unset.
    */
  def get[A](
    key: String
  )(
    implicit codec: Codec[A]
  ): F[Option[A]]

  /**
    * Gets the current value for the specified key,
    * and sets a new value and expiry.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    *
    * Returns `Some` with the current value, or
    * `None` if the key is currently unset.
    */
  def getAndSet[A](
    key: String,
    value: A,
    expiry: Duration
  )(
    implicit codec: Codec[A]
  ): F[Option[A]]

  /**
    * Gets the current value for the specified key,
    * and updates it using the provided function
    * and expiry.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    *
    * Returns `Some` with the current value, or
    * `None` if the key is currently unset.
    */
  def getAndUpdate[A](
    key: String,
    expiry: Duration
  )(
    f: Option[A] => A
  )(
    implicit codec: Codec[A]
  ): F[Option[A]]

  /**
    * Gets the current value for the specified key,
    * or the default value if the key is unset.
    */
  def getOrElse[A](
    key: String,
    default: => A
  )(
    implicit codec: Codec[A]
  ): F[A]

  /**
    * Updates the value for the specified key using
    * the provided function and expiry.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    *
    * Returns the value returned by the specified function.
    */
  def modify[A, B](
    key: String,
    expiry: Duration
  )(
    f: Option[A] => (A, B)
  )(
    implicit codec: Codec[A]
  ): F[B]

  /**
    * Optionally updates the value for the specified
    * key using the provided function and expiry.
    *
    * When the provided function returns `None`, no
    * update will be performed. Similarly, when the
    * function returns `Some`, the value will be
    * updated to the returned value.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    *
    * Returns the value returned by the specified function.
    */
  def modifyOption[A, B](
    key: String,
    expiry: Duration
  )(
    f: Option[A] => (Option[A], B)
  )(
    implicit codec: Codec[A]
  ): F[B]

  /**
    * Optionally updates the value for the specified
    * key using the provided function and the expiry
    * returned by the function.
    *
    * When the provided function returns `None`, no
    * update will be performed. Similarly, when the
    * function returns `Some`, the value will be
    * updated to the returned value.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    *
    * Returns the value returned by the specified function.
    */
  def modifyOption[A, B](
    key: String
  )(
    f: Option[A] => (Option[(A, Duration)], B)
  )(
    implicit codec: Codec[A]
  ): F[B]

  /**
    * Sets the specified key to the provided value and expiry.
    */
  def set[A](
    key: String,
    value: A,
    expiry: Duration
  )(
    implicit codec: Codec[A]
  ): F[Unit]

  /**
    * Updates the expiry for the specified key.
    *
    * The function might not succeed if the key
    * is unset.
    *
    * Returns `true` if the expiry was updated;
    * `false` otherwise.
    */
  def touch(key: String, expiry: Duration): F[Boolean]

  /**
    * Updates the value for the specified key using
    * the provided function and expiry.
    *
    * The function is using check-and-set, so it
    * might not succeed.
    *
    * Returns `Some` with the value returned by the
    * provided function if the value was updated;
    * `None` otherwise.
    */
  def tryModify[A, B](
    key: String,
    expiry: Duration
  )(
    f: Option[A] => (A, B)
  )(
    implicit codec: Codec[A]
  ): F[Option[B]]

  /**
    * Updates the value for the specified key using
    * the provided function and expiry.
    *
    * The function is using check-and-set, so it
    * might not succeed.
    *
    * Returns `true` if the value was updated;
    * `false` otherwise.
    */
  def tryUpdate[A](
    key: String,
    expiry: Duration
  )(
    f: Option[A] => A
  )(
    implicit codec: Codec[A]
  ): F[Boolean]

  /**
    * Updates the value for the specified key using
    * the provided function and expiry.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    */
  def update[A](
    key: String,
    expiry: Duration
  )(
    f: Option[A] => A
  )(
    implicit codec: Codec[A]
  ): F[Unit]

  /**
    * Optionally updates the value for the specified
    * key using the provided function and expiry.
    *
    * When the provided function returns `None`, no
    * update will be performed. Similarly, when the
    * function returns `Some`, the value will be
    * updated to the returned value.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    */
  def updateOption[A](
    key: String,
    expiry: Duration
  )(
    f: Option[A] => Option[A]
  )(
    implicit codec: Codec[A]
  ): F[Unit]

  /**
    * Optionally updates the value for the specified
    * key using the provided function and the expiry
    * returned by the function.
    *
    * When the provided function returns `None`, no
    * update will be performed. Similarly, when the
    * function returns `Some`, the value will be
    * updated to the returned value.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    */
  def updateOption[A](
    key: String
  )(
    f: Option[A] => Option[(A, Duration)]
  )(
    implicit codec: Codec[A]
  ): F[Unit]

  /**
    * Updates the value for the specified key
    * using the provided function and expiry,
    * returning the updated value.
    *
    * The function is using check-and-set, so it
    * might be retried several times.
    */
  def updateAndGet[A](
    key: String,
    expiry: Duration
  )(
    f: Option[A] => A
  )(
    implicit codec: Codec[A]
  ): F[A]
}

object Memcached {
  def apply[F[_]](
    addresses: String
  )(
    implicit F: Async[F]
  ): Resource[F, Memcached[F]] =
    ascii(addresses)

  def ascii[F[_]](
    addresses: String
  )(
    implicit F: Async[F]
  ): Resource[F, Memcached[F]] =
    Resource
      .eval(F.delay(new DefaultConnectionFactory))
      .flatMap(fromConnectionFactory(addresses, _))

  def binary[F[_]](
    addresses: String
  )(
    implicit F: Async[F]
  ): Resource[F, Memcached[F]] =
    Resource
      .eval(F.delay(new BinaryConnectionFactory))
      .flatMap(fromConnectionFactory(addresses, _))

  def builder[F[_]](
    addresses: String
  )(
    f: ConnectionFactoryBuilder => ConnectionFactoryBuilder
  )(
    implicit F: Async[F]
  ): Resource[F, Memcached[F]] =
    Resource
      .eval(F.delay(f(new ConnectionFactoryBuilder)))
      .flatMap(fromBuilder(addresses, _))

  def fromBuilder[F[_]](
    addresses: String,
    builder: ConnectionFactoryBuilder
  )(
    implicit F: Async[F]
  ): Resource[F, Memcached[F]] =
    Resource
      .eval(F.delay(builder.build()))
      .flatMap(fromConnectionFactory(addresses, _))

  def fromClient[F[_]](
    client: MemcachedClient
  )(
    implicit F: Async[F]
  ): Memcached[F] =
    new Memcached[F] {
      override def access[A](
        key: String
      )(
        implicit codec: Codec[A]
      ): F[(Option[A], (A, Duration) => F[Boolean])] =
        gets[A](key).map {
          case None =>
            (None, (value, expiry) => add(key, value, expiry))
          case Some((a, casId)) =>
            (Some(a), (value, expiry) => sets(key, value, expiry, casId))
        }

      override def add[A](
        key: String,
        value: A,
        expiry: Duration
      )(
        implicit codec: Codec[A]
      ): F[Boolean] =
        expiryTime[F](expiry).flatMap { expiryTime =>
          asyncOperation { cb =>
            client
              .add(key, expiryTime, codec.encode(value))
              .onComplete(cb) {
                case future if future.isSuccess =>
                  cb(Right(true))
                case future if future.hasStatus(StatusCode.ERR_EXISTS) =>
                  cb(Right(false))
                case future if future.hasStatus(StatusCode.ERR_NOT_STORED) =>
                  cb(Right(false))
                case future =>
                  cb(Left(error(s"add(key = $key, value = $value, expiry = $expiry)", future)))
              }
          }
        }

      override def delete(key: String): F[Boolean] =
        asyncOperation { cb =>
          client.delete(key).onComplete(cb) {
            case future if future.isSuccess =>
              cb(Right(true))
            case future if future.hasStatus(StatusCode.ERR_NOT_FOUND) =>
              cb(Right(false))
            case future =>
              cb(Left(error(s"delete(key = $key)", future)))
          }
        }

      private def error(
        operation: String,
        status: Option[OperationStatus],
        cause: => Option[Throwable]
      ): MemcachedError =
        status match {
          case Some(status) =>
            MemcachedError(s"Memcached $operation failed: ${status.getMessage} (${status.getStatusCode})")
          case None =>
            MemcachedError(s"Memcached $operation failed", cause)
        }

      private def error(operation: String, future: GetFuture[?]): MemcachedError =
        error(
          operation = operation,
          status = Option(future.getStatus),
          cause = Either.catchNonFatal(future.get).left.toOption
        )

      private def error(operation: String, future: OperationFuture[?]): MemcachedError =
        error(
          operation = operation,
          status = Option(future.getStatus),
          cause = Either.catchNonFatal(future.get).left.toOption
        )

      override def get[A](
        key: String
      )(
        implicit codec: Codec[A]
      ): F[Option[A]] =
        asyncGet { cb =>
          client
            .asyncGet(key)
            .onComplete(cb) {
              case future if future.isSuccess =>
                cb(codec.decode(future.get.asInstanceOf[Array[Byte]]).map(_.some))
              case future if future.hasStatus(StatusCode.ERR_NOT_FOUND) =>
                cb(Right(none))
              case future =>
                cb(Left(error(s"get(key = $key)", future)))
            }
        }

      private[Memcached] def gets[A](
        key: String
      )(
        implicit codec: Codec[A]
      ): F[Option[(A, Long)]] =
        asyncOperation { cb =>
          client
            .asyncGets(key)
            .onComplete(cb) {
              case future if future.isSuccess =>
                val cas = future.get.asInstanceOf[CASValue[?]]
                val bytes = cas.getValue.asInstanceOf[Array[Byte]]
                cb(codec.decode(bytes).tupleRight(cas.getCas).map(_.some))
              case future if future.hasStatus(StatusCode.ERR_NOT_FOUND) =>
                cb(Right(none))
              case future =>
                cb(Left(error(s"gets(key = $key)", future)))
            }
        }

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
        F.tailRecM(()) { _ =>
          gets[A](key).flatMap {
            case None =>
              f(none) match {
                case (Some((fa, expiry)), fb) =>
                  add(key, fa, expiry).map {
                    case false => Left(())
                    case true => Right(fb)
                  }

                case (None, fb) =>
                  F.pure(Right(fb))
              }

            case Some((a, casId)) =>
              f(a.some) match {
                case (Some((fa, expiry)), fb) =>
                  sets(key, fa, expiry, casId).map {
                    case false => Left(())
                    case true => Right(fb)
                  }

                case (None, fb) =>
                  F.pure(Right(fb))
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
        expiryTime[F](expiry).flatMap { expiryTime =>
          asyncOperation { cb =>
            client
              .set(key, expiryTime, codec.encode(value))
              .onComplete(cb) {
                case future if future.isSuccess =>
                  cb(Right(()))
                case future =>
                  cb(Left(error(s"set(key = $key, value = $value, expiry = $expiry)", future)))
              }
          }
        }

      private[Memcached] def sets[A](
        key: String,
        value: A,
        expiry: Duration,
        casId: Long
      )(
        implicit codec: Codec[A]
      ): F[Boolean] =
        expiryTime[F](expiry).flatMap { expiryTime =>
          asyncOperation { cb =>
            client
              .asyncCAS(key, casId, expiryTime, codec.encode(value))
              .onComplete(cb) {
                case future if future.isSuccess =>
                  cb(Right(true))
                case future if future.hasCasResponse(CASResponse.EXISTS) =>
                  cb(Right(false))
                case future if future.hasCasResponse(CASResponse.NOT_FOUND) =>
                  cb(Right(false))
                case future =>
                  cb(
                    Left(
                      error(
                        s"sets(key = $key, value = $value, expiry = $expiry, casId = $casId)",
                        future
                      )
                    )
                  )
              }
          }
        }

      override def touch(key: String, expiry: Duration): F[Boolean] =
        expiryTime[F](expiry).flatMap { expiryTime =>
          asyncOperation { cb =>
            client
              .touch(key, expiryTime)
              .onComplete(cb) {
                case future if future.isSuccess =>
                  cb(Right(true))
                case future if future.hasStatus(StatusCode.ERR_NOT_FOUND) =>
                  cb(Right(false))
                case future =>
                  cb(Left(error(s"touch(key = $key, expiry = $expiry)", future)))
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
        gets[A](key).flatMap {
          case None =>
            val (fa, fb) = f(none)
            add(key, fa, expiry).map {
              case false => none
              case true => fb.some
            }

          case Some((a, casId)) =>
            val (fa, fb) = f(a.some)
            sets(key, fa, expiry, casId).map {
              case false => none
              case true => fb.some
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

  def fromConnectionFactory[F[_]](
    addresses: String,
    connectionFactory: ConnectionFactory
  )(
    implicit F: Async[F]
  ): Resource[F, Memcached[F]] = {
    val acquire: F[MemcachedClient] =
      F.blocking {
        new MemcachedClient(
          connectionFactory,
          AddrUtil.getAddresses(addresses)
        )
      }

    val release: MemcachedClient => F[Unit] =
      client => F.blocking(client.shutdown())

    Resource.make(acquire)(release).map(fromClient[F])
  }

  def ketama[F[_]](
    addresses: String
  )(
    implicit F: Async[F]
  ): Resource[F, Memcached[F]] =
    Resource
      .eval(F.delay(new KetamaConnectionFactory))
      .flatMap(fromConnectionFactory(addresses, _))

  def localhost[F[_]](
    implicit F: Async[F]
  ): Resource[F, Memcached[F]] =
    builder("localhost:11211")(_.setClientMode(ClientMode.Static))
}
