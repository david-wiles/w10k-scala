package net.davidwiles.w10k

import cats.effect.std.Queue
import com.comcast.ip4s._
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all._
import com.typesafe.scalalogging.LazyLogging
import fs2.{Pipe, Stream}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try


object Main extends IOApp with LazyLogging {

  case class QueueMessage(q: Queue[IO, WebSocketFrame], frame: WebSocketFrame) {
    def tryOfferFrame: IO[Boolean] = q.tryOffer(frame)
  }

  private val connections = new ConcurrentHashMap[UUID, Queue[IO, WebSocketFrame]]()

  private def routes(ws: WebSocketBuilder2[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "ws" =>
      val uuid = UUID.randomUUID()

      // Print all text messages received
      val receive: Pipe[IO, WebSocketFrame, Unit] = in => in.evalMap {
        case text: WebSocketFrame.Text =>
          IO(logger.debug(s"got text frame from $uuid: ${text.data.decodeUtf8.getOrElse("???").trim}"))
      }

      // Create a FIFO queue of size 1 to send messages to the websocket
      Queue.bounded[IO, WebSocketFrame](1).flatMap { q =>
        connections.put(uuid, q)
        // Removes the websocket from `connections` upon disconnect. This may block
        // the thread if we are trying to write to the websocket at the same instant
        ws.withOnClose {
          IO(connections.remove(uuid))
        }.build(Stream.fromQueueUnterminated(q), receive)
      }
  }

  def run(args: List[String]): IO[ExitCode] = {
    val interval = Try(Duration(System.getenv("PING_INTERVAL"))).toOption
      .collect { case d: FiniteDuration => d }
      .getOrElse(10.seconds)

    val serverResource = EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withMaxConnections(10000)
      .withHttpWebSocketApp(ws => routes(ws).orNotFound)
      .build

    val broadcast = Stream.awakeEvery[IO](interval)
      .evalMap { _ =>
        val msg = s"current time: ${System.currentTimeMillis()}"
        logger.info(s"broadcasting '$msg'")
        IO(connections.values().asScala.toSeq.map(q => QueueMessage(q, WebSocketFrame.Text(msg))))
      }
      .flatMap(Stream.emits)
      .parEvalMapUnorderedUnbounded(_.tryOfferFrame)

    Stream.resource(serverResource >> Resource.never).concurrently(broadcast)
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
