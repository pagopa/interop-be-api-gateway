package it.pagopa.interop.apigateway.common

import com.typesafe.config.{Config, ConfigFactory}
import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {

  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int = config.getInt("interop-api-gateway.port")

  lazy val agreementManagementURL: String         = config.getString("services.agreement-management")
  lazy val authorizationManagementURL: String     = config.getString("services.authorization-management")
  lazy val catalogManagementURL: String           = config.getString("services.catalog-management")
  lazy val partyManagementURL: String             = config.getString("services.party-management")
  lazy val attributeRegistryManagementURL: String = config.getString("services.attribute-registry-management")
  lazy val purposeManagementURL: String           = config.getString("services.purpose-management")

  lazy val interopAudience: Set[String] = config.getStringList("interop-api-gateway.jwt.audience").asScala.toSet
}
