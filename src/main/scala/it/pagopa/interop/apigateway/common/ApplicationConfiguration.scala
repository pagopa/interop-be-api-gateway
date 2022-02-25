package it.pagopa.interop.apigateway.common

import com.typesafe.config.{Config, ConfigFactory}
import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {

  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = config.getInt("interop-api-gateway.port")

  def agreementManagementURL: String         = config.getString("services.agreement-management")
  def authorizationManagementURL: String     = config.getString("services.authorization-management")
  def catalogManagementURL: String           = config.getString("services.catalog-management")
  def partyManagementURL: String             = config.getString("services.party-management")
  def attributeRegistryManagementURL: String = config.getString("services.attribute-registry-management")
  def purposeManagementURL: String           = config.getString("services.purpose-management")

  def interopIdIssuer: String = config.getString("interop-api-gateway.issuer")

  def rsaPrivatePath: String = config.getString("interop-api-gateway.rsa-private-path")

  lazy val interopAudience: Set[String] = config.getStringList("interop-api-gateway.jwt.audience").asScala.toSet
  lazy val interopTokenDuration: Int    = config.getInt("interop-api-gateway.jwt.duration-seconds")
}
