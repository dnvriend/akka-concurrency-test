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

package com.github.dnvriend

import akka.actor.ActorSystem
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object Test7 extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("system", ConfigFactory.parseString(
    """
      | akka {
      |      actor {
      |        default-dispatcher = {
      |          fork-join-executor {
      |            parallelism-min = 1
      |            parallelism-factor = 1
      |            parallelism-max = 1
      |          }
      |        }
      |      }
      |    }
    """.stripMargin
  ))

  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(1 seconds)

  startServer(interface = "localhost", port = 8080) {
    pathPrefix("hello") {
      pathEnd {
        get {
          complete("Got a get")
        } ~
          post {
            complete("Got a post")
          } ~
          put {
            complete("Got a put")
          } ~
          delete {
            complete("Got a delete")
          }
      } ~
        path(Segment) { name ⇒
          complete(s"Hello: $name")
        }
    }
  }
}
