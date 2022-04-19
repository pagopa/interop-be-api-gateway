package it.pagopa.interop.apigateway

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.apigateway.utils.SpecHelper
import org.scalatest.wordspec.AnyWordSpecLike
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.apigateway.api.impl._
import it.pagopa.interop.apigateway.model.Client
import it.pagopa.interop.commons.utils.ORGANIZATION_ID_CLAIM
import org.scalatest.matchers.should.Matchers._

import java.util.UUID

class ClientRetrieveSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Client retrieve" should {
    "succeed if the requester is the consumer" in {
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

      implicit val contexts: Seq[(String, String)] = Seq(ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString)

      mockClientRetrieve(clientId, client)(contexts)

      Get() ~> service.getClient(clientId.toString) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Client] shouldEqual expectedClient
      }

    }

    "succeed if the requester is the producer" in {}

    "fail if the requester is neither the consumer or the producer" in {}
  }
}
