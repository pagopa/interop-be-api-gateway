package it.pagopa.interop.apigateway.service

import it.pagopa.interop.purposeprocess.client.model.{Purpose, Purposes}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait PurposeProcessService {

  def getAllPurposes(eserviceId: UUID, consumerId: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[Seq[Purpose]] = {

    def getPurposesFrom(offset: Int): Future[Seq[Purpose]] =
      getPurposes(eserviceId = eserviceId, consumerId = consumerId, offset = offset, limit = 50).map(_.results)

    def go(start: Int)(as: Seq[Purpose]): Future[Seq[Purpose]] =
      getPurposesFrom(start).flatMap(purp =>
        if (purp.size < 50) Future.successful(as ++ purp) else go(start + 50)(as ++ purp)
      )

    go(0)(Nil)
  }

  def getPurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose]

  def getPurposes(eserviceId: UUID, consumerId: UUID, offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Purposes]
}
