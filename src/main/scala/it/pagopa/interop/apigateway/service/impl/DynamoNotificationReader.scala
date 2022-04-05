package it.pagopa.interop.apigateway.service.impl

import org.scanamo.DynamoResultStream.ScanResponseStream.items
import org.scanamo._
import org.scanamo.generic.auto._
import org.scanamo.query.{KeyEquals, UniqueKey}
import org.scanamo.syntax._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.util
import scala.concurrent.{ExecutionContext, Future}

final case class Test(key: String)

class DynamoNotificationReader(val dynamoAsyncClient: DynamoDbAsyncClient)(implicit ec: ExecutionContext) {
  val scanamo       = ScanamoAsync(dynamoAsyncClient)
  val notifications = Table[Test]("notifications")

  def getNotificationsFromStart(limit: Integer) = {

    val x = notifications.from(UniqueKey(KeyEquals("name", "Maggot"))).limit(limit)
    val result: Future[(util.Map[FieldName, AttributeValue], List[Either[DynamoReadError, Test]])] = scanamo.exec {
      for {
        tubesStartingWithC <- x.scanRaw
        y       = tubesStartingWithC.lastEvaluatedKey()
        results = items(tubesStartingWithC).map(ScanamoFree.read[Test])
      } yield (y, results)
    }
  }

}
