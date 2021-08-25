package dev.akif.githubranks
package github.api

import common.Errors
import github.data.{AnonymousContributor, Contributor, Repository}

import cats.Parallel
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Json}
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.implicits._
import org.typelevel.ci.CIString

import java.time.Instant
import scala.util.matching.Regex

/**
 * GitHub client that implements [[GitHubAPI]], using GitHub REST API v3
 *
 * @param http    HTTP client to make request
 * @param token   OAuth token to use for requests
 * @param perPage Number of items to receive in a page for paginated requests
 */
class GitHubClient(private val http: Client[IO],
                   private val token: String,
                   private val perPage: Int) extends GitHubAPI with LazyLogging {
  private val apiHost: Uri = uri"https://api.github.com"
  private val linkHeaderLastPageRegex: Regex = (s"<https:\\/\\/api\\.github\\.com\\/.+page=(\\d+)&per_page=" + perPage + ">; rel=\"last\"").r

  private implicit val anonymousOrRealContributorDecoder: Decoder[Either[AnonymousContributor, Contributor]] =
    Decoder[AnonymousContributor].either(Decoder[Contributor])

  override def repositoriesOfOrganization(organization: String): IO[List[Repository]] =
    paginatedGetRequest(
      action            = s"Getting repositories of organization '$organization' from GitHub",
      uri               = apiHost / "orgs" / organization / "repos",
      page              = 1,
      lastPage          = None,
      customStatusCheck = {
        case r if r.status == Status.NoContent =>
          IO(logger.warn(s"Organization '$organization' has no repositories")) *> IO(List.empty)

        case r if r.status == Status.NotFound =>
          IO.raiseError(Errors.OrganizationNotFound(organization))
      },
      parse = { response =>
        response.as[List[Repository]].handleErrorWith { e =>
          failWithResponseDetails(
            response, s"Failed to get repositories of organization '$organization' from GitHub", Some(e)
          )
        }
      }
    )

  override def contributorsOfRepository(organization: String, repository: String): IO[List[Contributor]] =
    paginatedGetRequest(
      action            = s"Getting contributors of repository '$organization/$repository' from GitHub",
      uri               = apiHost / "repos" / organization / repository / "contributors" +? ("anon" -> true),
      page              = 1,
      lastPage          = None,
      customStatusCheck = {
        case r if r.status == Status.NoContent =>
          IO(logger.warn(s"Repository '$organization/$repository' has no contributors")) *> IO(List.empty)

        case r if r.status == Status.NotFound =>
          IO.raiseError(Errors.RepositoryNotFound(repository))
      },
      parse = { response =>
        response
          .as[List[Either[AnonymousContributor, Contributor]]]
          .map(es => es.map(e => e.fold(ac => ac.toContributor, identity)))
          .handleErrorWith { e =>
              failWithResponseDetails(
                response, s"Failed to get contributors of repository '$organization/$repository' from GitHub", Some(e)
              )
          }
      }
    ).handleErrorWith {
      case e: Errors.APIError if e.message.contains("contributor list is too large") =>
        IO(logger.warn(s"'$organization/$repository' has too many contributors to list via API, skipping.")) *>
        IO(List.empty)

      case e =>
        IO.raiseError(e)
    }

  /**
   * Performs a GET request to GitHub with given parameters, handling OAuth, pagination and rate limiting
   *
   * @param action            Description of the action, used for logging
   * @param uri               URI of the request to make
   * @param page              Number of the page requested, used in pagination
   * @param lastPage          Number of the last page (if available), used in creating subsequent requests in pagination
   * @param customStatusCheck Partial function to perform on the response to handle status-code specific use cases
   * @param parse             Function to parse the response of successful requests to desired type
   *
   * @tparam A Type of the requested value
   *
   * @return A list of requested items (collected from all pages, if available) or a failed IO in case of an error
   */
  def paginatedGetRequest[A: Decoder](action: String,
                                      uri: Uri,
                                      page: Int,
                                      lastPage: Option[Int],
                                      customStatusCheck: PartialFunction[Response[IO], IO[List[A]]],
                                      parse: Response[IO] => IO[List[A]]): IO[List[A]] =
    IO(lastPage.fold(logger.info(action))(lp => logger.info(s"$action ($page/$lp)"))) *>
    http.run(
      Request[IO](
        uri     = uri +? ("page" -> page) +? ("per_page" -> perPage),
        headers = Headers(
          Header.Raw(CIString("Accept"), "application/vnd.github.v3+json"),
          Header.Raw(CIString("Authorization"), s"Bearer $token"),
        )
      )
    ).use { response =>
      val maybeCheckResult = checkRateLimit(response) orElse customStatusCheck.unapply(response)

      maybeCheckResult match {
        case Some(checkResult) =>
          checkResult

        case None if !response.status.isSuccess =>
          failWithResponseDetails(response, s"Action failed: $action")

        case None if page > 1 =>
          // We are performing a subsequent request to get all pages already, simply return this page.
          parse(response)

        case _ =>
          // We got the first page, try to paginate.
          val extractedLastPage = response.headers.get(CIString("Link")).map(_.head.value).flatMap {
            case linkHeaderLastPageRegex(lastPageString) => lastPageString.toIntOption
            case _                                       => None
          }

          extractedLastPage match {
            case None =>
              // We don't have the last page, just use current result.
              parse(response)

            case Some(lastPage) =>
              // Get subsequent pages in parallel and collect results.
              Parallel.parFlatTraverse(((page + 1) to lastPage).toList) { currentPage =>
                paginatedGetRequest(action, uri, currentPage, Some(lastPage), customStatusCheck, parse)
              }
          }
      }
    }

  /**
   * Checks given response for a rate limit error
   *
   * @param response An HTTP response
   *
   * @tparam A Type of the requested value
   *
   * @return Some failed IO with rate limit error when rate limit is hit, None otherwise
   */
  def checkRateLimit[A](response: Response[IO]): Option[IO[A]] =
    if (response.status != Status.Forbidden) {
      None
    } else {
      val maybeRemainingRateLimit = response.headers.get(CIString("X-RateLimit-Remaining"))
      val maybeRateLimitReset     = response.headers.get(CIString("X-RateLimit-Reset"))

      (maybeRemainingRateLimit.map(_.head.value), maybeRateLimitReset.flatMap(_.head.value.toLongOption)) match {
        case (Some("0"), Some(rateLimitReset)) =>
          Some(IO.raiseError(Errors.RateLimited(Instant.ofEpochSecond(rateLimitReset))))

        case _ =>
          None
      }
    }

  /**
   * Returns a failed IO with error, possibly including some details;
   * also logs details about the response for debugging purposes
   *
   * @param response   An HTTP response
   * @param log        Log message describing the action that failed
   * @param maybeCause An optional error that caused the failure
   *
   * @tparam A Type of the requested value
   *
   * @return A failed IO with error, possibly including some details
   */
  def failWithResponseDetails[A](response: Response[IO], log: String, maybeCause: Option[Throwable] = None): IO[A] =
    response.as[Json].redeem(
      e    => Left(e),
      body => Right(body -> (body \\ "message").headOption.flatMap(_.asString))
    ).flatMap { errorOrBodyAndDetails =>
      logger.error(
        s"""
           |$log
           |Status: ${response.status}
           |Headers: ${response.headers}
           |Body: ${errorOrBodyAndDetails.map(_._1).fold(_ => "N/A", _.toString)}
           |""".stripMargin
      )
      IO.raiseError(
        Errors.Unavailable(
          maybeCause = maybeCause orElse errorOrBodyAndDetails.left.toOption,
          details    = errorOrBodyAndDetails.toOption.flatMap(_._2)
        )
      )
    }
}
