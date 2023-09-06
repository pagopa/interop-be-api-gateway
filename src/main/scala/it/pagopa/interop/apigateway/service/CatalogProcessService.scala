package it.pagopa.interop.apigateway.service
import it.pagopa.interop.catalogprocess.client.model.{AgreementState, EService, EServiceDescriptorState, EServices}

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

trait CatalogProcessService {

  def getAllEServices(producerId: UUID, attributeId: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[Seq[EService]] = {

    def getEServicesFrom(offset: Int): Future[Seq[EService]] =
      getEServices(
        eServicesIds = Seq.empty,
        producersIds = Seq(producerId),
        attributesIds = Seq(attributeId),
        agreementStates = Seq.empty,
        states = Seq.empty,
        offset = offset,
        limit = 50
      ).map(_.results)

    def go(start: Int)(as: Seq[EService]): Future[Seq[EService]] =
      getEServicesFrom(start).flatMap(eser =>
        if (eser.size < 50) Future.successful(as ++ eser) else go(start + 50)(as ++ eser)
      )

    go(0)(Nil)
  }

  def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]

  def getEServices(
    name: Option[String] = None,
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    attributesIds: Seq[UUID],
    agreementStates: Seq[AgreementState],
    states: Seq[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[EServices]
}
