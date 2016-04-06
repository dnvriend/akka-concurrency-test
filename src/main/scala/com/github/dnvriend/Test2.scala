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

import java.util.concurrent.Executors
import scala.concurrent._
import scala.concurrent.duration._

object Test2 extends App {

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(1))

  def calc(name: String, max: Int = 0) = (1 to max)
    .map(_ * 2)
    .map { e ⇒
      Thread.sleep((1 second).toMillis)
      val calc = e + 2
      println(name + ": " + calc)
      calc
    }

  val f1 = Future {
    calc("fut1", 5)
  }

  val f2 = Future {
    calc("fut2", 10)
  }

  Await.ready(Future.sequence(List(f1, f2)), 5 minutes)
}
