package dev.akif.githubranks
package common

import cats.effect.IO
import cats.effect.kernel.Clock
import munit.CatsEffectSuite

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class TraverseInParallelOverTest extends CatsEffectSuite {
  test("Traversing in parallel over list of items returns collected results") {
    val clock       = Clock[IO]
    val effectSleep = 200L
    val minSleep    = 100L
    val maxSleep    = 500L
    val list        = (1 to 5).toList

    val elapsedTimeWithDoublesAndSquaresAsStrings =
      clock.timed {
        TraverseInParallelOver(list, minSleep, maxSleep) { number =>
          // Simulate a long running operation by sleeping
          IO.sleep(FiniteDuration(effectSleep, TimeUnit.MILLISECONDS)) *>
          IO.pure(List(s"${number * 2}", s"${number * number}"))
        }
      }

    elapsedTimeWithDoublesAndSquaresAsStrings.map {
      case (duration, list) =>
        val minTotalSleep = effectSleep + minSleep
        val maxTotalSleep = effectSleep + maxSleep
        val totalMillis   = duration.toMillis

        assertEquals(list, List("2", "1", "4", "4", "6", "9", "8", "16", "10", "25"))
        assert(totalMillis >= minTotalSleep, s"Took $totalMillis but should have taken at least $minTotalSleep")
        assert(totalMillis <= maxTotalSleep, s"Took $totalMillis but should have taken at most $maxTotalSleep")
    }
  }
}
