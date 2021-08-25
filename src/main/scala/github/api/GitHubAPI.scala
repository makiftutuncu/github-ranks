package dev.akif.githubranks
package github.api

import github.data.{Contributor, Repository}

import cats.effect.IO

/**
 * Interface of the operations that can be performed on GitHub
 */
trait GitHubAPI {
  /**
   * Gets a list of repositories under given organization
   *
   * @param organization Name of the organization
   *
   * @return List of repositories under given organization or a failed IO in case of an error
   */
  def repositoriesOfOrganization(organization: String): IO[List[Repository]]

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
  def contributorsOfRepository(organization: String, repository: String): IO[List[Contributor]]
}
