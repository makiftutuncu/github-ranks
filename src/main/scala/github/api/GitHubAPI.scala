package dev.akif.githubranks
package github.api

import github.data.{Contributor, Repository}

import cats.effect.IO

trait GitHubAPI {
  def repositoriesOfOrganization(organization: String): IO[List[Repository]]

  def contributorsOfRepository(repository: String): IO[List[Contributor]]
}
