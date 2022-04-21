package it.pagopa.interop.apigateway.service

import it.pagopa.interop.notifier.client.model.Events

import scala.concurrent.Future

trait NotifierService {
  def getEvents(lastEventId: Long, limit: Int)(contexts: Seq[(String, String)]): Future[Events]
}
