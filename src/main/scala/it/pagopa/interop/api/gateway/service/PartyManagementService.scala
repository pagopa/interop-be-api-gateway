package it.pagopa.interop.api.gateway.service

import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{
  Organization => PartyManagementApiOrganization,
  Relationship => PartyManagementApiRelationship,
  Relationships => PartyManagementApiRelationships
}
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.StringOps

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try
import it.pagopa.interop.be.gateway.model.Organization

trait PartyManagementService {

  def getOrganization(organizationId: UUID)(bearerToken: String): Future[PartyManagementApiOrganization]
  def getRelationships(organizationId: UUID, personId: UUID, productRole: String)(
    bearerToken: String
  ): Future[PartyManagementApiRelationships]
  def getRelationshipsByPersonId(personId: UUID, productRole: Seq[String])(
    bearerToken: String
  ): Future[PartyManagementApiRelationships]
  def getRelationshipById(relationshipId: UUID)(bearerToken: String): Future[PartyManagementApiRelationship]
}

object PartyManagementService {

  final val PRODUCT_ROLE_SECURITY_OPERATOR = "security"

  def organizationToApi(organization: PartyManagementApiOrganization): Try[Organization] =
    organization.institutionId.toUUID.map { institutionId =>
      Organization(institutionId, organization.description, organization.attributes.headOption.getOrElse("UNKNOWN"))
    }

}
