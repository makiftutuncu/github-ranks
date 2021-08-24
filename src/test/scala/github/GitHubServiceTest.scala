package dev.akif.githubranks
package github

import Errors.{OrganizationNotFound, RepositoryNotFound}
import github.api.InMemoryGitHubAPI
import github.api.InMemoryGitHubAPI._
import github.data.{Contributor, Repository}

import munit.CatsEffectSuite

class GitHubServiceTest extends CatsEffectSuite {
  private val api     = InMemoryGitHubAPI()
  private val service = new GitHubService(api)

  test("Getting contributors of an invalid organization fails") {
    interceptIO[OrganizationNotFound](service.contributorsOfOrganization("invalid")).map { notFound =>
      assertEquals(notFound.organization, "invalid")
    }
  }

  test("Getting contributors of organization fails when getting contributors of a repository fails") {
    val modifiedApi = new InMemoryGitHubAPI(
      organizationsAndRepositories = Map(organization1 -> List(Repository(repository1))),
      repositoriesAndContributors  = Map.empty
    )
    val modifiedService = new GitHubService(modifiedApi)

    interceptIO[RepositoryNotFound](modifiedService.contributorsOfOrganization(organization1)).map { notFound =>
      assertEquals(notFound.repository, repository1)
    }
  }

  test("Getting contributors of organization returns unique contributors sorted descending by their total contributions") {
    val expected = List(Contributor(login3, 5), Contributor(login4, 4), Contributor(login2, 1))

    assertIO(service.contributorsOfOrganization(organization2), expected)
  }

  test("Getting repositories of an invalid organization fails") {
    interceptIO[OrganizationNotFound](service.repositoriesOfOrganization("invalid")).map { notFound =>
      assertEquals(notFound.organization, "invalid")
    }
  }

  test("Getting repositories of a organization returns list of repositories") {
    val expected = api.organizationsAndRepositories(organization1)

    assertIO(service.repositoriesOfOrganization(organization1), expected)
  }

  test("Getting contributors of an invalid repository fails") {
    interceptIO[RepositoryNotFound](service.contributorsOfRepository("invalid")).map { notFound =>
      assertEquals(notFound.repository, "invalid")
    }
  }

  test("Getting contributors of a repository returns list of contributors") {
    val expected = api.repositoriesAndContributors(repository1)

    assertIO(service.contributorsOfRepository(repository1), expected)
  }

  test("Grouping and sorting empty contributors returns empty contributors") {
    assertIO(service.groupAndSortContributors(List.empty), List.empty)
  }

  test("Grouping and sorting contributors returns unique contributors sorted descending by their total contributions") {
    val contributors = List(Contributor(login2, 2), Contributor(login1, 2), Contributor(login3, 6))

    val expected = List(Contributor(login3, 6), Contributor(login1, 2), Contributor(login2, 2))

    assertIO(service.groupAndSortContributors(contributors), expected)
  }
}
