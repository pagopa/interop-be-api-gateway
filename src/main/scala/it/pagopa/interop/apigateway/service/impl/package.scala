package it.pagopa.interop.apigateway.service

import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.MissingHeader
import it.pagopa.interop.commons.utils.{CORRELATION_ID_HEADER, extractHeaders}

import java.util.UUID
import scala.concurrent.Future

package object impl {

  private[service] def extractHeadersWithOptionalCorrelationId(
    contexts: Seq[(String, String)]
  ): Either[ComponentError, (String, String, Option[String])] =
    extractHeaders(contexts) match {
      case Left(MissingHeader(_)) =>
        extractHeaders(contexts.appended(CORRELATION_ID_HEADER -> UUID.randomUUID().toString))
      case x @ _                  => x
    }

  def extractHeadersWithOptionalCorrelationIdF(
    contexts: Seq[(String, String)]
  ): Future[(String, String, Option[String])] =
    extractHeadersWithOptionalCorrelationId(contexts).toFuture
}
