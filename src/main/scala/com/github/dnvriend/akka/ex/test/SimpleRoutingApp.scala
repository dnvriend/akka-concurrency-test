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

package com.github.dnvriend.akka.ex.test

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.Directives
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.scaladsl.Flow

trait SimpleRoutingApp extends App with Directives {
  implicit def system: ActorSystem
  implicit lazy val mat: Materializer = ActorMaterializer()
  def startServer(interface: String, port: Int)(handler: Flow[HttpRequest, HttpResponse, Any]) =
    Http().bindAndHandle(handler, interface, port)
}
