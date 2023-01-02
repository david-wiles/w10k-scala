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

import java.util.concurrent.ConcurrentHashMap
import java.util.{TimerTask, UUID}
import java.util.function.BiConsumer
import scala.concurrent.duration._

class Server(port: Int, interval: Duration) {
  private val bossGroup = new NioEventLoopGroup(1)
  private val workerGroup = new NioEventLoopGroup()

  private val connections = new ConcurrentHashMap[UUID, Channel]()

  private val timer = new java.util.Timer()
  private val task = new TimerTask {
    override def run(): Unit = {
      val now = System.currentTimeMillis()
      connections.forEach(new BiConsumer[UUID, Channel] {
        override def accept(uuid: UUID, ch: Channel): Unit = {
          ch.writeAndFlush(new TextWebSocketFrame(s"The current time is $now"))
        }
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
