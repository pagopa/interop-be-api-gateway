package it.pagopa.interop.apigateway.service

import it.pagopa.interop.purposeprocess.client.model.{Purpose, Purposes}

import java.util.UUID
import scala.concurrent.Future

trait PurposeProcessService {

  def getPurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose]

  def getPurposes(eserviceId: UUID, consumerId: UUID)(implicit contexts: Seq[(String, String)]): Future[Purposes]
}
