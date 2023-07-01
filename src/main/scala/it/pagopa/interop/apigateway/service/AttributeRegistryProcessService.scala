package it.pagopa.interop.apigateway.service

import it.pagopa.interop.attributeregistryprocess.client.model.{Attribute, AttributeSeed}

import java.util.UUID
import scala.concurrent.Future

trait AttributeRegistryProcessService {

  def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Attribute]
  def getBulkAttributes(attributeIds: Set[UUID])(implicit contexts: Seq[(String, String)]): Future[Seq[Attribute]]

  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute]

  def createAttribute(attributeSeed: AttributeSeed)(implicit contexts: Seq[(String, String)]): Future[Attribute]

}
