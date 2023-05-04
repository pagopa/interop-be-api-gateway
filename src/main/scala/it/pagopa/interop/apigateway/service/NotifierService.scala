package it.pagopa.interop.apigateway.service

import it.pagopa.interop.notifier.client.model.Events

import scala.concurrent.Future

trait NotifierService {
  def getEvents(lastEventId: Long, limit: Int)(implicit contexts: Seq[(String, String)]): Future[Events]
  def getAllOrganizationEvents(lastEventId: Long, limit: Int)(implicit contexts: Seq[(String, String)]): Future[Events]

  def getKeysEvents(lastEventId: Long, limit: Int)(implicit contexts: Seq[(String, String)]): Future[Events]
}
