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

import java.util.concurrent.Executors
import scala.concurrent._
import scala.concurrent.duration._

object Test6 extends App {

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))

  def calc = Future {
    (1 to 20)
      .map(_ * 2)
      .map { e ⇒
        Thread.sleep((1 second).toMillis)
        val calc = e + 2
        println(Thread.currentThread().getName + ": " + calc)
        calc
      }
  }

  Await.ready(Future.sequence((1 to 10).map(_ ⇒ calc).toList), 5 minutes)
}
