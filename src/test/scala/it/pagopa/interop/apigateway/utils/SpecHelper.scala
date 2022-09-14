package it.pagopa.interop.apigateway.utils

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.interop.apigateway.api.impl.GatewayApiServiceImpl
import it.pagopa.interop.apigateway.service._
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import org.scalamock.handlers.CallHandler2
import org.scalamock.scalatest.MockFactory
import spray.json.DefaultJsonProtocol

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait SpecHelper extends SprayJsonSupport with DefaultJsonProtocol with MockFactory {

  val mockAgreementManagementService: AgreementManagementService                 = mock[AgreementManagementService]
  val mockAuthorizationManagementService: AuthorizationManagementService         = mock[AuthorizationManagementService]
  val mockPartyManagementService: PartyManagementService                         = mock[PartyManagementService]
  val mockPurposeManagementService: PurposeManagementService                     = mock[PurposeManagementService]
  val mockCatalogManagementService: CatalogManagementService                     = mock[CatalogManagementService]
  val mockAttributeRegistryManagementService: AttributeRegistryManagementService =
    mock[AttributeRegistryManagementService]
  val mockNotifierService: NotifierService                                       = mock[NotifierService]
  val mockTenantProcessService: TenantProcessService                             = mock[TenantProcessService]
  val mockTenantManagementService: TenantManagementService                       = mock[TenantManagementService]

  val service: GatewayApiServiceImpl =
    GatewayApiServiceImpl(
      mockPartyManagementService,
      mockAgreementManagementService,
      mockAuthorizationManagementService,
      mockCatalogManagementService,
      mockAttributeRegistryManagementService,
      mockPurposeManagementService,
      mockNotifierService,
      mockTenantProcessService,
      mockTenantManagementService
    )(ExecutionContext.global)

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
