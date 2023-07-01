package it.pagopa.interop.apigateway.utils

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.interop.apigateway.api.impl.GatewayApiServiceImpl
import it.pagopa.interop.apigateway.service._
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.scalamock.handlers.CallHandler2
import org.scalamock.scalatest.MockFactory
import spray.json.DefaultJsonProtocol

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait SpecHelper extends SprayJsonSupport with DefaultJsonProtocol with MockFactory {

  val mockAgreementProcessService: AgreementProcessService                 = mock[AgreementProcessService]
  val mockAuthorizationProcessService: AuthorizationProcessService         = mock[AuthorizationProcessService]
  val mockPartyRegistryProxy: PartyRegistryProxyService                    = mock[PartyRegistryProxyService]
  val mockPurposeProcessService: PurposeProcessService                     = mock[PurposeProcessService]
  val mockCatalogProcessService: CatalogProcessService                     = mock[CatalogProcessService]
  val mockAttributeRegistryProcessService: AttributeRegistryProcessService =
    mock[AttributeRegistryProcessService]
  val mockNotifierService: NotifierService                                 = mock[NotifierService]
  val mockTenantProcessService: TenantProcessService                       = mock[TenantProcessService]
  val timestamp: OffsetDateTime                                            = OffsetDateTime.now()
  val readModel: ReadModelService                                          = mock[ReadModelService]

  val service: GatewayApiServiceImpl =
    GatewayApiServiceImpl(
      mockAgreementProcessService,
      mockAuthorizationProcessService,
      mockCatalogProcessService,
      mockAttributeRegistryProcessService,
      mockPartyRegistryProxy,
      mockPurposeProcessService,
      mockNotifierService,
      mockTenantProcessService
    )(ExecutionContext.global, readModel)

  def randomString(): String = Random.alphanumeric.take(40).mkString

  def mockClientRetrieve(clientId: UUID, result: AuthorizationProcess.Client)(implicit
    contexts: Seq[(String, String)]
  ): CallHandler2[UUID, Seq[(String, String)], Future[AuthorizationProcess.Client]] =
    (mockAuthorizationProcessService
      .getClientById(_: UUID)(_: Seq[(String, String)]))
      .expects(clientId, contexts)
      .returning(Future.successful(result))
      .once()

  def mockEServiceRetrieve(clientId: UUID, result: CatalogProcess.EService)(implicit
    contexts: Seq[(String, String)]
  ): CallHandler2[UUID, Seq[(String, String)], Future[CatalogProcess.EService]] =
    (mockCatalogProcessService
      .getEServiceById(_: UUID)(_: Seq[(String, String)]))
      .expects(clientId, contexts)
      .returning(Future.successful(result))
      .once()

}
