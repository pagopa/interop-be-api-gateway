package it.pagopa.interop.api.gateway.service

import java.util.UUID
import scala.concurrent.Future
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.model.{Purpose, Purposes}

trait PurposeManagementService {

  def getPurpose(purposeId: UUID)(bearerToken: String): Future[Purpose]

  def getPurposes(eserviceId: UUID, consumerId: UUID)(bearerToken: String): Future[Purposes]
}
