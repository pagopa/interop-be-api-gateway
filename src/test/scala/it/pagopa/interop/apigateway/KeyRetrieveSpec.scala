package it.pagopa.interop.apigateway

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.apigateway.api.impl._
import it.pagopa.interop.apigateway.utils.SpecHelper
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, USER_ROLES}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class KeyRetrieveSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Key retrieve" should {
    "fail if the requester does not have M2M role" in {
      val requesterOrganizationId = UUID.randomUUID()
      val kid                     = randomString()

      implicit val contexts: Seq[(String, String)] =
        Seq(USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> requesterOrganizationId.toString)

      Get() ~> service.getJWKByKid(kid) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }
}
