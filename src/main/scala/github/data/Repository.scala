package dev.akif.githubranks
package github.data

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class Repository(name: String)

object Repository {
  implicit lazy val contributorEncoder: Encoder[Repository] = deriveEncoder
}
