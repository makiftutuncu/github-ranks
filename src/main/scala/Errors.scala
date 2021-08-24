package dev.akif.githubranks

import cats.effect.IO
import io.circe.Encoder
import org.http4s.{Response, Status}
import org.http4s.circe.CirceEntityCodec._

object Errors {
  sealed abstract class APIError(val message: String,
                                 val maybeCause: Option[Throwable] = None) extends Exception(message,
                                                                                             maybeCause.orNull,
                                                                                             false,
                                                                                             maybeCause.isDefined) {
    def code: Int

    def toResponse: IO[Response[IO]] =
      IO {
        Response[IO]()
          .withEntity(this)
          .withStatus(Status.fromInt(code).getOrElse(Status.InternalServerError))
      }
  }

  object APIError {
    implicit lazy val apiErrorEncoder: Encoder[APIError] = Encoder.forProduct1("error")(_.message)
  }

  sealed abstract class NotFoundError(val what: String, val kind: String) extends APIError(
    s"'$what' is not found. Make sure it is a valid and public $kind name on GitHub."
  ) {
    override val code: Int = Status.NotFound.code
  }

  case class OrganizationNotFound(organization: String) extends NotFoundError(organization, "organization")

  case class RepositoryNotFound(repository: String) extends NotFoundError(repository, "repository")

  case class Unhandled(cause: Throwable) extends APIError("Unhandled error", Some(cause)) {
    override val code: Int = Status.InternalServerError.code
  }
}
