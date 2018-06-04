package com.skapadia.akka.streams


import java.io.File
import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import org.squbs.streams.circuitbreaker.{CircuitBreaker, CircuitBreakerSettings}
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState

import scala.collection.immutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try

object StreamApp  {

  def lineSink(filename: String): Sink[String, Future[IOResult]] = {
    Flow[String]
      .alsoTo(Sink.foreach(s => println(s"$filename: $s")))
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toFile(new File(filename)))(Keep.right)
  }

  def main(args: Array[String]): Unit = {

    val state = AtomicCircuitBreakerState("sample", 2, 100 milliseconds, 1 second)
    val settings = CircuitBreakerSettings[Int, String, UUID](state)
    val circuitBreaker = CircuitBreaker(settings)

    implicit val system = ActorSystem("streamapp")
    implicit val materializer = ActorMaterializer()

    val source: Source[Int, NotUsed] = Source(1 to 100)
    val doubles: Flow[(Int, UUID), (String, UUID), NotUsed] = Flow[(Int, UUID)].map(tuple => ((tuple._1*tuple._1).toString, tuple._2))
    val sink: Sink[String, Future[IOResult]] = lineSink("factorial.txt")
    val sink2 = lineSink("factorial2.txt")
    val slowSink2 = Flow[String].via(Flow[String].throttle(1, 1.second, 1, ThrottleMode.shaping)).toMat(sink2)(Keep.right)
    val bufferedSink2 = Flow[String].buffer(50, OverflowStrategy.backpressure).via(Flow[String].throttle(1, 1.second, 1, ThrottleMode.shaping)).toMat(sink2)(Keep.right)

//    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
//      import GraphDSL.Implicits._
//
//      val bcast = b.add(Broadcast[String](2))
//      factorials.map(_.toString) ~> bcast.in
//      bcast.out(0) ~> sink1
//      bcast.out(1) ~> sink2
//      ClosedShape
//    })


    source
      .map(s => (s, UUID.randomUUID()))
      .via(circuitBreaker.join(doubles))
      .runWith(Sink.seq)




//    val flow = Flow[(String, UUID)].mapAsyncUnordered(10) { elem =>
//      (ref ? elem).mapTo[(String, UUID)]
//    }
//
//    Source("a" :: "b" :: "c" :: Nil)
//      .map(s => (s, UUID.randomUUID()))
//      .via(circuitBreaker.join(flow))
//      .runWith(Sink.seq)
  }
}
