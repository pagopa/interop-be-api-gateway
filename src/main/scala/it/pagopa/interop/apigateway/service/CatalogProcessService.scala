package it.pagopa.interop.apigateway.service
import it.pagopa.interop.catalogprocess.client.model.{EService, EServices}

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

trait CatalogProcessService {

  def getAllEServices(producerId: UUID, attributeId: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[Seq[EService]] = {

    def getEServicesFrom(offset: Int): Future[Seq[EService]] =
      getEServices(producerIds = Seq(producerId), attributeIds = Seq(attributeId), offset = offset, limit = 50)
        .map(_.results)

    def go(start: Int)(as: Seq[EService]): Future[Seq[EService]] =
      getEServicesFrom(start).flatMap(eser =>
        if (eser.size < 50) Future.successful(as ++ eser) else go(start + 50)(as ++ eser)
      )

    go(0)(Nil)
  }

  def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]

  def getEServices(producerIds: Seq[UUID], attributeIds: Seq[UUID], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServices]
}
