package net.davidwiles.w10k

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.{BinaryWebSocketFrame, CloseWebSocketFrame, ContinuationWebSocketFrame, PingWebSocketFrame, PongWebSocketFrame, TextWebSocketFrame, WebSocketFrame, WebSocketServerHandshakerFactory}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpMethod, HttpRequest}
import io.netty.util.AttributeKey

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.TimerTask
import java.util.function.BiConsumer

import scala.util.Try
import scala.concurrent.duration._


object Main {
  def main(args: Array[String]): Unit = {
    val interval = Try(Duration(System.getenv("PING_INTERVAL"))).toOption
      .getOrElse(10.seconds)
    Server(8080, interval).start()
  }
}


class Server(port: Int, interval: Duration) {
  private val bossGroup = new NioEventLoopGroup(1)
  private val workerGroup = new NioEventLoopGroup()

  private val connections = new ConcurrentHashMap[UUID, Channel]()

  private val timer = new java.util.Timer()
  private val task = new TimerTask {
    override def run(): Unit = {
      val now = System.currentTimeMillis()
      connections.forEach((uuid: UUID, ch: Channel) => {
        ch.writeAndFlush(new TextWebSocketFrame(s"The current time is $now"))
      })
    }
  }

  def start() = {
    timer.schedule(task, 1.second.toMillis, interval.toMillis)
    try {
      val b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
        .channel(classOf[NioServerSocketChannel])
        .handler(new LoggingHandler(LogLevel.ERROR))
        .childHandler(new ChannelInitializer[SocketChannel] {
          override def initChannel(ch: SocketChannel): Unit = {
            ch.pipeline
              .addLast(new HttpServerCodec)
              .addLast(new HttpObjectAggregator(65536))
              .addLast(new WebSocketServerCompressionHandler)
              .addLast(new WebsocketUpgradeHandler(connections))
          }
        });

      val ch = b.bind(port).sync().channel();
      ch.closeFuture().sync();
    } finally {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
    }
  }
}

object Server {
  def apply(port: Int, hub: Duration) = new Server(port, hub)
}


class WebsocketUpgradeHandler(connections: ConcurrentHashMap[UUID, Channel]) extends SimpleChannelInboundHandler[HttpRequest] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpRequest): Unit = {
    val headers = msg.headers();
    if (headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true) && headers
      .containsValue(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)) {
      if (HttpMethod.GET == msg.method()) {
        val wsFactory = new WebSocketServerHandshakerFactory("ws://" + headers.get(HttpHeaderNames.HOST), null, false, Integer.MAX_VALUE)
        val handshaker = wsFactory.newHandshaker(msg)
        if (handshaker == null) {
          WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
          handshaker.handshake(ctx.channel(), msg)
          registerConnection(ctx)
          ctx.pipeline().replace(this, "WebsocketHandler", new WebsocketHandler(connections))
        }
      }
    }
  }

  private def registerConnection(ctx: ChannelHandlerContext): Unit = {
    // Register with connections
    val uuid = UUID.randomUUID()
    ctx.channel().attr(WebsocketHandler.uuidKey).set(uuid)
    println(s"New connection: ${uuid.toString}")
    connections.put(uuid, ctx.channel())
  }
}

class WebsocketHandler(connections: ConcurrentHashMap[UUID, Channel]) extends SimpleChannelInboundHandler[WebSocketFrame] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: WebSocketFrame): Unit = {
    val uuid = Try(ctx.channel().attr(WebsocketHandler.uuidKey).get()).toOption
    val id = uuid.getOrElse("unknown")
    msg match {
      case frame: PingWebSocketFrame => println(s"Got ping from $id: ${frame.toString}")
      case frame: PongWebSocketFrame => println(s"Got pong from $id: ${frame.toString}")
      case frame: ContinuationWebSocketFrame => println(s"Got continuation from $id: ${frame.toString}")
      case frame: TextWebSocketFrame => println(s"Got text from $id: ${frame.toString}")
      case frame: BinaryWebSocketFrame => println(s"Got binary from $id: ${frame.toString}")
      case frame: CloseWebSocketFrame => println(s"Got close from $id: ${frame.toString}")
    }
  }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    Try(ctx.channel().attr(WebsocketHandler.uuidKey).get()).toOption.map { uuid =>
      println(s"removing connection $uuid")
      connections.remove(uuid)
    }
    super.channelUnregistered(ctx)
  }
}

object WebsocketHandler {
  val uuidKey: AttributeKey[UUID] = AttributeKey.valueOf("uuid")
}