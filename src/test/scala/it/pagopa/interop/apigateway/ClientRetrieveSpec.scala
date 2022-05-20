package it.pagopa.interop.apigateway

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.apigateway.utils.SpecHelper
import org.scalatest.wordspec.AnyWordSpecLike
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.apigateway.api.impl._
import it.pagopa.interop.apigateway.model.Client
import it.pagopa.interop.commons.jwt.M2M_ROLE
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, USER_ROLES}
import org.scalatest.matchers.should.Matchers._

import java.util.UUID

class ClientRetrieveSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Client retrieve" should {
    "succeed if the requester is the consumer of the Client" in {
      val requesterOrganizationId                = UUID.randomUUID()
      val clientId                               = UUID.randomUUID()
      val client: AuthorizationManagement.Client = AuthorizationManagement.Client(
        id = clientId,
        consumerId = requesterOrganizationId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Seq.empty,
        relationships = Set.empty,
        kind = AuthorizationManagement.ClientKind.CONSUMER
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
      val requesterOrganizationId                = UUID.randomUUID()
      val clientId                               = UUID.randomUUID()
      val client: AuthorizationManagement.Client = AuthorizationManagement.Client(
        id = clientId,
        consumerId = requesterOrganizationId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Seq.empty,
        relationships = Set.empty,
        kind = AuthorizationManagement.ClientKind.CONSUMER
      )

      implicit val contexts: Seq[(String, String)] = Seq(ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString)
      mockClientRetrieve(clientId, client)(contexts)

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

      val client: AuthorizationManagement.Client = AuthorizationManagement.Client(
        id = clientId,
        consumerId = consumerId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Seq(
          AuthorizationManagement.Purpose(
            purposeId = purposeId,
            states = AuthorizationManagement.ClientStatesChain(
              id = UUID.randomUUID(),
              eservice = AuthorizationManagement.ClientEServiceDetails(
                eserviceId = eServiceId,
                state = AuthorizationManagement.ClientComponentState.ACTIVE,
                audience = Seq("aud"),
                voucherLifespan = 1
              ),
              agreement = AuthorizationManagement.ClientAgreementDetails(
                eserviceId = eServiceId,
                consumerId = consumerId,
                state = AuthorizationManagement.ClientComponentState.ACTIVE
              ),
              purpose = AuthorizationManagement.ClientPurposeDetails(
                purposeId = purposeId,
                state = AuthorizationManagement.ClientComponentState.ACTIVE
              )
            )
          )
        ),
        relationships = Set.empty,
        kind = AuthorizationManagement.ClientKind.CONSUMER
      )

      val eService: CatalogManagement.EService = CatalogManagement.EService(
        id = eServiceId,
        producerId = requesterOrganizationId,
        name = "EService",
        description = "Description",
        technology = CatalogManagement.EServiceTechnology.REST,
        attributes = CatalogManagement.Attributes(Seq.empty, Seq.empty, Seq.empty),
        descriptors = Seq.empty
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
      val purposeId               = UUID.randomUUID()

      val client: AuthorizationManagement.Client = AuthorizationManagement.Client(
        id = clientId,
        consumerId = consumerId,
        name = "A Client",
        description = Some("A Client Description"),
        purposes = Seq(
          AuthorizationManagement.Purpose(
            purposeId = purposeId,
            states = AuthorizationManagement.ClientStatesChain(
              id = UUID.randomUUID(),
              eservice = AuthorizationManagement.ClientEServiceDetails(
                eserviceId = eServiceId,
                state = AuthorizationManagement.ClientComponentState.ACTIVE,
                audience = Seq("aud"),
                voucherLifespan = 1
              ),
              agreement = AuthorizationManagement.ClientAgreementDetails(
                eserviceId = eServiceId,
                consumerId = consumerId,
                state = AuthorizationManagement.ClientComponentState.ACTIVE
              ),
              purpose = AuthorizationManagement.ClientPurposeDetails(
                purposeId = purposeId,
                state = AuthorizationManagement.ClientComponentState.ACTIVE
              )
            )
          )
        ),
        relationships = Set.empty,
        kind = AuthorizationManagement.ClientKind.CONSUMER
      )

      val eService: CatalogManagement.EService = CatalogManagement.EService(
        id = eServiceId,
        producerId = producerId,
        name = "EService",
        description = "Description",
        technology = CatalogManagement.EServiceTechnology.REST,
        attributes = CatalogManagement.Attributes(Seq.empty, Seq.empty, Seq.empty),
        descriptors = Seq.empty
      )

      implicit val contexts: Seq[(String, String)] = Seq(ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString)

      mockClientRetrieve(clientId, client)(contexts)
      mockEServiceRetrieve(eServiceId, eService)(contexts)

      Get() ~> service.getClient(clientId.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
}
