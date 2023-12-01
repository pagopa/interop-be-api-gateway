package it.pagopa.interop.apigateway

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.apigateway.utils.SpecHelper
import org.scalatest.wordspec.AnyWordSpecLike
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.apigateway.api.impl._
import it.pagopa.interop.apigateway.model.Client
import it.pagopa.interop.commons.jwt.M2M_ROLE
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, USER_ROLES}
import org.scalatest.matchers.should.Matchers._

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
        purposes = Seq.empty,
        relationshipsIds = Set.empty,
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
      val descriptorId            = UUID.randomUUID()
      val agreementId             = UUID.randomUUID()
      val versionId               = UUID.randomUUID()
      val purposeId               = UUID.randomUUID()

      val client: AuthorizationProcess.Client = AuthorizationProcess.Client(
        id = clientId,
        consumerId = consumerId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Seq(
          AuthorizationProcess.ClientPurpose(states =
            AuthorizationProcess.ClientStatesChain(
              id = UUID.randomUUID(),
              eservice = AuthorizationProcess.ClientEServiceDetails(
                eserviceId = eServiceId,
                descriptorId = descriptorId,
                state = AuthorizationProcess.ClientComponentState.ACTIVE,
                audience = Seq("aud"),
                voucherLifespan = 1
              ),
              agreement = AuthorizationProcess.ClientAgreementDetails(
                eserviceId = eServiceId,
                consumerId = consumerId,
                agreementId = agreementId,
                state = AuthorizationProcess.ClientComponentState.ACTIVE
              ),
              purpose = AuthorizationProcess.ClientPurposeDetails(
                purposeId = purposeId,
                versionId = versionId,
                state = AuthorizationProcess.ClientComponentState.ACTIVE
              )
            )
          )
        ),
        relationshipsIds = Set.empty,
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

      val expectedClient: Client = Client(id = client.id, consumerId = client.consumerId)

      implicit val contexts: Seq[(String, String)] =
        Seq(ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString, USER_ROLES -> M2M_ROLE)

      mockClientRetrieve(clientId, client)(contexts)
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
      val descriptorId            = UUID.randomUUID()
      val agreementId             = UUID.randomUUID()
      val versionId               = UUID.randomUUID()
      val purposeId               = UUID.randomUUID()

      val client: AuthorizationProcess.Client = AuthorizationProcess.Client(
        id = clientId,
        consumerId = consumerId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Seq(
          AuthorizationProcess.ClientPurpose(states =
            AuthorizationProcess.ClientStatesChain(
              id = UUID.randomUUID(),
              eservice = AuthorizationProcess.ClientEServiceDetails(
                eserviceId = eServiceId,
                descriptorId = descriptorId,
                state = AuthorizationProcess.ClientComponentState.ACTIVE,
                audience = Seq("aud"),
                voucherLifespan = 1
              ),
              agreement = AuthorizationProcess.ClientAgreementDetails(
                eserviceId = eServiceId,
                consumerId = consumerId,
                agreementId = agreementId,
                state = AuthorizationProcess.ClientComponentState.ACTIVE
              ),
              purpose = AuthorizationProcess.ClientPurposeDetails(
                purposeId = purposeId,
                versionId = versionId,
                state = AuthorizationProcess.ClientComponentState.ACTIVE
              )
            )
          )
        ),
        relationshipsIds = Set.empty,
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

      implicit val contexts: Seq[(String, String)] =
        Seq(USER_ROLES -> "m2m", ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString)

      mockClientRetrieve(clientId, client)(contexts)
      mockEServiceRetrieve(eServiceId, eService)(contexts)

      Get() ~> service.getClient(clientId.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
}
