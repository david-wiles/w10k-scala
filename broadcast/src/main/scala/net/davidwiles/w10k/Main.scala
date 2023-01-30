package net.davidwiles.w10k

import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s._
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all._
import fs2.{Chunk, Pipe, Stream}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import java.util.concurrent.ConcurrentHashMap
import java.util.{Timer, TimerTask, UUID}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try


object Main extends IOApp {

  private val connections = new ConcurrentHashMap[UUID, Queue[IO, WebSocketFrame]]()

  private def routes(ws: WebSocketBuilder2[IO]) = HttpRoutes.of[IO] {
    case GET -> Root / "ws" =>
      val uuid = UUID.randomUUID()

      // Print all text messages received
      val receive: Pipe[IO, WebSocketFrame, Unit] = in => in.evalMap {
        case text: WebSocketFrame.Text => IO(println(s"got text frame: ${text.data.decodeUtf8.getOrElse("???").trim}"))
      }

      // Create a FIFO queue of size 1 to send messages to the websocket
      Queue.bounded[IO, WebSocketFrame](1).flatMap { q =>
        println(s"Adding connection $uuid")
        connections.put(uuid, q)

        // Removes the websocket from `connections` upon disconnect. This may block
        // the thread if we are trying to write to the websocket at the same instant
        ws.withOnClose {
          IO.blocking {
            println(s"Removing connection $uuid")
            connections.remove(uuid)
          }
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
      .withHttpWebSocketApp(ws => routes(ws).orNotFound)
      .build

    // Create a timer which broadcasts a message to every websocket every interval
    val timer = new Timer()
    val task = new TimerTask {
      override def run(): Unit = {
        val now = System.currentTimeMillis()
        connections.forEach((uuid: UUID, q: Queue[IO, WebSocketFrame]) => {
          q.offer(WebSocketFrame.Text(s"current time: $now")).unsafeRunAndForget()
        })
      }
    }

    timer.schedule(task, 0, interval.toMillis)

    Stream.resource(serverResource >> Resource.never)
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
