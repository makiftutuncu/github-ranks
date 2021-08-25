package dev.akif.githubranks
package common

import common.Config.{GitHub, Server}

import cats.effect.IO
import com.comcast.ip4s.{Host, Port}
import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigReader}
import pureconfig.generic.semiauto._

/**
 * Application configuration
 *
 * @param github [[GitHub]] configuration
 * @param server [[Server]] configuration
 */
case class Config(github: GitHub, server: Server)

object Config {
  implicit def configProductHint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  implicit lazy val configReader: ConfigReader[Config] = deriveReader

  /**
   * GitHub configuration
   *
   * @param token   OAuth token to use for requests
   * @param perPage Number of items to receive in a page for paginated requests
   */
  case class GitHub(token: String, perPage: Int)

  object GitHub {
    implicit lazy val githubReader: ConfigReader[GitHub] = deriveReader
  }

  /**
   * Server configuration
   *
   * @param host Host of the application
   * @param port Port of the application
   */
  case class Server(host: String, port: Int) {
    /**
     * Make [[Host]] from the config value
     *
     * @return Created [[Host]] or a failed IO if host is invalid
     */
    def makeHost: IO[Host] =
      IO.fromOption(Host.fromString(host))(Errors.InvalidConfig("server.host", host))

    /**
     * Make [[Port]] from the config value
     *
     * @return Created [[Port]] or a failed IO if port is invalid
     */
    def makePort: IO[Port] =
      IO.fromOption(Port.fromInt(port))(Errors.InvalidConfig("server.port", port))
  }

  object Server {
    implicit lazy val serverReader: ConfigReader[Server] = deriveReader
  }
}
