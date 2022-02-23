package it.pagopa.interop.apigateway.service

import it.pagopa.pdnd.interop.uservice.purposemanagement.client.model.{Purpose, Purposes}

import java.util.UUID
import scala.concurrent.Future

trait PurposeManagementService {

  def getPurpose(purposeId: UUID)(bearerToken: String): Future[Purpose]

  def getPurposes(eserviceId: UUID, consumerId: UUID)(bearerToken: String): Future[Purposes]
}
