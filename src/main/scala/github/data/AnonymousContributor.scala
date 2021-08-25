package dev.akif.githubranks
package github.data

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

/**
 * Model of an anonymous contributor of a repository on GitHub
 *
 * @param email         Email of the contributor
 * @param name          Name of the contributor
 * @param contributions Number of contributions this contributor made
 */
case class AnonymousContributor(email: String, name: String, contributions: Int) {
  /**
   * Converts this anonymous contributor to a [[Contributor]]
   *
   * @return A contributor whose login is fabricated from name and email
   */
  def toContributor: Contributor = Contributor(s"$name <$email>", contributions)
}

object AnonymousContributor {
  implicit lazy val anonymousContributorCodec: Codec[AnonymousContributor] = deriveCodec
}
