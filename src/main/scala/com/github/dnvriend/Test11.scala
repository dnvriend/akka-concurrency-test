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

import akka.actor.{ ActorLogging, _ }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scalikejdbc._

import scala.concurrent.duration._

object Test11 extends App with SimpleRoutingApp {
  object JdbcActor {
    Class.forName("org.postgresql.Driver")
    ConnectionPool.singleton("jdbc:postgresql://boot2docker:5432/docker", "docker", "docker")
    implicit val session = AutoSession

    case class Save(msg: String)

    def props = Props(new JdbcActor)

    def writeToDb(msg: String) = SQL("INSERT INTO messages values (?)").bind(msg).update().apply()

    def numRecords = SQL("SELECT COUNT(*) FROM messages").map(rs ⇒ rs.string(1)).single().apply()
  }

  class JdbcActor extends Actor with ActorLogging {
    import JdbcActor._
    override def receive: Receive = {
      case Save(msg) ⇒
        writeToDb(msg)
        sender ! numRecords.get
    }
  }

  implicit val system = ActorSystem("system", ConfigFactory.parseString(
    """
      | akka {
      |     loglevel = "error"
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
