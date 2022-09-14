package it.pagopa.interop.apigateway.common

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {

  val config: Config = ConfigFactory.load()

  val serverPort: Int = config.getInt("interop-api-gateway.port")

  val agreementManagementURL: String         = config.getString("interop-api-gateway.services.agreement-management")
  val authorizationManagementURL: String     = config.getString("interop-api-gateway.services.authorization-management")
  val catalogManagementURL: String           = config.getString("interop-api-gateway.services.catalog-management")
  val partyManagementURL: String             = config.getString("interop-api-gateway.services.party-management")
  val attributeRegistryManagementURL: String =
    config.getString("interop-api-gateway.services.attribute-registry-management")
  val purposeManagementURL: String           = config.getString("interop-api-gateway.services.purpose-management")
  val tenantProcessURL: String               = config.getString("interop-api-gateway.services.tenant-process")
  val tenantManagementURL: String            = config.getString("interop-api-gateway.services.tenant-management")

  val notifierURL: String = config.getString("interop-api-gateway.services.notifier")

  val partyManagementApiKey: String = config.getString("interop-api-gateway.api-keys.party-management")

  val jwtAudience: Set[String] =
    config.getString("interop-api-gateway.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
}
