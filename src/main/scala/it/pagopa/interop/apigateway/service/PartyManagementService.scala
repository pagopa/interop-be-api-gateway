package it.pagopa.interop.apigateway.service

import it.pagopa.interop.selfcare.partymanagement.client.model.Institution

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait PartyManagementService {
  def getInstitution(
    institutionId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution]
}
