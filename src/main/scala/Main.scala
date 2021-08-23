package dev.akif.githubranks

import cats.effect.*
import com.comcast.ip4s.{Host, Port}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.Router

import scala.util.control.NonFatal

object Main extends IOApp with LazyLogging:
  lazy val helloRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root =>
      Ok("Hello world!")
  }

  lazy val httpApp: HttpApp[IO] = Router("/" -> helloRoute).orNotFound

  lazy val errorHandler: PartialFunction[Throwable, IO[Response[IO]]] =
    case NonFatal(e) =>
      val response = Response[IO]()
        .withEntity(e.getMessage)
        .withStatus(Status.InternalServerError)

      IO.delay(logger.error("Unhandled error", e)) *> IO.pure(response)

  override def run(args: List[String]): IO[ExitCode] =
    for
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
    yield
      exitCode
