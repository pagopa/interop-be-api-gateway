package it.pagopa.pdnd.interop.uservice.authorizationprocess.service

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

package object impl extends DefaultJsonProtocol with SprayJsonSupport {}
