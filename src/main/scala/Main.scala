package dev.akif.githubranks

import common.{Config, Errors}
import github.GitHubService
import github.api.GitHubClient

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.LazyLogging
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import pureconfig._

import scala.util.control.NonFatal

object Main extends IOApp with LazyLogging {
  lazy val errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = {
    case e: Errors.APIError =>
      IO(logger.error("API error", e)) *> e.toResponse

    case NonFatal(cause) =>
      val e = Errors.Unhandled(cause)
      IO(logger.error("Unhandled error", e)) *> e.toResponse
  }

  override def run(args: List[String]): IO[ExitCode] =
    for {
      config   <- IO(ConfigSource.default.loadOrThrow[Config])
      host     <- config.server.makeHost
      port     <- config.server.makePort
      exitCode <- EmberClientBuilder.default[IO].build.use { http =>
        val gitHubAPI      = new GitHubClient(http, config.github)
        val gitHubService  = new GitHubService(gitHubAPI)
        val httpApp        = Router("/" -> gitHubService.route).orNotFound

        EmberServerBuilder.default[IO]
                          .withHost(host)
                          .withPort(port)
                          .withHttpApp(httpApp)
                          .withErrorHandler(errorHandler)
                          .build
                          .use(_ => IO.never)
                          .as(ExitCode.Success)
      }
    } yield {
      exitCode
    }
}
