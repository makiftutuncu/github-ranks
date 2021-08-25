package dev.akif.githubranks
package github

import common.TraverseInParallelOver
import github.api.GitHubAPI
import github.data.{Contributor, Repository}

import cats.effect.IO
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._

/**
 * GitHub service providing HTTP endpoints for operations that can be performed on GitHub
 *
 * @param api An implementation of [[GitHubAPI]]
 */
class GitHubService(private val api: GitHubAPI) {
  lazy val route: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "org" / organization / "contributors" =>
        contributorsOfOrganization(organization).flatMap(contributors => Ok(contributors))
    }

  /**
   * Gets a list of contributors of all repositories under given organization,
   * sorted by total contributions of each unique user in descending order
   *
   * @param organization Name of the organization
   *
   * @return List of contributors of all repositories under given organization
   *         sorted by total contributions of each unique user in descending order
   *         or a failed IO in case of an error
   */
  def contributorsOfOrganization(organization: String): IO[List[Contributor]] =
    for {
      repositories       <- repositoriesOfOrganization(organization)
      contributors       <- TraverseInParallelOver(repositories)(r => contributorsOfRepository(organization, r.name))
      sortedContributors <- groupAndSortContributors(contributors)
    } yield {
      sortedContributors
    }

  /**
   * Gets a list of repositories under given organization
   *
   * @param organization Name of the organization
   *
   * @return List of repositories under given organization or a failed IO in case of an error
   */
  def repositoriesOfOrganization(organization: String): IO[List[Repository]] =
    api.repositoriesOfOrganization(organization)

  /**
   * Gets a list of contributors of given repository under given organization,
   * sorted by total contributions of each unique user in descending order
   *
   * @param organization Name of the organization
   * @param repository   Name of the repository
   *
   * @return List of contributors of given repository under given organization
   *         sorted by total contributions of each unique user in descending order
   *         or a failed IO in case of an error
   */
  def contributorsOfRepository(organization: String, repository: String): IO[List[Contributor]] =
    api.contributorsOfRepository(organization, repository)

  /**
   * Groups given contributors by their logins, summing up their contributions and sorts them in descending order
   *
   * @param contributors Some contributors
   *
   * @return List of contributors with their contributions aggregated and sorted in descending order
   */
  def groupAndSortContributors(contributors: List[Contributor]): IO[List[Contributor]] =
    IO {
      contributors
        .groupMapReduce(_.login)(_.contributions)(_ + _)
        .toList
        .map { case (login, totalContributions) => Contributor(login, totalContributions) }
        .sortBy(cs => (cs.contributions * -1, cs.login))
    }
}
