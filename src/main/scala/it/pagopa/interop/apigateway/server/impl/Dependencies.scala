package it.pagopa.interop.apigateway.server.impl

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.agreementmanagement.client.api.{AgreementApi => AgreementManagementApi}
import it.pagopa.interop.apigateway.api.impl.{
  GatewayApiMarshallerImpl,
  GatewayApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  problemOf
}
import it.pagopa.interop.apigateway.api.{GatewayApi, HealthApi}
import it.pagopa.interop.apigateway.common.ApplicationConfiguration
import it.pagopa.interop.apigateway.service._
import it.pagopa.interop.apigateway.service.impl._
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.authorizationmanagement.client.api.{ClientApi => AuthorizationManagementApi}
import it.pagopa.interop.catalogmanagement.client.api.{EServiceApi => CatalogManagementApi}
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}
import it.pagopa.interop.notifier.client.api.EventsApi
import it.pagopa.interop.partymanagement.client.api.{PartyApi => PartyManagementApi}
import it.pagopa.interop.purposemanagement.client.api.PurposeApi

import scala.concurrent.{ExecutionContext, Future}

trait Dependencies {

  def agreementManagementService()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new AgreementManagementServiceImpl(
      AgreementManagementInvoker()(actorSystem.classicSystem),
      AgreementManagementApi(ApplicationConfiguration.agreementManagementURL)
    )

  def authorizationManagementService()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new AuthorizationManagementServiceImpl(
      AuthorizationManagementInvoker()(actorSystem.classicSystem),
      AuthorizationManagementApi(ApplicationConfiguration.authorizationManagementURL)
    )

  def catalogManagementService()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new CatalogManagementServiceImpl(
      CatalogManagementInvoker()(actorSystem.classicSystem),
      CatalogManagementApi(ApplicationConfiguration.catalogManagementURL)
    )

  def partyManagementService()(implicit actorSystem: ActorSystem[_]) =
    new PartyManagementServiceImpl(
      PartyManagementInvoker()(actorSystem.classicSystem),
      PartyManagementApi(ApplicationConfiguration.partyManagementURL)
    )

  def notifierService()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) = new NotifierServiceImpl(
    NotifierInvoker()(actorSystem.classicSystem),
    EventsApi(ApplicationConfiguration.notifierURL)
  )

  def attributeRegistryManagementService()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new AttributeRegistryManagementServiceImpl(
      AttributeRegistryManagementInvoker()(actorSystem.classicSystem),
      AttributeApi(ApplicationConfiguration.attributeRegistryManagementURL)
    )

  def purposeManagementService()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new PurposeManagementServiceImpl(
      PurposeManagementInvoker()(actorSystem.classicSystem),
      PurposeApi(ApplicationConfiguration.purposeManagementURL)
    )

  def getJwtValidator()(implicit ec: ExecutionContext): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .toFuture
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey] = keyset

        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )

  def gatewayApi(jwtReader: JWTReader)(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): GatewayApi =
    new GatewayApi(
      GatewayApiServiceImpl(
        partyManagementService(),
        agreementManagementService(),
        authorizationManagementService(),
        catalogManagementService(),
        attributeRegistryManagementService(),
        purposeManagementService(),
        notifierService()
      ),
      GatewayApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      problemOf(
        StatusCodes.BadRequest,
        GenericComponentErrors.ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
      )
    complete(error.status, error)(HealthApiMarshallerImpl.toEntityMarshallerProblem)
  }

}
