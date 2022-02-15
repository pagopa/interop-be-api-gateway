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
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
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
  PartyManagementServiceImpl
}
import it.pagopa.interop.be.gateway.api._
import it.pagopa.interop.be.gateway.server.Controller
import it.pagopa.pdnd.interop.commons.jwt.service.{ClientAssertionValidator, JWTReader, PDNDTokenGenerator}
import it.pagopa.pdnd.interop.commons.jwt.service.impl.{
  DefaultClientAssertionValidator,
  DefaultJWTReader,
  DefaultPDNDTokenGenerator,
  getClaimsVerifier
}
import it.pagopa.pdnd.interop.commons.jwt._
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
import it.pagopa.interop.api.gateway.service.impl.PurposeManagementServiceImpl
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.api.PurposeApi
//shuts down the actor system in case of startup errors
case object StartupErrorShutdown extends CoordinatedShutdown.Reason

trait AgreementManagementDependency {
  val agreementManagementService = new AgreementManagementServiceImpl(
    AgreementManagementInvoker(),
    AgreementManagementApi(ApplicationConfiguration.agreementManagementURL)
  )
}

trait CatalogManagementDependency {
  val catalogManagementService = new CatalogManagementServiceImpl(
    CatalogManagementInvoker(),
    CatalogManagementApi(ApplicationConfiguration.catalogManagementURL)
  )
}

trait PartyManagementDependency {
  val partyManagementService = new PartyManagementServiceImpl(
    PartyManagementInvoker(),
    PartyManagementApi(ApplicationConfiguration.partyManagementURL)
  )
}

trait AuthorizationManagementDependency {
  val authorizationManagementClientApi: AuthorizationClientApi = AuthorizationClientApi(
    ApplicationConfiguration.authorizationManagementURL
  )
  val authorizationManagementKeyApi: AuthorizationKeyApi = AuthorizationKeyApi(
    ApplicationConfiguration.authorizationManagementURL
  )
  val authorizationManagementService =
    new AuthorizationManagementServiceImpl(
      AuthorizationManagementInvoker(),
      authorizationManagementKeyApi,
      authorizationManagementClientApi
    )
}

trait VaultServiceDependency {
  val vaultService: VaultService = new DefaultVaultService with DefaultVaultClient.DefaultClientInstance
}

trait AttributeRegistryManagementDependency {
  val attributeRegistryManagementApi: AttributeApi = AttributeApi(
    ApplicationConfiguration.attributeRegistryManagementURL
  )

  val attributeRegistryManagementService =
    new AttributeRegistryManagementServiceImpl(AttributeRegistryManagementInvoker(), attributeRegistryManagementApi)
}

trait PurposeManagementDependency {
  val purposeManagementService = new PurposeManagementServiceImpl(
    PurposeManagementInvoker(),
    PurposeApi(ApplicationConfiguration.purposeManagementURL)
  )
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
    with PurposeManagementDependency {

  val dependenciesLoaded: Future[(JWTReader, ClientAssertionValidator, PDNDTokenGenerator)] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset().toFuture
    jwtReader = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset: Map[KID, SerializedKey] = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
    clientAssertionValidator = new DefaultClientAssertionValidator with PublicKeysHolder {
      var publicKeyset: Map[KID, SerializedKey] = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
    pdndTokenGenerator = new DefaultPDNDTokenGenerator with PrivateKeysHolder {
      override val RSAPrivateKeyset: Map[KID, SerializedKey] =
        vaultService.readBase64EncodedData(ApplicationConfiguration.rsaPrivatePath)
      override val ECPrivateKeyset: Map[KID, SerializedKey] =
        vaultService.readBase64EncodedData(ApplicationConfiguration.ecPrivatePath)
    }
  } yield (jwtReader, clientAssertionValidator, pdndTokenGenerator)

  dependenciesLoaded.transformWith {
    case Success((jwtReader, clientAssertionValidator, pdndTokenGenerator)) =>
      launchApp(jwtReader, clientAssertionValidator, pdndTokenGenerator)
    case Failure(ex) =>
      classicActorSystem.log.error(s"Startup error: ${ex.getMessage}")
      classicActorSystem.log.error(s"${ex.getStackTrace.mkString("\n")}")
      CoordinatedShutdown(classicActorSystem).run(StartupErrorShutdown)
  }

  private def launchApp(
    jwtReader: JWTReader,
    clientAssertionValidator: ClientAssertionValidator,
    pdndTokenGenerator: PDNDTokenGenerator
  ): Future[Http.ServerBinding] = {
    Kamon.init()

    locally {
      val _ = AkkaManagement.get(classicActorSystem).start()
    }

    val authApiService: AuthApiService =
      new AuthApiServiceImpl(authorizationManagementService, clientAssertionValidator, pdndTokenGenerator)
    val authApiMarshaller: AuthApiMarshaller = AuthApiMarshallerImpl

    val gatewayApiService: GatewayApiService =
      new GatewayApiServiceImpl(
        partyManagementService,
        agreementManagementService,
        catalogManagementService,
        attributeRegistryManagementService,
        purposeManagementService
      )
    val gatewayApiMarshaller: GatewayApiMarshaller = GatewayApiMarshallerImpl

    val authApi: AuthApi = new AuthApi(
      authApiService,
      authApiMarshaller,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
    )
    val gatewayApi: GatewayApi =
      new GatewayApi(gatewayApiService, gatewayApiMarshaller, jwtReader.OAuth2JWTValidatorAsContexts)

    val healthApi: HealthApi = new HealthApi(
      new HealthServiceApiImpl(),
      HealthApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
    )

    val controller: Controller = new Controller(
      authApi,
      gatewayApi,
      healthApi,
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
