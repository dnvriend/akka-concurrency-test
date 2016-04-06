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

import akka.actor.{ Actor, ActorRef, Props }
import akka.event.LoggingReceive
import akka.testkit.TestProbe
import com.github.dnvriend.TestSpec

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class CustomActorDispatcher extends TestSpec {

  class TestActor(implicit ec: ExecutionContext) extends Actor {

    def sleep: Unit = Thread.sleep(1.second.toMillis)

    def replyTo(dispatcherThreadName: String, replyTo: ActorRef): Unit = for {
      blockingIOThreadName ← Future {
        Thread.sleep(1.second.toMillis)
        Thread.currentThread().getName
      }
    } yield replyTo ! s"$dispatcherThreadName,$blockingIOThreadName"

    override def receive: Receive = LoggingReceive {
      case _ ⇒ replyTo(Thread.currentThread().getName, sender())
    }
  }

  def withTestActor(dispatcherId: String)(f: (ActorRef, TestProbe) ⇒ Unit): Unit = {
    implicit val blockingIOExecutionContext = system.dispatchers.lookup("dispatchers." + dispatcherId)
    val actor = system.actorOf(Props(new TestActor()))
    try f(actor, TestProbe()) finally cleanup(actor)
  }

  it should "dispatch a message to TestActor using the default dispatcher and execute in blocking io dispatcher" in withTestActor("blocking-io-dispatcher") { (actor, tp) ⇒
    tp.send(actor, "foo")
    val str = tp.expectMsgType[String]
    str.split(",").map(_.dropRight(2)) should contain("default-dispatchers.blocking-io-dispatcher")
  }

  it should "dispatch a message to TestActor using the default dispatcher and execute in blocking io pinned dispatcher" in withTestActor("blocking-io-pinned-dispatcher") { (actor, tp) ⇒
    tp.send(actor, "foo")
    val str = tp.expectMsgType[String]
    str.split(",").map(_.dropRight(2)) should contain("default-dispatchers.blocking-io-pinned-dispatcher")
  }
}