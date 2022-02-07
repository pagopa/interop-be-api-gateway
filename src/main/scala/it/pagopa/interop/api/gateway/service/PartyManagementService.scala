package it.pagopa.interop.api.gateway.service

import it.pagopa.interop.be.gateway.model.{Subscriber => ApiOrganization}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model._
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.StringOps

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

trait PartyManagementService {

  def getOrganization(organizationId: UUID)(bearerToken: String): Future[Organization]
  def getRelationships(organizationId: UUID, personId: UUID, productRole: String)(
    bearerToken: String
  ): Future[Relationships]
  def getRelationshipsByPersonId(personId: UUID, productRole: Seq[String])(bearerToken: String): Future[Relationships]
  def getRelationshipById(relationshipId: UUID)(bearerToken: String): Future[Relationship]
}

object PartyManagementService {

  final val PRODUCT_ROLE_SECURITY_OPERATOR = "security"

  def organizationToApi(organization: Organization): Try[ApiOrganization] =
    organization.institutionId.toUUID.map { institutionId =>
      ApiOrganization(institutionId, organization.description, organization.attributes.headOption.getOrElse("UNKNOWN"))
    }

}
