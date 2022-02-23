package it.pagopa.interop.apigateway.service

import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{Organization => PartyManagementApiOrganization}

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {
  def getOrganization(organizationId: UUID)(bearerToken: String): Future[PartyManagementApiOrganization]
}
