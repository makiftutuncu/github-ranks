package dev.akif.githubranks

import github.GitHubService
import github.api.{GitHubAPI, InMemoryGitHubAPI}

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.{Host, Port}
import com.typesafe.scalalogging.LazyLogging
import org.http4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router

import scala.util.control.NonFatal

object Main extends IOApp with LazyLogging {
  lazy val gitHubAPI: GitHubAPI = InMemoryGitHubAPI()

  lazy val gitHubService: GitHubService = new GitHubService(gitHubAPI)

  lazy val httpApp: HttpApp[IO] = Router("/" -> gitHubService.route).orNotFound

  lazy val errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = {
    case e: Errors.APIError =>
      IO(logger.warn("API error", e)) *> e.toResponse

    case NonFatal(cause) =>
      val e = Errors.Unhandled(cause)
      IO(logger.error("Unhandled error", e)) *> e.toResponse
  }

  override def run(args: List[String]): IO[ExitCode] =
    for {
      host     <- IO.fromOption(Host.fromString("localhost"))(new IllegalArgumentException("Invalid host!"))
      port     <- IO.fromOption(Port.fromInt(8080))(new IllegalArgumentException("Invalid port!"))
      exitCode <- EmberServerBuilder.default[IO]
                                    .withHost(host)
                                    .withPort(port)
                                    .withHttpApp(httpApp)
                                    .withErrorHandler(errorHandler)
                                    .build
                                    .use(_ => IO.never)
                                    .as(ExitCode.Success)
    } yield {
      exitCode
    }
}
