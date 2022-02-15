package it.pagopa.interop.api.gateway.service.impl

import it.pagopa.interop.api.gateway.service.{PurposeManagementService, PurposeManagementInvoker}
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.api.PurposeApi
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.invoker.BearerToken
import java.util.UUID
import cats.implicits._
import scala.concurrent.Future
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.model.{Purpose, Purposes}
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.invoker.ApiRequest
import org.slf4j.{Logger, LoggerFactory}

class PurposeManagementServiceImpl(invoker: PurposeManagementInvoker, api: PurposeApi)
    extends PurposeManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getPurpose(purposeId: UUID)(bearerToken: String): Future[Purpose] = {
    val request: ApiRequest[Purpose] = api.getPurpose(purposeId)(BearerToken(bearerToken))
    invoker.invoke(request, "Invoking getPurpose")
  }

  override def getPurposes(eserviceId: UUID, consumerId: UUID)(bearerToken: String): Future[Purposes] = {
    val request: ApiRequest[Purposes] = api.getPurposes(eserviceId.some, consumerId.some, Nil)(BearerToken(bearerToken))
    invoker.invoke(request, "Invoking getPurposes")
  }

}
