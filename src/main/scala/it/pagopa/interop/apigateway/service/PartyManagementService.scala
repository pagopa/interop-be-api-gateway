package it.pagopa.interop.apigateway.service

import it.pagopa.interop.partymanagement.client.model.{Institution => PartyManagementApiInstitution}

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {
  def getInstitution(institutionId: UUID)(bearerToken: String): Future[PartyManagementApiInstitution]
}
