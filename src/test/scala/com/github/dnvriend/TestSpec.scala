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

import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.event.{ Logging, LoggingAdapter }
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait TestSpec extends FlatSpec with Matchers with ScalaFutures {
  implicit val system: ActorSystem = ActorSystem()
  implicit val logger: LoggingAdapter = Logging(system, this.getClass)
  implicit val mat = ActorMaterializer()
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 1.minute)

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }
  /**
   * Sends the PoisonPill command to an actor and waits for it to die
   */
  def cleanup(actors: ActorRef*): Unit = {
    val tp = TestProbe()
    actors.foreach { (actor: ActorRef) ⇒
      tp watch actor
      actor ! PoisonPill
      tp.expectTerminated(actor)
    }
  }

}
