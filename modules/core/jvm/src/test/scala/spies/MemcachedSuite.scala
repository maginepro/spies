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

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.effect.PropF.*
import scala.concurrent.duration.*

abstract class MemcachedSuite extends CatsEffectSuite with ScalaCheckEffectSuite {
  def memcached: IO[Memcached[IO]]

  test("add(key) >> touch(key) == true") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val add = memcached.add(key.value, value, expiry.value)
          val touch = memcached.touch(key.value, expiry.value)
          add >> touch.assertEquals(true)
        }
    }
  }

  test("delete(key) >> add(key) == true") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val delete = memcached.delete(key.value)
          val add = memcached.add(key.value, value, expiry.value)
          delete >> add.assertEquals(true)
        }
    }
  }

  test("delete(key) >> delete(key) == false") {
    forAllNoShrinkF { (key: MemcachedKey) =>
      memcached.flatMap { memcached =>
        val delete = memcached.delete(key.value)
        delete >> delete.assertEquals(false)
      }
    }
  }

  test("delete(key) >> get(key) == none") {
    forAllNoShrinkF { (key: MemcachedKey) =>
      memcached.flatMap { memcached =>
        val delete = memcached.delete(key.value)
        val get = memcached.get[Int](key.value)
        delete >> get.assertEquals(none)
      }
    }
  }

  test("delete(key) >> getOrElse(key, value) == value") {
    forAllNoShrinkF { (key: MemcachedKey, value: Int) =>
      memcached.flatMap { memcached =>
        val delete = memcached.delete(key.value)
        val getOrElse = memcached.getOrElse(key.value, value)
        delete >> getOrElse.assertEquals(value)
      }
    }
  }

  test("delete(key) >> getAndUpdate(key, value) == none") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val delete = memcached.delete(key.value)
          val getAndUpdate = memcached.getAndUpdate[Int](key.value, expiry.value)(_ => value)
          delete >> getAndUpdate.assertEquals(none)
        }
    }
  }

  test("delete(key) >> touch(key) == false") {
    forAllNoShrinkF { (key: MemcachedKey, expiry: MemcachedExpiry) =>
      memcached.flatMap { memcached =>
        val delete = memcached.delete(key.value)
        val touch = memcached.touch(key.value, expiry.value)
        delete >> touch.assertEquals(false)
      }
    }
  }

  test("delete(key) >> update(key, _ + 1) >> get(key) == sum") {
    memcached.flatMap { memcached =>
      val delete = memcached.delete("key")
      val update = memcached.update[Int]("key", 10.seconds)(_.fold(1)(_ + 1))
      val updates = List.fill(100)(update).parSequence_
      val get = memcached.get[Int]("key")
      delete >> updates >> get.assertEquals(100.some)
    }
  }

  test("delete(key) >> updateOption(none) >> get(key) == none") {
    forAllNoShrinkF { (key: MemcachedKey, expiry: MemcachedExpiry) =>
      memcached.flatMap { memcached =>
        val delete = memcached.delete(key.value)
        val updateOption = memcached.updateOption[Int](key.value, expiry.value)(_ => None)
        val get = memcached.get[Int](key.value)
        delete >> updateOption >> get.assertEquals(None)
      }
    }
  }

  test("delete(key) >> updateOption(value) >> get(key) == value") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val delete = memcached.delete(key.value)
          val updateOption = memcached.updateOption[Int](key.value, expiry.value)(_ => Some(value))
          val get = memcached.get[Int](key.value)
          delete >> updateOption >> get.assertEquals(Some(value))
        }
    }
  }

  test("set(key, value) >> add(key, value) == false") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value, expiry.value)
          val add = memcached.add(key.value, value, expiry.value)
          set >> add.assertEquals(false)
        }
    }
  }

  test("set(key, value) >> delete(key) == true") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value, expiry.value)
          val delete = memcached.delete(key.value)
          set >> delete.assertEquals(true)
        }
    }
  }

  test("set(key, value) >> get(key) == value") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value, expiry.value)
          val get = memcached.get[Int](key.value)
          set >> get.assertEquals(value.some)
        }
    }
  }

  test("set(key, value) >> getAndUpdate(key, value2) == value") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        value2: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value, expiry.value)
          val getAndUpdate = memcached.getAndUpdate[Int](key.value, expiry.value)(_ => value2)
          set >> getAndUpdate.assertEquals(value.some)
        }
    }
  }

  test("set(key, value) >> getOrElse(key, value2) == value") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        value2: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value, expiry.value)
          val getOrElse = memcached.getOrElse(key.value, value2)
          set >> getOrElse.assertEquals(value)
        }
    }
  }

  test("set(key, value) >> delete(key) >> get(key) == none") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value, expiry.value)
          val delete = memcached.delete(key.value)
          val get = memcached.get[Int](key.value)
          set >> delete >> get.assertEquals(none)
        }
    }
  }

  test("set(key, value) >> update(key, value2) >> get(key) == value2") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        value2: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value, expiry.value)
          val update = memcached.update[Int](key.value, expiry.value)(_ => value2)
          val get = memcached.get[Int](key.value)
          set >> update >> get.assertEquals(value2.some)
        }
    }
  }

  test("set(key, value) >> updateOption(none) >> get(key) == value") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value, expiry.value)
          val updateOption = memcached.updateOption[Int](key.value, expiry.value)(_ => None)
          val get = memcached.get[Int](key.value)
          set >> updateOption >> get.assertEquals(Some(value))
        }
    }
  }

  test("set(key, value1) >> updateOption(value2) >> get(key) == value2") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value1: Int,
        value2: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val set = memcached.set(key.value, value1, expiry.value)
          val updateOption = memcached.updateOption[Int](key.value, expiry.value)(_ => Some(value2))
          val get = memcached.get[Int](key.value)
          set >> updateOption >> get.assertEquals(Some(value2))
        }
    }
  }

  test("update(key, value) >> get(key) == value") {
    forAllNoShrinkF {
      (
        key: MemcachedKey,
        value: Int,
        expiry: MemcachedExpiry
      ) =>
        memcached.flatMap { memcached =>
          val update = memcached.update[Int](key.value, expiry.value)(_ => value)
          val get = memcached.get[Int](key.value)
          update >> get.assertEquals(value.some)
        }
    }
  }
}
