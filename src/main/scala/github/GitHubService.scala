package dev.akif.githubranks
package github

import github.api.GitHubAPI
import github.data.{Contributor, Repository}

import cats.Parallel
import cats.effect.IO
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._

class GitHubService(private val api: GitHubAPI) {
  lazy val route: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "org" / organization / "contributors" =>
        contributorsOfOrganization(organization).flatMap(contributors => Ok(contributors))
    }

  def contributorsOfOrganization(organization: String): IO[List[Contributor]] =
    for {
      repositories       <- repositoriesOfOrganization(organization)
      contributors       <- Parallel.parFlatTraverse(repositories)(r => contributorsOfRepository(r.name))
      sortedContributors <- groupAndSortContributors(contributors)
    } yield {
      sortedContributors
    }

  def repositoriesOfOrganization(organization: String): IO[List[Repository]] =
    api.repositoriesOfOrganization(organization)

  def contributorsOfRepository(repository: String): IO[List[Contributor]] =
    api.contributorsOfRepository(repository)

  def groupAndSortContributors(contributors: List[Contributor]): IO[List[Contributor]] =
    IO {
      contributors
        .groupMapReduce(_.login)(_.contributions)(_ + _)
        .toList
        .map { case (login, totalContributions) => Contributor(login, totalContributions) }
        .sortBy(cs => (cs.contributions * -1, cs.login))
    }
}
