package it.pagopa.interop.apigateway.service

import it.pagopa.interop.attributeregistryprocess.client.model.{Attribute, Attributes, CertifiedAttributeSeed}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait AttributeRegistryProcessService {

  def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Attribute]

  def getAllBulkAttributes(
    attributeIds: Set[UUID]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Attribute]] = {

    def getAttributesFrom(offset: Int): Future[Seq[Attribute]] =
      getBulkAttributes(attributeIds = attributeIds, offset = offset, limit = 50).map(_.results)

    def go(start: Int)(as: Seq[Attribute]): Future[Seq[Attribute]] =
      getAttributesFrom(start).flatMap(attrs =>
        if (attrs.size < 50) Future.successful(as ++ attrs) else go(start + 50)(as ++ attrs)
      )

    go(0)(Nil)
  }

  def getBulkAttributes(attributeIds: Set[UUID], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attributes]

  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute]

  def createCertifiedAttribute(attributeSeed: CertifiedAttributeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute]

}
