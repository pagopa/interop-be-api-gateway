package it.pagopa.interop.apigateway.service

import it.pagopa.interop.notifier.client.model.Messages

import scala.concurrent.Future

trait NotifierService {
  def getEvents(lastEventId: String, limit: Int)(contexts: Seq[(String, String)]): Future[Messages]
}
