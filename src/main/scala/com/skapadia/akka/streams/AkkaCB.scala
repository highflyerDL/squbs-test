package com.skapadia.akka.streams

import akka.NotUsed
import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try


object AkkaCB {

  /**
    * A stream of 100 integers
    * CB trips every 2 elements. Stream should wait until CB is closed then continue
    **/
  def main(args: Array[String]): Unit = {
    val CBresetTimeout = 5.seconds
    val streamResetTimeout = 6.seconds
//    val streamResetTimeout = CBresetTimeout
    implicit val system: ActorSystem = ActorSystem("streamapp")
    implicit val scheduler: Scheduler = system.scheduler

    val breaker =
      new CircuitBreaker(
        system.scheduler,
        maxFailures = 3,
        callTimeout = 3.seconds,
        resetTimeout = CBresetTimeout)
        .onOpen(println(s"CB opened! Will be reset after $CBresetTimeout"))
        .onHalfOpen(println("CB half-opened! Will test now with the next element"))
//        .onCallSuccess(_ => println(s"CB success"))
//        .onCallFailure(_ => println(s"CB failure"))
        .onClose(println("CB closed!"))

    val decider: Supervision.Decider = {
      case _: MerchantCenterException ⇒ println("MC down!"); Supervision.Resume
      case _: CircuitBreakerOpenException ⇒ {
        if (breaker.isOpen) {
          println(s"CB tripped! Will resume stream after $streamResetTimeout")
          Thread.sleep(streamResetTimeout.toMillis)
        }
        Supervision.Restart
      }
      case err: Throwable ⇒ println(err); Supervision.Stop
    }

    implicit val materializer: ActorMaterializer = ActorMaterializer(
      ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    def callExternal(number: Int): Future[Int] = Future {
      Thread.sleep(1500)
      if (number % 10 == 3 || number % 10 == 4 || number % 10 == 5) {
        throw MerchantCenterException("Service down!")
      }
      number
    }

//    def defineFailure(result: Try[Int]): Boolean = {
//      result.isSuccess
//    }


    val source: Source[Int, NotUsed] = Source(1 to 100).map(a => {
      println(s"Init $a"); a
    })
    val process = Flow[Int].mapAsync(2)(elem => breaker.withCircuitBreaker(callExternal(elem)))
//    val processRestart = RestartFlow
//      .withBackoff(10.seconds, 30.seconds, 0.2, 20) {() =>
//      Flow[Int].mapAsync(1)(elem => breaker.withCircuitBreaker(callExternal(elem)))
//    }

    source
      .via(process)
      .runForeach(result => println(s"Finished $result"))

  }

  case class MerchantCenterException(msg: String) extends RuntimeException

}
