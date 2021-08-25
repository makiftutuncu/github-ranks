package dev.akif.githubranks
package github.api

import common.Errors
import github.data.{Contributor, Repository}

import cats.effect.IO

/**
 * GitHub client that contains some in-memory data for testing
 *
 * @param organizationsAndRepositories Map of organization names to their repositories
 * @param repositoriesAndContributors  Map of repository names to their contributors
 */
class InMemoryGitHubAPI(val organizationsAndRepositories: Map[String, List[Repository]],
                        val repositoriesAndContributors: Map[String, List[Contributor]]) extends GitHubAPI {
  override def repositoriesOfOrganization(organization: String): IO[List[Repository]] = {
    organizationsAndRepositories.get(organization) match {
      case None => IO.raiseError(Errors.OrganizationNotFound(organization))
      case Some(repositories) => IO.pure(repositories)
    }
  }

  override def contributorsOfRepository(organization: String, repository: String): IO[List[Contributor]] =
    repositoriesAndContributors.get(repository) match {
      case None => IO.raiseError(Errors.RepositoryNotFound(repository))
      case Some(repositories) => IO.pure(repositories)
    }
}

object InMemoryGitHubAPI {
  val organization1 = "organization1"
  val organization2 = "organization2"
  val repository1   = "repository1"
  val repository2   = "repository2"
  val repository3   = "repository3"
  val repository4   = "repository4"
  val login1        = "login1"
  val login2        = "login2"
  val login3        = "login3"
  val login4        = "login4"

  def apply(): InMemoryGitHubAPI =
    new InMemoryGitHubAPI(
      organizationsAndRepositories =
        Map(
          organization1 -> List(Repository(repository1), Repository(repository2)),
          organization2 -> List(Repository(repository3), Repository(repository4)),
        ),
      repositoriesAndContributors =
        Map(
          repository1 -> List(Contributor(login1, 1), Contributor(login2, 2)),
          repository2 -> List(Contributor(login1, 1)),
          repository3 -> List(Contributor(login3, 5)),
          repository4 -> List(Contributor(login2, 1), Contributor(login4, 4))
        )
    )
}
