/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.frontend.filters

import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import play.api.http.HeaderNames
import play.api.mvc.{Cookie, RequestHeader, Result, Results, Cookies}
import play.api.test.{FakeApplication, FakeRequest, WithApplication}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.filters.frontend.{DeviceIdCookie, DeviceId}

import scala.concurrent.Future

class DeviceIdCookieFilterSpec extends WordSpecLike with Matchers with MockitoSugar with ScalaFutures {

  final val theSecret = "some_secret"

  lazy val createDeviceId = new DeviceIdCookie {
    override val secret = theSecret
  }

  val appConfig = Map("cookie.deviceId.secret" -> theSecret)

  val auditConnector = mock[AuditConnector]

  trait Setup {
    val action = {
      val mockAction = mock[(RequestHeader) => Future[Result]]
      val outgoingResponse = Future.successful(Results.Ok)
      when(mockAction.apply(any())).thenReturn(outgoingResponse)
      mockAction
    }

    def requestPassedToAction: RequestHeader = {
      val updatedRequest = ArgumentCaptor.forClass(classOf[RequestHeader])
      verify(action).apply(updatedRequest.capture())
      updatedRequest.getValue
    }
  }

  "DeviceIdFilter" should {

    "create the deviceId when no cookie exists" in new WithApplication(FakeApplication(additionalConfiguration = appConfig)) with Setup {

      val incomingRequest = FakeRequest()
      val response = DeviceIdCookieFilter("someapp",auditConnector)(action)(incomingRequest).futureValue

      val deviceIdRequestCookie: Cookie = requestPassedToAction.cookies(DeviceId.MdtpDeviceId)

      val responseDeviceIdCookie = Cookies.decode(response.header.headers(HeaderNames.SET_COOKIE))(0).value
      responseDeviceIdCookie shouldBe deviceIdRequestCookie.value
    }

    "do nothing when a valid cookie exists" in new WithApplication(FakeApplication(additionalConfiguration = appConfig)) with Setup {

      val deviceId = createDeviceId.buildNewDeviceIdCookie()
      val incomingRequest = FakeRequest().withCookies(deviceId)

      val response = DeviceIdCookieFilter("someapp",auditConnector)(action)(incomingRequest).futureValue

      val deviceIdRequestCookie: Cookie = requestPassedToAction.cookies(DeviceId.MdtpDeviceId)

      deviceIdRequestCookie.value shouldBe deviceId.value
      response.header.headers shouldBe Map.empty
    }

  }

}