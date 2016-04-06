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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ Executors, ForkJoinPool, ThreadFactory }

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scalikejdbc._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

object Test12 extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("system", ConfigFactory.parseString(
    """
      | akka {
      |     loglevel = "error"
      |      actor {
      |        default-dispatcher = {
      |          fork-join-executor {
      |            parallelism-min = 8
      |            parallelism-factor = 3
      |            parallelism-max = 64
      |          }
      |        }
      |      }
      |    }
    """.stripMargin
  ))

  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(1 seconds)

  val jdbcActor = system.actorOf(JdbcActor.props, "JdbcActor")

  startServer(interface = "localhost", port = 8080) {
    pathPrefix("hello") {
      pathEnd {
        get {
          complete {
            (jdbcActor ? JdbcActor.Save("Got a get")).mapTo[String]
          }
        } ~
          post {
            complete {
              jdbcActor ! JdbcActor.Save("Got a post")
              StatusCodes.OK
            }
          } ~
          put {
            complete {
              jdbcActor ! JdbcActor.Save("Got a put")
              StatusCodes.OK
            }
          } ~
          delete {
            complete {
              jdbcActor ! JdbcActor.Save("Got a delete")
              StatusCodes.OK
            }
          }
      } ~
        path(Segment) { name ⇒
          complete {
            jdbcActor ! JdbcActor.Save(name)
            StatusCodes.OK
          }
        }
    }
  }
}

class MyThreadFactory extends ThreadFactory {
  val poolNumber = new AtomicInteger(0)
  override def newThread(r: Runnable): Thread = {
    new Thread(r, "MyThread: " + poolNumber.addAndGet(1))
  }
}

object JdbcActor {
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(new MyThreadFactory))
  Class.forName("org.postgresql.Driver")
  ConnectionPool.singleton("jdbc:postgresql://boot2docker:5432/docker", "docker", "docker")
  implicit val session = AutoSession

  case class Save(msg: String)

  def props = Props(new JdbcActor)

  def writeToDb(msg: String) = Future { SQL("INSERT INTO messages values (?)").bind(msg).update().apply() }

  def selectAll = Future { SQL("SELECT * FROM messages").update.apply }

  def numRecords = SQL("SELECT COUNT(*) FROM messages").map(rs ⇒ rs.string(1)).single().apply()
}

class JdbcActor extends Actor with ActorLogging {
  import com.github.dnvriend.akka.ex.test.JdbcActor._
  override def receive: Receive = {
    case Save(msg) ⇒
      writeToDb(msg)
      selectAll
      sender ! numRecords.get
  }
}
