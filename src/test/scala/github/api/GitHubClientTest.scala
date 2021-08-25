package dev.akif.githubranks
package github.api

import common.{Config, Errors}
import github.data.{Contributor, Repository}

import cats.effect.{IO, Resource}
import io.circe.Json
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s.{EmptyBody, EntityBody, Header, Headers, InvalidMessageBodyFailure, MalformedMessageBodyFailure, Response, Status}
import org.http4s.client.Client
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec._
import org.typelevel.ci.CIString

import java.time.Instant
import java.time.temporal.ChronoField

class GitHubClientTest extends CatsEffectSuite {
  private val config = Config.GitHub("token", 10)

  private def mockClient(body: EntityBody[IO] = EmptyBody,
                         status: Status = Status.Ok,
                         headers: Headers = Headers.empty): Client[IO] =
    Client.apply[IO] { _ =>
      Resource.liftK.apply(IO(Response(status = status, headers = headers, body = body)))
    }

  private def jsonEntityBody(json: Json): EntityBody[IO] =
    jsonEncoderOf[IO, Json].toEntity(json).body

  test("Getting repositories of organization returns empty list when organization has no repositories") {
    val http = mockClient(status = Status.NoContent)
    val client = new GitHubClient(http, config)

    assertIO(client.repositoriesOfOrganization("test"), List.empty[Repository])
  }

  test("Getting repositories of organization fails when organization is not found") {
    val http = mockClient(status = Status.NotFound)
    val client = new GitHubClient(http, config)

    interceptIO[Errors.OrganizationNotFound](client.repositoriesOfOrganization("test")).map { error =>
      assertEquals(error.organization, "test")
    }
  }

  test("Getting repositories of organization fails when API response cannot be parsed") {
    val http = mockClient(body = jsonEntityBody(Json.obj("message" := "test")))
    val client = new GitHubClient(http, config)

    interceptIO[Errors.Unavailable](client.repositoriesOfOrganization("test")).map { error =>
      assert(
        error.maybeCause.exists(_.isInstanceOf[InvalidMessageBodyFailure]),
        s"Error wasn't InvalidMessageBodyFailure but was ${error.maybeCause.map(_.getClass.getSimpleName)}"
      )
      assertEquals(error.details, Some("test"))
    }
  }

  test("Getting repositories of organization returns repositories received from GitHub") {
    val repositories = List(Repository("repository1"), Repository("repository2"))

    val http = mockClient(body = jsonEntityBody(Json.arr(repositories.map(_.asJson):_*)))
    val client = new GitHubClient(http, config)

    assertIO(client.repositoriesOfOrganization("test"), repositories)
  }

  test("Getting contributors of repository returns empty list when repository has no contributors") {
    val http = mockClient(status = Status.NoContent)
    val client = new GitHubClient(http, config)

    assertIO(client.contributorsOfRepository("test", "test"), List.empty[Contributor])
  }

  test("Getting contributors of repository fails when repository is not found") {
    val http = mockClient(status = Status.NotFound)
    val client = new GitHubClient(http, config)

    interceptIO[Errors.RepositoryNotFound](client.contributorsOfRepository("test-organization", "test-repository")).map { error =>
      assertEquals(error.repository, "test-organization/test-repository")
    }
  }

  test("Getting contributors of repository fails when API response cannot be parsed") {
    val http = mockClient(body = jsonEntityBody(Json.obj("message" := "test")))
    val client = new GitHubClient(http, config)

    interceptIO[Errors.Unavailable](client.contributorsOfRepository("test", "test")).map { error =>
      assert(
        error.maybeCause.exists(_.isInstanceOf[InvalidMessageBodyFailure]),
        s"Error wasn't InvalidMessageBodyFailure but was ${error.maybeCause.map(_.getClass.getSimpleName)}"
      )
      assertEquals(error.details, Some("test"))
    }
  }

  test("Getting contributors of repository returns empty list when contributor list is too large") {
    val http = mockClient(body = jsonEntityBody(Json.obj("message" := "contributor list is too large")))
    val client = new GitHubClient(http, config)

    assertIO(client.contributorsOfRepository("test", "test"), List.empty[Contributor])
  }

  test("Getting contributors of repository returns repositories received from GitHub") {
    val contributors = List[Contributor](Contributor.Known("login1", 1), Contributor.Anonymous("email", "name", 2), Contributor.Known("login2", 3))

    val http = mockClient(body = jsonEntityBody(Json.arr(contributors.map(_.asJson):_*)))
    val client = new GitHubClient(http, config)

    assertIO(client.contributorsOfRepository("test", "test"), contributors)
  }

  test("Paginated request fails when rate limit is hit") {
    val instant = Instant.now

    val http = mockClient(
      status = Status.Forbidden,
      headers = Headers(
        Header.Raw(CIString("X-RateLimit-Remaining"), "0"),
        Header.Raw(CIString("X-RateLimit-Reset"), instant.getEpochSecond.toString)
      )
    )
    val client = new GitHubClient(http, config)

    val response = client.paginatedGetRequest("test", GitHubClient.apiHost, 1, None, PartialFunction.empty, _.as[List[Json]])

    interceptIO[Errors.RateLimited](response).map { error =>
      assertEquals(error.resetInstant, instant.`with`(ChronoField.NANO_OF_SECOND, 0))
    }
  }

  test("Paginated request fails when custom status check returns a result") {
    val http = mockClient(status = Status.NoContent)
    val client = new GitHubClient(http, config)

    val response = client.paginatedGetRequest("test", GitHubClient.apiHost, 1, None, {
      case Status.NoContent(_) => IO(List("test".asJson))
    }, _.as[List[Json]])

    assertIO(response, List("test".asJson))
  }

  test("Paginated request fails when response status is not successful") {
    val http = mockClient(status = Status.InternalServerError, body = jsonEntityBody(Json.obj("message" := "test")))
    val client = new GitHubClient(http, config)

    val response = client.paginatedGetRequest("test", GitHubClient.apiHost, 1, None, PartialFunction.empty, _.as[List[Json]])

    interceptIO[Errors.Unavailable](response).map { error =>
      assert(error.maybeCause.isEmpty)
      assertEquals(error.details, Some("test"))
    }
  }

  test("Paginated request fails when parsing response is fails") {
    val http = mockClient(body = jsonEntityBody(Json.obj("message" := "test")))
    val client = new GitHubClient(http, config)

    val response = client.paginatedGetRequest("test", GitHubClient.apiHost, 1, None, PartialFunction.empty, _.as[List[Json]])

    interceptIO[InvalidMessageBodyFailure](response)
  }

  test("Paginated request returns first page when response doesn't contain pagination info") {
    val http = mockClient(body = jsonEntityBody(Json.arr("test".asJson)))
    val client = new GitHubClient(http, config)

    val response = client.paginatedGetRequest("test", GitHubClient.apiHost, 1, None, PartialFunction.empty, _.as[List[Json]])

    assertIO(response, List("test".asJson))
  }

  test("Paginated request fails when subsequent requests fails") {
    val http = Client.apply[IO] { request =>
      request.uri.query.params("page") match {
        case "1" =>
          val response1 = Response(body = jsonEntityBody(Json.arr("test".asJson)), headers = Headers(
            Header.Raw(CIString("Link"), """<https://api.github.com?page=2&per_page=10>; rel="last"""")
          ))
          Resource.liftK.apply(IO(response1))

        case "2" =>
          val response2 = Response[IO](status = Status.InternalServerError, body = jsonEntityBody(Json.obj("message" := "test")))
          Resource.liftK.apply(IO(response2))
      }
    }
    val client = new GitHubClient(http, config)

    val response = client.paginatedGetRequest("test", GitHubClient.apiHost, 1, None, PartialFunction.empty, _.as[List[Json]])

    interceptIO[Errors.Unavailable](response).map { error =>
      assert(error.maybeCause.isEmpty)
      assertEquals(error.details, Some("test"))
    }
  }

  test("Paginated request returns all subsequent results") {
    val http = Client.apply[IO] { request =>
      request.uri.query.params("page") match {
        case "1" =>
          val response1 = Response(body = jsonEntityBody(Json.arr("test1".asJson)), headers = Headers(
            Header.Raw(CIString("Link"), """<https://api.github.com?page=2&per_page=10>; rel="last"""")
          ))
          Resource.liftK.apply(IO(response1))

        case "2" =>
          val response2 = Response(body = jsonEntityBody(Json.arr("test2".asJson)))
          Resource.liftK.apply(IO(response2))
      }
    }
    val client = new GitHubClient(http, config)

    val response = client.paginatedGetRequest("test", GitHubClient.apiHost, 1, None, PartialFunction.empty, _.as[List[Json]])

    assertIO(response, List("test1".asJson, "test2".asJson))
  }

  test("Checking rate limit returns None when response status is not Forbidden") {
    assertEquals(GitHubClient.checkRateLimit(Response[IO]()), None)
  }

  test("Checking rate limit returns None when rate limit headers are invalid") {
    assertEquals(GitHubClient.checkRateLimit(Response[IO](status = Status.Forbidden)), None)
  }

  test("Checking rate limit returns rate limit error when rate limit is hit") {
    val now = Instant.now.getEpochSecond
    val response = Response[IO](
      status = Status.Forbidden,
      headers = Headers(
        Header.Raw(CIString("X-RateLimit-Remaining"), "0"),
        Header.Raw(CIString("X-RateLimit-Reset"), now.toString)
      ))

    val result = GitHubClient.checkRateLimit(response)
    assert(result.isDefined)
    interceptIO[Errors.RateLimited](result.get).map { error =>
      assertEquals(error.resetInstant, Instant.ofEpochSecond(now))
    }
  }

  test("Failing with response details returns given cause when a cause is given") {
    interceptIO[Errors.Unavailable](GitHubClient.failWithResponseDetails(Response[IO](), "test", Some(new Exception("test")))).map { error =>
      assert(error.maybeCause.isDefined)
      assertEquals(error.maybeCause.get.getMessage, "test")
    }
  }

  test("Failing with response details returns parse error as cause when no cause is given") {
    interceptIO[Errors.Unavailable](GitHubClient.failWithResponseDetails(Response[IO](), "test", None)).map { error =>
      assert(
        error.maybeCause.exists(_.isInstanceOf[MalformedMessageBodyFailure]),
        s"Error wasn't MalformedMessageBodyFailure but was ${error.maybeCause.map(_.getClass.getSimpleName)}"
      )
      assert(error.details.isEmpty)
    }
  }

  test("Failing with response details returns parse error with details") {
    interceptIO[Errors.Unavailable](GitHubClient.failWithResponseDetails(Response[IO](body = jsonEntityBody(Json.obj("message" := "test"))), "test", None)).map { error =>
      assert(error.maybeCause.isEmpty)
      assertEquals(error.details, Some("test"))
    }
  }
}
