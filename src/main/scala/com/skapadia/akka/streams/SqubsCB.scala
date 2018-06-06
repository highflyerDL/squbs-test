package com.skapadia.akka.streams


import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState
import org.squbs.streams.circuitbreaker.{CircuitBreaker, CircuitBreakerSettings}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Success


object SqubsCB {

  /**
    * A stream of 100 integers
    * CB trips every 2 elements. Stream should wait until CB is closed then continue
   **/
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("streamapp")
    implicit val materializer = ActorMaterializer()
    implicit val scheduler = system.scheduler
    val state = AtomicCircuitBreakerState("sample", 1, 1.2 second, 5 second)
    val settings = CircuitBreakerSettings[Int, Int, UUID](state)
      .withFailureDecider(tryResult => tryResult.get % 2 == 0)
      .withFallback((elem: Int) => Success(2))
    val circuitBreaker = CircuitBreaker(settings)

    val source: Source[Int, NotUsed] = Source(1 to 100)
    val process = Flow[(Int, UUID)].mapAsync(1) { elem =>
      Future {
        Thread.sleep(1000)
        elem
      }
    }

    source
      .map(s => (s, UUID.randomUUID()))
      .via(circuitBreaker.join(process))
      .runWith(Sink.foreach(println))

  }
}
