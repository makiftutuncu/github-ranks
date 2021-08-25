package dev.akif.githubranks
package github.data

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

/**
 * Model of a contributor of a repository on GitHub
 *
 * @param login         Login of the contributor
 * @param contributions Number of contributions this contributor made
 */
case class Contributor(login: String, contributions: Int)

object Contributor {
  implicit lazy val contributorCodec: Codec[Contributor] = deriveCodec
}
