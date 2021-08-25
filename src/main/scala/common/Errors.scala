package dev.akif.githubranks
package common

import cats.effect.IO
import io.circe.Encoder
import org.http4s.{Response, Status}
import org.http4s.circe.CirceEntityCodec._

import java.time.Instant

/**
 * A container of error type hierarchy
 */
object Errors {
  /**
   * An abstract error type that's used to represent known errors, with no stack trace
   *
   * @param message    Message of the error
   * @param maybeCause An optional cause of the failure
   */
  sealed abstract class APIError(val message: String,
                                 val maybeCause: Option[Throwable] = None) extends Exception(message,
                                                                                             maybeCause.orNull,
                                                                                             false,
                                                                                             maybeCause.isDefined) {
    /**
     * A numeric code for the error, used as an HTTP response code
     *
     * @return A numeric code for the error
     */
    def code: Int

    /**
     * Converts this error into an HTTP response of appropriate status code and body
     *
     * @return HTTP response created by this error
     */
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

  /**
   * An abstract error type, specialized for not found use cases
   *
   * @param what Identifier of the thing that's not found
   * @param kind Type of the thing that's not found
   */
  sealed abstract class NotFoundError(val what: String, val kind: String) extends APIError(
    s"'$what' is not found. Make sure it is a valid and public $kind name on GitHub."
  ) {
    override val code: Int = Status.NotFound.code
  }

  /**
   * Error to be used when rate limit is hit
   *
   * @param resetInstant Instant when the rate limit will be lifted
   */
  case class RateLimited(resetInstant: Instant) extends APIError(
    s"GitHub API rate limit has been reached. You'll be able to try again after $resetInstant"
  ) {
    override val code: Int = Status.Forbidden.code
  }

  /**
   * Error to be used when an organization is not found
   *
   * @param organization Name of the organization
   */
  case class OrganizationNotFound(organization: String) extends NotFoundError(organization, "organization")

  /**
   * Error to be used when an repository is not found
   *
   * @param repository Name of the repository
   */
  case class RepositoryNotFound(repository: String) extends NotFoundError(repository, "repository")

  /**
   * Error to be used when GitHub is not available due to an error
   *
   * @param maybeCause An optional cause of the failure
   * @param details    Optionally some details about the error (error message returned from API if available)
   */
  case class Unavailable(override val maybeCause: Option[Throwable], details: Option[String] = None) extends APIError(
    s"Service is unavailable${details.fold("")(d => s", details: $d")}", maybeCause
  ) {
    override val code: Int = Status.ServiceUnavailable.code
  }

  /**
   * Error to be used when an unknown/unhandled error is encountered
   *
   * @param cause Cause of the failure
   */
  case class Unhandled(cause: Throwable) extends APIError("Unhandled error", Some(cause)) {
    override val code: Int = Status.InternalServerError.code
  }
}
