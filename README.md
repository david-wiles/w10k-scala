# W10k Challenge (Scala)

This is a simple web server to see if we can handle 10k concurrent websocket connections, and what limits the server
would have in different situations. 

The implementation currently uses Netty. Most websocket server implementations in Scala currently use Akka, but the
framework should generally be avoided due to new exorbitant licensing fees in new (2.6+) versions.

## Broadcast

This server broadcasts messages to all clients at a set interval.

* Prints messages it receives
* Sends the current time to all websockets at the interval defined by `PING_INTERVAL`

## Client2Client

This program will forward messages from one client to another one, using a uuid passed in the message text.

You will need to install `sbt` to build the jars. You can do this with 

```
brew install sbt
```

To deploy the jars to a DigitalOcean droplet, you can use `./deploy.sh`. This can be done with 

```
./deploy.sh yourdomain.com
```

Be sure to add your private key and DigitalOcean token to tf/terraform.tfvars.
