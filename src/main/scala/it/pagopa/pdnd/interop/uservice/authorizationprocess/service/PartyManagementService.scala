package it.pagopa.pdnd.interop.uservice.authorizationprocess.service

import it.pagopa.pdnd.interop.uservice.authorizationprocess.model.{
  Organization => ApiOrganization,
  OperatorState => ApiOperatorState,
  OperatorRole => ApiOperatorRole,
  RelationshipProduct => ApiOperatorRelationshipProduct
}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model._

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {

  def getOrganization(organizationId: UUID)(bearerToken: String): Future[Organization]
  def getRelationships(organizationId: UUID, personId: UUID, productRole: String)(
    bearerToken: String
  ): Future[Relationships]
  def getRelationshipsByPersonId(personId: UUID, productRole: Seq[String])(bearerToken: String): Future[Relationships]
  def getRelationshipById(relationshipId: UUID)(bearerToken: String): Future[Relationship]
}

object PartyManagementService {

  final val ROLE_SECURITY_OPERATOR = "security"

  def organizationToApi(organization: Organization): ApiOrganization =
    ApiOrganization(organization.institutionId, organization.description)

  def relationshipStateToApi(state: RelationshipState): Either[Throwable, ApiOperatorState] =
    state match {
      case RelationshipState.ACTIVE    => Right(ApiOperatorState.ACTIVE)
      case RelationshipState.SUSPENDED => Right(ApiOperatorState.SUSPENDED)
      case RelationshipState.DELETED   => Right(ApiOperatorState.DELETED)
      case RelationshipState.PENDING =>
        Left(new RuntimeException(s"State ${RelationshipState.PENDING.toString} not allowed for security operator"))
      case RelationshipState.REJECTED =>
        Left(new RuntimeException(s"State ${RelationshipState.REJECTED.toString} not allowed for security operator"))
    }

  def relationshipRoleToApi(role: PartyRole): ApiOperatorRole =
    role match {
      case PartyRole.MANAGER      => ApiOperatorRole.MANAGER
      case PartyRole.DELEGATE     => ApiOperatorRole.DELEGATE
      case PartyRole.OPERATOR     => ApiOperatorRole.OPERATOR
      case PartyRole.SUB_DELEGATE => ApiOperatorRole.SUB_DELEGATE
    }

  def relationshipProductToApi(product: RelationshipProduct): ApiOperatorRelationshipProduct =
    ApiOperatorRelationshipProduct(id = product.id, role = product.role, createdAt = product.createdAt)

}
