package it.pagopa.interop.apigateway.utils

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.nimbusds.jwt.JWTClaimsSet
import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.apigateway.api.impl.GatewayApiServiceImpl
import it.pagopa.interop.apigateway.service._
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalamock.handlers.CallHandler2
import org.scalamock.scalatest.MockFactory
import spray.json.DefaultJsonProtocol

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

trait SpecHelper extends SprayJsonSupport with DefaultJsonProtocol with MockFactory {

  final val bearerToken: String = "token"

  val config: Config = ConfigFactory
    .parseResourcesAnySyntax("application-test")
    .resolve()

  val mockAgreementManagementService: AgreementManagementService                 = mock[AgreementManagementService]
  val mockAuthorizationManagementService: AuthorizationManagementService         = mock[AuthorizationManagementService]
  val mockPartyManagementService: PartyManagementService                         = mock[PartyManagementService]
  val mockPurposeManagementService: PurposeManagementService                     = mock[PurposeManagementService]
  val mockCatalogManagementService: CatalogManagementService                     = mock[CatalogManagementService]
  val mockAttributeRegistryManagementService: AttributeRegistryManagementService =
    mock[AttributeRegistryManagementService]

  val mockUUIDSupplier: UUIDSupplier               = mock[UUIDSupplier]
  val mockDateTimeSupplier: OffsetDateTimeSupplier = mock[OffsetDateTimeSupplier]

  val service: GatewayApiServiceImpl =
    GatewayApiServiceImpl(
      mockPartyManagementService,
      mockAgreementManagementService,
      mockAuthorizationManagementService,
      mockCatalogManagementService,
      mockAttributeRegistryManagementService,
      mockPurposeManagementService
    )(ExecutionContext.global)

  def mockSubject(uuid: String): Try[JWTClaimsSet] = Success(new JWTClaimsSet.Builder().subject(uuid).build())

  def mockClientRetrieve(clientId: UUID, result: AuthorizationManagement.Client)(implicit
    contexts: Seq[(String, String)]
  ): CallHandler2[UUID, Seq[(String, String)], Future[AuthorizationManagement.Client]] =
    (mockAuthorizationManagementService
      .getClientById(_: UUID)(_: Seq[(String, String)]))
      .expects(clientId, contexts)
      .returning(Future.successful(result))
      .once()

  def mockEServiceRetrieve(clientId: UUID, result: CatalogManagement.EService)(implicit
    contexts: Seq[(String, String)]
  ): CallHandler2[UUID, Seq[(String, String)], Future[CatalogManagement.EService]] =
    (mockCatalogManagementService
      .getEService(_: UUID)(_: Seq[(String, String)]))
      .expects(clientId, contexts)
      .returning(Future.successful(result))
      .once()

}
