package it.pagopa.interop.apigateway.service

import it.pagopa.interop.selfcare.partymanagement.client.model.Institution

import scala.concurrent.{ExecutionContext, Future}

trait PartyManagementService {
  def getInstitution(
    institutionId: String
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution]
}
