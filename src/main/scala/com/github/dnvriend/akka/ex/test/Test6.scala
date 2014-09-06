package com.github.dnvriend.akka.ex.test

import java.util.concurrent.Executors
import scala.concurrent._
import scala.concurrent.duration._

object Test6 extends App {

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))

  def calc = Future {
    (1 to 20)
      .map(_ * 2)
      .map { e =>
      Thread.sleep((1 second).toMillis)
      val calc = e + 2
      println(Thread.currentThread().getName + ": " + calc)
      calc
    }
  }

  Await.ready(Future.sequence((1 to 10).map(_ => calc).toList), 5 minutes)
}
