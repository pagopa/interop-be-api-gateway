package it.pagopa.interop.apigateway

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.apigateway.api.impl._
import it.pagopa.interop.apigateway.model.Client
import it.pagopa.interop.apigateway.utils.SpecHelper
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.purposeprocess.client.{model => PurposeProcess}
import it.pagopa.interop.commons.jwt.M2M_ROLE
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, USER_ROLES}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class ClientRetrieveSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Client retrieve" should {
    "succeed if the requester is the consumer of the Client" in {
      val requesterOrganizationId             = UUID.randomUUID()
      val clientId                            = UUID.randomUUID()
      val client: AuthorizationProcess.Client = AuthorizationProcess.Client(
        id = clientId,
        consumerId = requesterOrganizationId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Set.empty,
        users = Set.empty,
        kind = AuthorizationProcess.ClientKind.CONSUMER,
        createdAt = timestamp
      )

      val expectedClient: Client = Client(id = client.id, consumerId = client.consumerId)

      implicit val contexts: Seq[(String, String)] =
        Seq(ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString, USER_ROLES -> M2M_ROLE)

      mockClientRetrieve(clientId, client)(contexts)

      Get() ~> service.getClient(clientId.toString) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Client] shouldEqual expectedClient
      }

    }

    "fail if the requester does not have M2M role" in {
      val requesterOrganizationId = UUID.randomUUID()
      val clientId                = UUID.randomUUID()

      implicit val contexts: Seq[(String, String)] =
        Seq(USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString)

      Get() ~> service.getClient(clientId.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }

    }

    "succeed if the requester is a producer of an EService related to the Client" in {
      val requesterOrganizationId = UUID.randomUUID()
      val clientId                = UUID.randomUUID()
      val consumerId              = UUID.randomUUID()
      val eServiceId              = UUID.randomUUID()
      val purposeId               = UUID.randomUUID()

      val client: AuthorizationProcess.Client = AuthorizationProcess.Client(
        id = clientId,
        consumerId = consumerId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Set(purposeId),
        users = Set.empty,
        kind = AuthorizationProcess.ClientKind.CONSUMER,
        createdAt = timestamp
      )

      val eService: CatalogProcess.EService = CatalogProcess.EService(
        id = eServiceId,
        producerId = requesterOrganizationId,
        name = "EService",
        description = "Description",
        technology = CatalogProcess.EServiceTechnology.REST,
        descriptors = Seq.empty,
        riskAnalysis = Seq.empty,
        mode = CatalogProcess.EServiceMode.DELIVER
      )

      val purpose: PurposeProcess.Purpose = PurposeProcess.Purpose(
        id = purposeId,
        eserviceId = eServiceId,
        consumerId = consumerId,
        versions = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        title = "title",
        description = "description",
        riskAnalysisForm = None,
        createdAt = timestamp,
        updatedAt = None,
        isRiskAnalysisValid = false,
        isFreeOfCharge = true,
        freeOfChargeReason = Some("ok")
      )

      val expectedClient: Client = Client(id = client.id, consumerId = client.consumerId)

      implicit val contexts: Seq[(String, String)] =
        Seq(ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString, USER_ROLES -> M2M_ROLE)

      mockClientRetrieve(clientId, client)(contexts)
      mockPurposeRetrieve(purposeId, purpose)(contexts)
      mockEServiceRetrieve(eServiceId, eService)(contexts)

      Get() ~> service.getClient(clientId.toString) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Client] shouldEqual expectedClient
      }

    }

    "fail if the requester is neither the consumer or the producer" in {
      val requesterOrganizationId = UUID.randomUUID()
      val clientId                = UUID.randomUUID()
      val consumerId              = UUID.randomUUID()
      val producerId              = UUID.randomUUID()
      val eServiceId              = UUID.randomUUID()
      val purposeId               = UUID.randomUUID()

      val client: AuthorizationProcess.Client = AuthorizationProcess.Client(
        id = clientId,
        consumerId = consumerId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Set(purposeId),
        users = Set.empty,
        kind = AuthorizationProcess.ClientKind.CONSUMER,
        createdAt = timestamp
      )

      val eService: CatalogProcess.EService = CatalogProcess.EService(
        id = eServiceId,
        producerId = producerId,
        name = "EService",
        description = "Description",
        technology = CatalogProcess.EServiceTechnology.REST,
        descriptors = Seq.empty,
        riskAnalysis = Seq.empty,
        mode = CatalogProcess.EServiceMode.DELIVER
      )

      val purpose: PurposeProcess.Purpose = PurposeProcess.Purpose(
        id = purposeId,
        eserviceId = eServiceId,
        consumerId = consumerId,
        versions = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        title = "title",
        description = "description",
        riskAnalysisForm = None,
        createdAt = timestamp,
        updatedAt = None,
        isRiskAnalysisValid = false,
        isFreeOfCharge = true,
        freeOfChargeReason = Some("ok")
      )

      implicit val contexts: Seq[(String, String)] =
        Seq(USER_ROLES -> "m2m", ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString)

      mockClientRetrieve(clientId, client)(contexts)
      mockPurposeRetrieve(purposeId, purpose)(contexts)
      mockEServiceRetrieve(eServiceId, eService)(contexts)

      Get() ~> service.getClient(clientId.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
}
