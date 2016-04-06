/*
 * Copyright 2016 Dennis Vriend
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

package com.github.dnvriend.dispatcher

import com.github.dnvriend.TestSpec

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class CustomDispatcherTest extends TestSpec {

  "blocking-io-dispatcher" should "not be found when wrong dispatcher name is used" in {
    intercept[akka.ConfigurationException] {
      system.dispatchers.lookup("wrong-dispatcher-name")
    }
    intercept[akka.ConfigurationException] {
      system.dispatchers.lookup("blocking-io-dispatcher")
    }
    intercept[akka.ConfigurationException] {
      system.dispatchers.lookup("dispatchers.wrong-dispatcher-name")
    }
  }

  it should "should not be the same as the default dispatcher" in {
    val blockingIOExecutionContext = system.dispatchers.lookup("dispatchers.blocking-io-dispatcher").hashCode()
    val defaultDispatcher = system.dispatcher.hashCode()
    blockingIOExecutionContext shouldNot equal(defaultDispatcher)
  }

  it should "execute futures" in {
    implicit val blockingIOExecutionContext = system.dispatchers.lookup("dispatchers.blocking-io-dispatcher")
    Future(1).toTry should be a 'success
  }

  it should "execute multiple futures" in {
    def sleep(x: Int)(implicit ec: ExecutionContext) = Future(Thread.sleep(500.millis.toMillis))
    implicit val blockingIOExecutionContext = system.dispatchers.lookup("dispatchers.blocking-io-dispatcher")
    Future.sequence((1 to 10).map(sleep)).toTry should be a 'success
  }
}
