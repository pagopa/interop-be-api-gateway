package it.pagopa.interop.apigateway.common

import com.typesafe.config.{Config, ConfigFactory}
import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {

  val config: Config = ConfigFactory.load()

  val serverPort: Int = config.getInt("interop-api-gateway.port")

  val agreementManagementURL: String         = config.getString("services.agreement-management")
  val authorizationManagementURL: String     = config.getString("services.authorization-management")
  val catalogManagementURL: String           = config.getString("services.catalog-management")
  val partyManagementURL: String             = config.getString("services.party-management")
  val attributeRegistryManagementURL: String = config.getString("services.attribute-registry-management")
  val purposeManagementURL: String           = config.getString("services.purpose-management")

  val interopAudience: Set[String] =
    config.getString("interop-api-gateway.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  require(interopAudience.nonEmpty, "Audience cannot be empty")
}
