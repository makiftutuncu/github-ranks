package dev.akif.githubranks
package github.data

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class Contributor(login: String, contributions: Int)

object Contributor {
  implicit lazy val contributorEncoder: Encoder[Contributor] = deriveEncoder
}
