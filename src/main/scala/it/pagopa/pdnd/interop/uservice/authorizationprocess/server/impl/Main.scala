package it.pagopa.pdnd.interop.uservice.authorizationprocess.server.impl

import akka.actor.CoordinatedShutdown
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import it.pagopa.pdnd.interop.commons.jwt.service.JWTReader
import it.pagopa.pdnd.interop.commons.jwt.service.impl.DefaultJWTReader
import it.pagopa.pdnd.interop.commons.jwt.{JWTConfiguration, PublicKeysHolder}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.pdnd.interop.commons.utils.{CORSSupport, OpenapiUtils}
import it.pagopa.pdnd.interop.commons.vault.service.VaultService
import it.pagopa.pdnd.interop.commons.vault.service.impl.{DefaultVaultClient, DefaultVaultService}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.{AgreementApi => AgreementManagementApi}
import it.pagopa.pdnd.interop.uservice.authorizationprocess.api.impl.problemOf
import it.pagopa.pdnd.interop.uservice.authorizationprocess.api.{AuthApi, ClientApi, OperatorApi}
import it.pagopa.pdnd.interop.uservice.authorizationprocess.common.ApplicationConfiguration
import it.pagopa.pdnd.interop.uservice.authorizationprocess.common.system.{classicActorSystem, executionContext}
import it.pagopa.pdnd.interop.uservice.authorizationprocess.server.Controller
import it.pagopa.pdnd.interop.uservice.authorizationprocess.service._
import it.pagopa.pdnd.interop.uservice.authorizationprocess.service.impl.{
  AgreementManagementServiceImpl,
  CatalogManagementServiceImpl,
  JWTGeneratorImpl,
  JWTValidatorImpl,
  PartyManagementServiceImpl
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.{EServiceApi => CatalogManagementApi}
import it.pagopa.pdnd.interop.uservice.keymanagement.client.api.{
  ClientApi => AuthorizationClientApi,
  KeyApi => AuthorizationKeyApi
}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.{PartyApi => PartyManagementApi}
import it.pagopa.pdnd.interop.uservice.userregistrymanagement.client.api.{UserApi => UserRegistryManagementApi}
import it.pagopa.pdnd.interop.uservice.userregistrymanagement.client.invoker.ApiKeyValue
import kamon.Kamon

import scala.concurrent.Future
import scala.util.{Failure, Success}
//shuts down the actor system in case of startup errors
case object StartupErrorShutdown extends CoordinatedShutdown.Reason

trait AgreementManagementDependency {
  val agreementManagementService = new AgreementManagementServiceImpl(
    AgreementManagementInvoker(),
    AgreementManagementApi(ApplicationConfiguration.getAgreementManagementURL)
  )
}

trait CatalogManagementDependency {
  val catalogManagementService = new CatalogManagementServiceImpl(
    CatalogManagementInvoker(),
    CatalogManagementApi(ApplicationConfiguration.getCatalogManagementURL)
  )
}

trait PartyManagementDependency {
  val partyManagementService = new PartyManagementServiceImpl(
    PartyManagementInvoker(),
    PartyManagementApi(ApplicationConfiguration.getPartyManagementURL)
  )
}

trait AuthorizationManagementDependency {
  val authorizationManagementClientApi: AuthorizationClientApi = AuthorizationClientApi(
    ApplicationConfiguration.getAuthorizationManagementURL
  )
  val authorizationManagementKeyApi: AuthorizationKeyApi = AuthorizationKeyApi(
    ApplicationConfiguration.getAuthorizationManagementURL
  )
  val authorizationManagementService = new AuthorizationManagementServiceImpl(
    AuthorizationManagementInvoker(),
    AuthorizationClientApi(ApplicationConfiguration.getAuthorizationManagementURL),
    AuthorizationKeyApi(ApplicationConfiguration.getAuthorizationManagementURL)
  )
}

trait VaultServiceDependency {
  val vaultService: VaultService = new DefaultVaultService with DefaultVaultClient.DefaultClientInstance
}

trait JWTGeneratorDependency { self: VaultServiceDependency =>
  val jwtGenerator: JWTGeneratorImpl = JWTGeneratorImpl(vaultService)
}

trait JWTValidatorDependency { self: AuthorizationManagementDependency with VaultServiceDependency =>
  val jwtValidator: JWTValidatorImpl = JWTValidatorImpl(authorizationManagementService, vaultService)
}

object Main
    extends App
    with CORSSupport
    with VaultServiceDependency
    with AgreementManagementDependency
    with AuthorizationManagementDependency
    with CatalogManagementDependency
    with PartyManagementDependency
    with JWTGeneratorDependency
    with JWTValidatorDependency
    with M2MAuthorizationService
    with UserRegistryManagementDependency {

  val dependenciesLoaded: Future[JWTReader] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset().toFuture
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset = keyset
    }
  } yield jwtValidator

  dependenciesLoaded.transformWith {
    case Success(jwtValidator) => launchApp(jwtValidator)
    case Failure(ex) => {
      classicActorSystem.log.error(s"Startup error: ${ex.getMessage}")
      classicActorSystem.log.error(s"${ex.getStackTrace.mkString("\n")}")
      CoordinatedShutdown(classicActorSystem).run(StartupErrorShutdown)
    }
  }

  private def launchApp(jwtReader: JWTReader): Future[Http.ServerBinding] = {
    Kamon.init()

    locally {
      val _ = AkkaManagement.get(classicActorSystem).start()
    }

    val controller: Controller = new Controller(
      authApi,
      clientApi,
      operatorApi,
      validationExceptionToRoute = Some(report => {
        val error =
          problemOf(
            StatusCodes.BadRequest,
            ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
          )
        complete(error.status, error)(ClientApiMarshallerImpl.toEntityMarshallerProblem)
      })
    )

    val server: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))

    server
  }
}
