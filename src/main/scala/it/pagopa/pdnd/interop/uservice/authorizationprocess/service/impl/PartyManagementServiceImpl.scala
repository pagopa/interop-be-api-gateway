package it.pagopa.pdnd.interop.uservice.authorizationprocess.service.impl

import it.pagopa.pdnd.interop.uservice.authorizationprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import it.pagopa.pdnd.interop.uservice.partymanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model._
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi) extends PartyManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getOrganization(organizationId: UUID)(bearerToken: String): Future[Organization] = {
    val request: ApiRequest[Organization] = api.getOrganizationById(organizationId)(BearerToken(bearerToken))
    invoker.invoke(request, "Retrieve Organization")
  }

  override def getRelationships(organizationId: UUID, personId: UUID, productRole: String)(
    bearerToken: String
  ): Future[Relationships] = {
    val request: ApiRequest[Relationships] =
      api.getRelationships(
        from = Some(personId),
        to = Some(organizationId),
        roles = Seq.empty,
        states = Seq.empty,
        products = Seq.empty,
        productRoles = Seq(productRole)
      )(BearerToken(bearerToken))
    invoker.invoke(request, "Retrieve Relationships")
  }

  override def getRelationshipsByPersonId(personId: UUID, productRoles: Seq[String])(
    bearerToken: String
  ): Future[Relationships] = {
    val request: ApiRequest[Relationships] =
      api.getRelationships(
        from = Some(personId),
        to = None,
        roles = Seq.empty,
        states = Seq.empty,
        products = Seq.empty,
        productRoles = productRoles
      )(BearerToken(bearerToken))
    invoker.invoke(request, "Retrieve Relationships By Person Id")
  }

  override def getRelationshipById(relationshipId: UUID)(bearerToken: String): Future[Relationship] = {
    val request: ApiRequest[Relationship] = api.getRelationshipById(relationshipId)(BearerToken(bearerToken))
    invoker.invoke(request, "Retrieve Relationship By Id")
  }

}
