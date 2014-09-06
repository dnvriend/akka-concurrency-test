package com.github.dnvriend.akka.ex.test

import scala.concurrent.duration._

object Test1 extends App {

  def calc(name: String, max: Int = 0) = (1 to max)
    .map(_*2)
    .map{ e =>
    Thread.sleep((1 second).toMillis)
      val calc = e + 2
      println(name + ": " + calc)
      calc
    }

  calc("fut2", 10)

  Thread.sleep((5 seconds).toMillis)
}
