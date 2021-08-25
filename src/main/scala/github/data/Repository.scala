package dev.akif.githubranks
package github.data

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

/**
 * Model of a repository on GitHub
 *
 * @param name Name of the repository
 */
case class Repository(name: String)

object Repository {
  implicit lazy val contributorCodec: Codec[Repository] = deriveCodec
}
