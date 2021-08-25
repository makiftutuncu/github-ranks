package dev.akif.githubranks
package common

import cats.Parallel
import cats.effect.IO
import cats.effect.std.Random

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/**
 * Utility to traverse over a collection in parallel, performing an effect on each item and collecting results,
 * delaying each execution by a small random sleep in milliseconds
 */
object TraverseInParallelOver {
  private val random = Random.scalaUtilRandom[IO]

  /**
   * Traverse over a collection in parallel, performing an effect on each item and collecting results,
   * delaying each execution by a small random sleep in milliseconds.
   *
   * Please note that order of items are not guaranteed, in fact due to random sleeps,
   * the order will most likely to be different.
   *
   * {{{
   *   import cats.effect.IO
   *   import cats.effect.unsafe.implicits.global
   *
   *   val numbers: List[Int] = List(1, 2, 3)
   *   val numbersAndSquares: IO[List[String]] = TraverseInParallelOver(numbers) { number =>
   *     IO.pure(List(s"$number->${number * number}"))
   *   }
   *
   *   squares.unsafeRunSync() // List(1->1, 2->4, 3->9) but order may differ
   * }}}
   *
   * @param list   List of items over which to traverse
   * @param min    Minimum amount of random sleep in milliseconds
   * @param max    Maximum amount of random sleep in milliseconds
   * @param effect Effect to perform on each item
   *
   * @tparam A Type of the input items
   * @tparam B Type of the resulting items
   *
   * @return List of collected results or a failed IO if any of the effects failed
   */
  def apply[A, B](list: List[A], min: Long = 100L, max: Long = 500L)(effect: A => IO[List[B]]): IO[List[B]] =
    Parallel.parFlatTraverse[List, IO, A, B](list) { a =>
      randomDelay(min, max) *> effect(a)
    }

  private def randomDelay(min: Long, max: Long): IO[Unit] =
    random.flatMap(_.betweenLong(min, max)).flatMap { randomSleepDuration =>
      IO.sleep(FiniteDuration(randomSleepDuration, TimeUnit.MILLISECONDS))
    }
}
