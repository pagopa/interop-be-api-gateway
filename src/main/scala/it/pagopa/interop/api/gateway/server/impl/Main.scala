package it.pagopa.interop.api.gateway.server.impl

import akka.actor.CoordinatedShutdown
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.api.gateway.api.impl.{
  AuthApiMarshallerImpl,
  AuthApiServiceImpl,
  GatewayApiMarshallerImpl,
  GatewayApiServiceImpl,
  problemOf
}
import it.pagopa.interop.api.gateway.common.ApplicationConfiguration
import it.pagopa.interop.api.gateway.common.system.{classicActorSystem, executionContext}
import it.pagopa.interop.api.gateway.service._
import it.pagopa.interop.api.gateway.service.impl.{
  AgreementManagementServiceImpl,
  AttributeRegistryManagementServiceImpl,
  AuthorizationManagementServiceImpl,
  CatalogManagementServiceImpl,
  JWTGeneratorImpl,
  JWTValidatorImpl,
  PartyManagementServiceImpl
}
import it.pagopa.interop.be.gateway.api._
import it.pagopa.interop.be.gateway.server.Controller
import it.pagopa.pdnd.interop.commons.jwt.service.JWTReader
import it.pagopa.pdnd.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.pdnd.interop.commons.jwt.{JWTConfiguration, PublicKeysHolder}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.pdnd.interop.commons.utils.{CORSSupport, OpenapiUtils}
import it.pagopa.pdnd.interop.commons.vault.service.VaultService
import it.pagopa.pdnd.interop.commons.vault.service.impl.{DefaultVaultClient, DefaultVaultService}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.{AgreementApi => AgreementManagementApi}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.{EServiceApi => CatalogManagementApi}
import it.pagopa.pdnd.interop.uservice.keymanagement.client.api.{
  ClientApi => AuthorizationClientApi,
  KeyApi => AuthorizationKeyApi
}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.{PartyApi => PartyManagementApi}
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
  val authorizationManagementService =
    new AuthorizationManagementServiceImpl(AuthorizationManagementInvoker(), authorizationManagementKeyApi)
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

trait AttributeRegistryManagementDependency {
  val attributeRegistryManagementApi: AttributeApi = AttributeApi(
    ApplicationConfiguration.getAttributeRegistryManagementURL
  )

  val attributeRegistryManagementService =
    new AttributeRegistryManagementServiceImpl(AttributeRegistryManagementInvoker(), attributeRegistryManagementApi)
}

object Main
    extends App
    with CORSSupport
    with VaultServiceDependency
    with AgreementManagementDependency
    with AuthorizationManagementDependency
    with CatalogManagementDependency
    with PartyManagementDependency
    with AttributeRegistryManagementDependency
    with JWTGeneratorDependency
    with JWTValidatorDependency {

  val dependenciesLoaded: Future[JWTReader] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset().toFuture
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
  } yield jwtValidator

  dependenciesLoaded.transformWith {
    case Success(jwtValidator) => launchApp(jwtValidator)
    case Failure(ex) =>
      classicActorSystem.log.error(s"Startup error: ${ex.getMessage}")
      classicActorSystem.log.error(s"${ex.getStackTrace.mkString("\n")}")
      CoordinatedShutdown(classicActorSystem).run(StartupErrorShutdown)
  }

  private def launchApp(jwtReader: JWTReader): Future[Http.ServerBinding] = {
    Kamon.init()

    locally {
      val _ = AkkaManagement.get(classicActorSystem).start()
    }

    val authApiService: AuthApiService       = new AuthApiServiceImpl()
    val authApiMarshaller: AuthApiMarshaller = AuthApiMarshallerImpl

    val gatewayApiService: GatewayApiService =
      new GatewayApiServiceImpl(
        partyManagementService,
        agreementManagementService,
        catalogManagementService,
        attributeRegistryManagementService
      )
    val gatewayApiMarshaller: GatewayApiMarshaller = GatewayApiMarshallerImpl

    val authApi: AuthApi = new AuthApi(
      authApiService,
      authApiMarshaller,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
    )
    val gatewayApi: GatewayApi =
      new GatewayApi(gatewayApiService, gatewayApiMarshaller, jwtReader.OAuth2JWTValidatorAsContexts)

    val controller: Controller = new Controller(
      authApi,
      gatewayApi,
      validationExceptionToRoute = Some(report => {
        val error =
          problemOf(
            StatusCodes.BadRequest,
            ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
          )
        complete(error.status, error)(AuthApiMarshallerImpl.toEntityMarshallerProblem)
      })
    )

    val server: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))

    server
  }
}
