package it.pagopa.interop.apigateway.service

import it.pagopa.interop.purposemanagement.client.model.{Purpose, Purposes}

import java.util.UUID
import scala.concurrent.Future

trait PurposeManagementService {

  def getPurpose(purposeId: UUID)(contexts: Seq[(String, String)]): Future[Purpose]

  def getPurposes(eserviceId: UUID, consumerId: UUID)(contexts: Seq[(String, String)]): Future[Purposes]
}
