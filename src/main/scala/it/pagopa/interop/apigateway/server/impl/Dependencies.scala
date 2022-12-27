package it.pagopa.interop.apigateway.server.impl

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.http.scaladsl.server.{Directive1, Route}
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.api.{AgreementApi => AgreementManagementApi}
import it.pagopa.interop.apigateway.api.impl.{
  GatewayApiMarshallerImpl,
  GatewayApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl
}
import it.pagopa.interop.apigateway.api.{GatewayApi, HealthApi}
import it.pagopa.interop.apigateway.common.ApplicationConfiguration
import it.pagopa.interop.apigateway.api.impl.ResponseHandlers.serviceCode
import it.pagopa.interop.apigateway.service._
import it.pagopa.interop.apigateway.service.impl._
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.authorizationmanagement.client.api.{ClientApi => AuthorizationManagementApi}
import it.pagopa.interop.catalogmanagement.client.api.{EServiceApi => CatalogManagementApi}
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.ratelimiter.RateLimiter
import it.pagopa.interop.commons.ratelimiter.akkahttp.RateLimiterDirective
import it.pagopa.interop.commons.ratelimiter.impl.RedisRateLimiter
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.errors.{Problem => CommonProblem}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}
import it.pagopa.interop.notifier.client.api.EventsApi
import it.pagopa.interop.partyregistryproxy.client.api.InstitutionApi
import it.pagopa.interop.purposemanagement.client.api.PurposeApi
import it.pagopa.interop.tenantmanagement.client.api.{TenantApi => TenantManagementApi}
import it.pagopa.interop.tenantprocess.client.api.{TenantApi => TenantProcessApi}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Dependencies {

  implicit val loggerTI: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog]("OAuth2JWTValidatorAsContexts")

  val rateLimiter: RateLimiter =
    RedisRateLimiter(ApplicationConfiguration.rateLimiterConfigs, OffsetDateTimeSupplier)

  val rateLimiterDirective: ExecutionContext => Seq[(String, String)] => Directive1[Seq[(String, String)]] = {
    val logger: LoggerTakingImplicit[ContextFieldsToLog] = Logger.takingImplicit[ContextFieldsToLog](this.getClass)
    ec => contexts => RateLimiterDirective.rateLimiterDirective(rateLimiter)(contexts)(ec, serviceCode, logger)
  }

  def agreementManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new AgreementManagementServiceImpl(
      AgreementManagementInvoker(blockingEc)(actorSystem.classicSystem),
      AgreementManagementApi(ApplicationConfiguration.agreementManagementURL)
    )

  def authorizationManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new AuthorizationManagementServiceImpl(
      AuthorizationManagementInvoker(blockingEc)(actorSystem.classicSystem),
      AuthorizationManagementApi(ApplicationConfiguration.authorizationManagementURL)
    )

  def catalogManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new CatalogManagementServiceImpl(
      CatalogManagementInvoker(blockingEc)(actorSystem.classicSystem),
      CatalogManagementApi(ApplicationConfiguration.catalogManagementURL)
    )

  def notifierService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) = new NotifierServiceImpl(
    NotifierInvoker(blockingEc)(actorSystem.classicSystem),
    EventsApi(ApplicationConfiguration.notifierURL)
  )

  def attributeRegistryManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new AttributeRegistryManagementServiceImpl(
      AttributeRegistryManagementInvoker(blockingEc)(actorSystem.classicSystem),
      AttributeApi(ApplicationConfiguration.attributeRegistryManagementURL)
    )

  def partyRegistryProxyService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new PartyRegistryProxyServiceImpl(
      PartyRegistryInvoker(blockingEc)(actorSystem.classicSystem),
      InstitutionApi(ApplicationConfiguration.partyRegistryProxyURL)
    )
  def purposeManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new PurposeManagementServiceImpl(
      PurposeManagementInvoker(blockingEc)(actorSystem.classicSystem),
      PurposeApi(ApplicationConfiguration.purposeManagementURL)
    )

  def tenantProcessService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new TenantProcessServiceImpl(
      TenantProcessInvoker(blockingEc)(actorSystem.classicSystem),
      TenantProcessApi(ApplicationConfiguration.tenantProcessURL)
    )

  def tenantManagementService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new TenantManagementServiceImpl(
      TenantManagementInvoker(blockingEc)(actorSystem.classicSystem),
      TenantManagementApi(ApplicationConfiguration.tenantManagementURL)
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

  def gatewayApi(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): GatewayApi =
    new GatewayApi(
      GatewayApiServiceImpl(
        agreementManagementService(blockingEc),
        authorizationManagementService(blockingEc),
        catalogManagementService(blockingEc),
        attributeRegistryManagementService(blockingEc),
        partyRegistryProxyService(blockingEc),
        purposeManagementService(blockingEc),
        notifierService(blockingEc),
        tenantProcessService(blockingEc),
        tenantManagementService(blockingEc)
      ),
      GatewayApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts.flatMap(rateLimiterDirective(ec))
    )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator),
    loggingEnabled = false
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      CommonProblem(StatusCodes.BadRequest, OpenapiUtils.errorFromRequestValidationReport(report), serviceCode, None)
    complete(error.status, error)
  }

}
