package com.github.dnvriend.akka.ex.test

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.routing.SimpleRoutingApp
import scala.concurrent.duration._

object Test10 extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("system", ConfigFactory.parseString(
    """
      | akka {
      |      actor {
      |        default-dispatcher = {
      |          fork-join-executor {
      |            parallelism-min = 300
      |            parallelism-factor = 3
      |            parallelism-max = 300
      |          }
      |        }
      |      }
      |    }
    """.stripMargin))

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
      path(Segment) { name =>
        complete(s"Hello: $name")
      }
    }
  }
}
