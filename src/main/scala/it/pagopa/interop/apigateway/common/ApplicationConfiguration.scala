package it.pagopa.interop.apigateway.common

import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.commons.cqrs.model.ReadModelConfig
import it.pagopa.interop.commons.ratelimiter.model.LimiterConfig

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object ApplicationConfiguration {

  val config: Config = ConfigFactory.load()

  val serverPort: Int = config.getInt("interop-api-gateway.port")

  val agreementManagementURL: String         = config.getString("interop-api-gateway.services.agreement-management")
  val authorizationManagementURL: String     = config.getString("interop-api-gateway.services.authorization-management")
  val catalogManagementURL: String           = config.getString("interop-api-gateway.services.catalog-management")
  val attributeRegistryManagementURL: String =
    config.getString("interop-api-gateway.services.attribute-registry-management")
  val partyRegistryProxyURL: String          = config.getString("interop-api-gateway.services.party-registry-proxy")
  val purposeManagementURL: String           = config.getString("interop-api-gateway.services.purpose-management")
  val tenantProcessURL: String               = config.getString("interop-api-gateway.services.tenant-process")
  val tenantManagementURL: String            = config.getString("interop-api-gateway.services.tenant-management")

  val notifierURL: String = config.getString("interop-api-gateway.services.notifier")

  val jwtAudience: Set[String] =
    config.getString("interop-api-gateway.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val rateLimiterConfigs: LimiterConfig = {
    val rateInterval = config.getDuration("interop-api-gateway.rate-limiter.rate-interval")
    val timeout      = config.getDuration("interop-api-gateway.rate-limiter.timeout")

    LimiterConfig(
      limiterGroup = config.getString("interop-api-gateway.rate-limiter.limiter-group"),
      maxRequests = config.getInt("interop-api-gateway.rate-limiter.max-requests"),
      burstPercentage = config.getDouble("interop-api-gateway.rate-limiter.burst-percentage"),
      rateInterval = FiniteDuration(rateInterval.toMillis, TimeUnit.MILLISECONDS),
      redisHost = config.getString("interop-api-gateway.rate-limiter.redis-host"),
      redisPort = config.getInt("interop-api-gateway.rate-limiter.redis-port"),
      timeout = FiniteDuration(timeout.toMillis, TimeUnit.MILLISECONDS)
    )
  }

  val readModelConfig: ReadModelConfig = {
    val connectionString: String = config.getString("api-gateway.read-model.db.connection-string")
    val dbName: String           = config.getString("api-gateway.read-model.db.name")

    ReadModelConfig(connectionString, dbName)
  }

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
}
