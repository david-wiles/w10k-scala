# W10k Challenge (Scala)

This is a simple web server to see if we can handle 10k concurrent websocket connections, and what limits the server
would have in different situations. The server only does a couple things:

* Prints messages it receives
* Sends the current time to all websockets at the interval defined by `PING_INTERVAL`

Critically, this server is only given 0.5 CPUs and 1Gi of memory.

The implementation currently uses Netty. Most websocket server implementations in Scala currently use Akka, but the 
framework should generally be avoided due to new exorbitant licensing fees in new (2.6+) versions.

The server can be built with 

```
./build.sh
```

And the container started with 

```
docker run --cpus="0.5" --memory="1Gi" --env PING_INTERVAL=10s -p 8080:8080 w10k-scala:v1
```
