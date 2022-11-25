package it.pagopa.interop.apigateway.server.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import buildinfo.BuildInfo
import cats.syntax.all._
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.apigateway.common.ApplicationConfiguration
import it.pagopa.interop.apigateway.server.Controller
import it.pagopa.interop.commons.logging.renderBuildInfo
import it.pagopa.interop.commons.utils.CORSSupport

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.{Failure, Success}
import akka.actor.typed.DispatcherSelector

object Main extends App with CORSSupport with Dependencies {

  private val logger: Logger = Logger(this.getClass)

  ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      implicit val actorSystem: ActorSystem[_]        = context.system
      implicit val executionContext: ExecutionContext = actorSystem.executionContext

      val selector: DispatcherSelector         = DispatcherSelector.fromConfig("futures-dispatcher")
      val blockingEc: ExecutionContextExecutor = actorSystem.dispatchers.lookup(selector)

      AkkaManagement.get(actorSystem.classicSystem).start()

      logger.info(renderBuildInfo(BuildInfo))

      val serverBinding = for {
        jwtReader <- getJwtValidator()
        gateway    = gatewayApi(jwtReader, blockingEc)
        controller = new Controller(
          gateway = gateway,
          health = healthApi,
          validationExceptionToRoute = validationExceptionToRoute.some
        )(actorSystem.classicSystem)
        binding <- Http()(actorSystem.classicSystem)
          .newServerAt("0.0.0.0", ApplicationConfiguration.serverPort)
          .bind(corsHandler(controller.routes))
      } yield binding

      serverBinding.onComplete {
        case Success(b) =>
          logger.info(s"Started server at ${b.localAddress.getHostString}:${b.localAddress.getPort}")
        case Failure(e) =>
          actorSystem.terminate()
          logger.error("Startup error: ", e)
      }

      Behaviors.empty
    },
    BuildInfo.name
  )
}
