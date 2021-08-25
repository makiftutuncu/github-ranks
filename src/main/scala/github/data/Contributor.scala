package dev.akif.githubranks
package github.data

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec

/**
 * Abstract model of a contributor of a repository on GitHub
 */
sealed trait Contributor {
  val contributions: Int
}

object Contributor {
  implicit lazy val contributorDecoder: Decoder[Contributor] =
    Decoder[Anonymous].either(Decoder[Known]).flatMap {
      case Left(anonymous) => Decoder.const(anonymous)
      case Right(known)    => Decoder.const(known)
    }

  implicit lazy val contributorEncoder: Encoder[Contributor] =
    Encoder.instance[Contributor] {
      case anonymous: Anonymous => Encoder[Anonymous].apply(anonymous)
      case known: Known         => Encoder[Known].apply(known)
    }

  /**
   * Model of a known contributor of a repository on GitHub
   *
   * @param login         Login of the contributor
   * @param contributions Number of contributions this contributor made
   */
  case class Known(login: String, contributions: Int) extends Contributor

  object Known {
    implicit lazy val knownCodec: Codec[Known] = deriveCodec
  }

  /**
   * Model of an anonymous contributor of a repository on GitHub
   *
   * @param email         Email of the contributor
   * @param name          Name of the contributor
   * @param contributions Number of contributions this contributor made
   */
  case class Anonymous(email: String, name: String, contributions: Int) extends Contributor {
    /**
     * Converts this anonymous contributor to a [[Contributor]]
     *
     * @return A contributor whose login is fabricated from name and email
     */
    def toKnown: Known = Known(s"$name <$email>", contributions)
  }

  object Anonymous {
    implicit lazy val anonymousCodec: Codec[Anonymous] = deriveCodec
  }
}
