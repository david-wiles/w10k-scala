package net.davidwiles.w10k

import scala.concurrent.duration._
import scala.util.Try

object Main {
  def main(args: Array[String]): Unit = {
    val interval = Try(Duration(System.getenv("PING_INTERVAL"))).toOption
      .getOrElse(10.seconds)
    Server(8080, interval).start()
  }
}
