package com.github.dnvriend.akka.ex.test

import java.util.concurrent.{ThreadFactory, ForkJoinPool, Executors}

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.http.StatusCodes
import spray.routing.SimpleRoutingApp
import scalikejdbc._

import scala.actors.threadpool.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
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
    """.stripMargin))

  import spray.httpx.SprayJsonSupport
  import spray.httpx.marshalling.MetaMarshallers
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
      path(Segment) { name =>
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
  ConnectionPool.singleton("jdbc:postgresql://192.168.99.99:5432/docker", "docker", "docker")
  implicit val session = AutoSession

  case class Save(msg: String)

  def props = Props(new JdbcActor)

  def writeToDb(msg: String) = Future { SQL("INSERT INTO messages values (?)").bind(msg).update().apply() }

  def selectAll = Future { SQL("SELECT * FROM messages").update.apply }

  def numRecords = SQL("SELECT COUNT(*) FROM messages").map(rs => rs.string(1)).single().apply()
}

class JdbcActor extends Actor with ActorLogging {
  import com.github.dnvriend.akka.ex.test.JdbcActor._
  override def receive: Receive = {
    case Save(msg) =>
      writeToDb(msg)
      selectAll
      sender ! numRecords.get
  }
}
