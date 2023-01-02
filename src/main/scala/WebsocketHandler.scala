package net.davidwiles.w10k

import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInboundHandlerAdapter, ChannelInitializer, SimpleChannelInboundHandler}
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.websocketx.{BinaryWebSocketFrame, CloseWebSocketFrame, ContinuationWebSocketFrame, PingWebSocketFrame, PongWebSocketFrame, TextWebSocketFrame, WebSocketFrame, WebSocketServerHandshakerFactory}
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpMethod, HttpObjectAggregator, HttpRequest, HttpServerCodec}
import io.netty.util.AttributeKey

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.util.Try


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
      case frame: CloseWebSocketFrame =>
        println(s"Got close from $id: ${frame.toString}")
        connections.remove(uuid)
      case _ => connections.remove(uuid)
    }
  }
}

object WebsocketHandler {
  val uuidKey: AttributeKey[UUID] = AttributeKey.valueOf("uuid")
}