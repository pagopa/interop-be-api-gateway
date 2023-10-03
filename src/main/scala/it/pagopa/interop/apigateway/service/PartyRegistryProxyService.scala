package it.pagopa.interop.apigateway.service

import it.pagopa.interop.partyregistryproxy.client.model.Institution

import scala.concurrent.Future

trait PartyRegistryProxyService {
  def getInstitutionByExternalId(origin: String, originId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Institution]

  def getAOOByExternalId(origin: String, originId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Institution]

  def getUOByExternalId(origin: String, originId: String)(implicit contexts: Seq[(String, String)]): Future[Institution]
}
