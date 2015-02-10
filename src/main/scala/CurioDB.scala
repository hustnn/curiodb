
package curiodb

import akka.actor.{ActorSystem, Actor, ActorSelection, ActorRef, ActorLogging, Cancellable, Props}
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import scala.collection.mutable.{ArrayBuffer, Map => MutableMap, Set, LinkedHashSet}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import java.net.InetSocketAddress


class CommandSpec(val args: Any, val default: (Seq[String] => Any))

object CommandSpec {
  def apply(args: Any = 0, default: (Seq[String] => Any) = (_ => ())) = {
    new CommandSpec(args, default)
  }
}

object Commands {

  val many = Int.MaxValue
  val evens = 2 to many by 2

  val error    = (_: Seq[String]) => "error"
  val nil      = (_: Seq[String]) => "nil"
  val ok       = (_: Seq[String]) => "OK"
  val zero     = (_: Seq[String]) => 0
  val negative = (_: Seq[String]) => -1
  val nils     = (x: Seq[String]) => x.map(_ => nil)
  val zeros    = (x: Seq[String]) => x.map(_ => zero)
  val seq      = (_: Seq[String]) => Seq()
  val string   = (_: Seq[String]) => ""
  val scan     = (_: Seq[String]) => Seq("0", "")

  val specs = Map(

    "string" -> Map(
      "append"       -> CommandSpec(args = 1),
      "bitcount"     -> CommandSpec(args = 0 to 2, default = zero),
      "bitop"        -> CommandSpec(args = 3 to many, default = zero),
      "bitpos"       -> CommandSpec(args = 1 to 3, default = negative),
      "decr"         -> CommandSpec(),
      "decrby"       -> CommandSpec(args = 1),
      "get"          -> CommandSpec(default = nil),
      "getbit"       -> CommandSpec(args = 1, zero),
      "getrange"     -> CommandSpec(args = 2, default = string),
      "getset"       -> CommandSpec(args = 1),
      "incr"         -> CommandSpec(),
      "incrby"       -> CommandSpec(args = 1),
      "incrbyfloat"  -> CommandSpec(args = 1),
      "psetex"       -> CommandSpec(args = 2),
      "set"          -> CommandSpec(args = 1 to 4),
      "setbit"       -> CommandSpec(args = 2),
      "setex"        -> CommandSpec(args = 2),
      "setnx"        -> CommandSpec(args = 1, default = zero),
      "setrange"     -> CommandSpec(args = 2),
      "strlen"       -> CommandSpec(default = zero)
    ),

    "hash" -> Map(
      "hdel"         -> CommandSpec(args = 1 to many, default = zeros),
      "hexists"      -> CommandSpec(args = 1, default = zero),
      "hget"         -> CommandSpec(args = 1, default = nil),
      "hgetall"      -> CommandSpec(default = seq),
      "hincrby"      -> CommandSpec(args = 2),
      "hincrbyfloat" -> CommandSpec(args = 2),
      "hkeys"        -> CommandSpec(default = seq),
      "hlen"         -> CommandSpec(default = zero),
      "hmget"        -> CommandSpec(args = 1 to many, default = nils),
      "hmset"        -> CommandSpec(args = evens),
      "hscan"        -> CommandSpec(args = 1 to 3, default = scan),
      "hset"         -> CommandSpec(args = 2),
      "hsetnx"       -> CommandSpec(args = 2),
      "hvals"        -> CommandSpec(default = seq)
    ),

    "list" -> Map(
      "blpop"        -> CommandSpec(args = 1 to many, default = nil),
      "brpop"        -> CommandSpec(args = 1 to many, default = nil),
      "brpoplpush"   -> CommandSpec(args = 2, default = nil),
      "lindex"       -> CommandSpec(args = 1, default = nil),
      "linsert"      -> CommandSpec(args = 3, default = zero),
      "llen"         -> CommandSpec(default = zero),
      "lpop"         -> CommandSpec(default = nil),
      "lpush"        -> CommandSpec(args = 1 to many),
      "lpushx"       -> CommandSpec(args = 1, default = zero),
      "lrange"       -> CommandSpec(args = 2, default = seq),
      "lrem"         -> CommandSpec(args = 2, default = zero),
      "lset"         -> CommandSpec(args = 2, default = error),
      "ltrim"        -> CommandSpec(args = 2, default = ok),
      "rpop"         -> CommandSpec(default = nil),
      "rpoplpush"    -> CommandSpec(args = 1, default = nil),
      "rpush"        -> CommandSpec(args = 1 to many),
      "rpushx"       -> CommandSpec(args = 1, default = zero)
    ),

    "set" -> Map(
      "sadd"         -> CommandSpec(args = 1 to many),
      "scard"        -> CommandSpec(default = zero),
      "sdiff"        -> CommandSpec(args = 0 to many, default = seq),
      "sdiffstore"   -> CommandSpec(args = 1 to many, default = zero),
      "sinter"       -> CommandSpec(args = 0 to many, default = seq),
      "sinterstore"  -> CommandSpec(args = 1 to many, default = zero),
      "sismember"    -> CommandSpec(args = 1, default = zero),
      "smembers"     -> CommandSpec(args = 1 to many, default = seq),
      "smove"        -> CommandSpec(args = 2, default = error),
      "spop"         -> CommandSpec(default = nil),
      "srandmember"  -> CommandSpec(args = 0 to 1, default = nil),
      "srem"         -> CommandSpec(args = 1 to many, default = zero),
      "sscan"        -> CommandSpec(args = 1 to 3, default = scan),
      "sunion"       -> CommandSpec(args = 0 to many, default = seq),
      "sunionstore"  -> CommandSpec(args = 1 to many, default = zero)
    ),

    "keys" -> Map(
      "del"          -> CommandSpec(args = 1 to many),
      "exists"       -> CommandSpec(args = 1),
      "expire"       -> CommandSpec(args = 2),
      "expireat"     -> CommandSpec(args = 2),
      "keys"         -> CommandSpec(args = 1),
      "mget"         -> CommandSpec(args = 1 to many),
      "mset"         -> CommandSpec(args = evens),
      "msetnx"       -> CommandSpec(args = evens),
      "persist"      -> CommandSpec(args = 1),
      "pexpire"      -> CommandSpec(args = 2),
      "pexpireat"    -> CommandSpec(args = 2),
      "pttl"         -> CommandSpec(args = 1),
      "randomkey"    -> CommandSpec(),
      "rename"       -> CommandSpec(args = 2, default = error),
      "renamenx"     -> CommandSpec(args = 2, default = error),
      "scan"         -> CommandSpec(args = 1 to 3),
      "sort"         -> CommandSpec(args = 1 to many, default = seq),
      "ttl"          -> CommandSpec(args = 1),
      "type"         -> CommandSpec(args = 1)
    )

  )

  def nodeSpecs(command: String) = specs.find(_._2.contains(command))

  def nodeType(command: String) =
    nodeSpecs(command) match {
      case Some((nodeType, _)) => nodeType
      case None => ""
    }

  def default(command: String, args: Seq[String]) =
    nodeSpecs(command) match {
      case Some((_, specs)) => specs(command).default(args)
      case None => ()
    }

  def argsInRange(command: String, args: Seq[String]) =
    nodeSpecs(command) match {
      case Some((_, specs)) =>
        specs(command).args match {
          case fixed: Int => args.size == fixed
          case range: Range => range.contains(args.size)
        }
      case None => false
    }

}

case class Payload(input: Seq[Any] = Seq(), toClient: Option[ActorRef] = None, toNode: Option[ActorRef] = None) {

  val command = if (input.size > 0) input(0).toString else ""
  val nodeType = Commands.nodeType(command)
  val forKeyNode = nodeType == "keys"
  val key = if (forKeyNode) "keys" else if (input.size > 1) input(1).toString else ""
  val args = input.slice(if (forKeyNode) 1 else 2, input.size).map(_.toString)

  def deliver(response: Any) = {
    response match {
      case () =>
      case _ =>

        toClient match {
          case None =>
          case Some(destination) =>
            val message = response match {
              case x: Iterable[Any] => x.mkString("\n")
              case x: Boolean => if (x) "1" else "0"
              case x => x.toString
            }
            destination ! Tcp.Write(ByteString(s"$message\n"))
        }

        toNode match {
          case None =>
          case Some(destination) => destination ! Response(response, key)
        }

    }
  }

}

case class Unrouted(payload: Payload)

case class Response(value: Any, key: String)

abstract class BaseActor extends Actor with ActorLogging {
  def route(payload: Payload) =
    context.system.actorSelection("/user/keys") ! Unrouted(payload)
}

abstract class Node extends BaseActor {

  implicit var payload = Payload()
  type Run = PartialFunction[String, Any]
  def run: Run
  def args = payload.args

  def receive = {
    case "del" => log.debug("Deleted"); context stop self
    case p: Payload =>
      payload = p
      val running = s"${p.command} ${p.key} ${args.mkString(" ").trim}"
      val response = try {
        run(p.command)
      } catch {case e: Throwable => log.error(s"$e ($running)"); "error"}
      log.debug(s"Running ${running} -> ${response}".replace("\n", " "))
      payload.deliver(response)
  }

  def scan(values: Iterable[String]) = {
    val count = if (args.size >= 3) args(2).toInt else 10
    val start = if (args.size >= 1) args(0).toInt else 0
    val end = start + count
    val filtered = if (args.size >= 2) {
      val regex = ("^" + args(1).map {
        case '.'|'('|')'|'+'|'|'|'^'|'$'|'@'|'%'|'\\' => "\\" + _
        case '*' => ".*"
        case '?' => "."
        case c => c
      }.mkString("") + "$").r
      values.filter(regex.pattern.matcher(_).matches)
    } else values
    val next = if (end < filtered.size) end else 0
    Seq(next.toString) ++ filtered.slice(start, end)
  }

  def argPairs = (0 to args.size - 2 by 2).map {i => (args(i), args(i + 1))}

}

class StringNode extends Node {

  var value = ""

  def valueOrZero = if (value == "") "0" else value

  def expire(command: String) = route(Payload(Seq(command, payload.key, args(1))))

  def run = {
    case "get"         => value
    case "set"         => value = args(0); "OK"
    case "setnx"       => run("set"); true
    case "getset"      => val x = value; value = args(0); x
    case "append"      => value += args(0); value
    case "getrange"    => value.slice(args(0).toInt, args(1).toInt)
    case "setrange"    => value.patch(args(0).toInt, args(1), 1)
    case "strlen"      => value.size
    case "incr"        => value = (valueOrZero.toInt + 1).toString; value
    case "incrby"      => value = (valueOrZero.toInt + args(0).toInt).toString; value
    case "incrbyfloat" => value = (valueOrZero.toFloat + args(0).toFloat).toString; value
    case "decr"        => value = (valueOrZero.toInt - 1).toString; value
    case "decrby"      => value = (valueOrZero.toInt - args(0).toInt).toString; value
    case "bitcount"    => value.getBytes.map{_.toInt.toBinaryString.count(_ == "1")}.sum
    case "bitop"       => "Not implemented"
    case "bitpos"      => "Not implemented"
    case "getbit"      => "Not implemented"
    case "setbit"      => "Not implemented"
    case "setex"       => val x = run("set"); expire("expire"); x
    case "psetex"      => val x = run("set"); expire("pexpire"); x
  }

}

class BaseHashNode[T] extends Node {

  var value = MutableMap[String, T]()

  def exists = value.contains(args(0))

  def run = {
    case "hkeys"   => value.keys
    case "hexists" => exists
    case "hscan"   => scan(value.keys)
  }

}

class HashNode extends BaseHashNode[String] {

  def set(arg: Any) = {val x = arg.toString; value(args(0)) = x; x}

  override def run = ({
    case "hget"         => value.get(args(0))
    case "hsetnx"       => if (exists) run("hset") else false
    case "hgetall"      => value.map(x => Seq(x._1, x._2)).flatten
    case "hvals"        => value.values
    case "hdel"         => val x = run("hexists"); value -= args(0); x
    case "hlen"         => value.size
    case "hmget"        => args.map(value.get(_))
    case "hmset"        => argPairs.foreach {args => value(args._1) = args._2}; "OK"
    case "hincrby"      => set(value.getOrElse(args(0), "0").toInt + args(1).toInt)
    case "hincrbyfloat" => set(value.getOrElse(args(0), "0").toFloat + args(1).toFloat)
    case "hset"         => val x = !exists; set(args(1)); x
  }: Run) orElse super.run

}

class ListNode extends Node {

  var value = ArrayBuffer[String]()
  var blocked = LinkedHashSet[Payload]()

  def slice = value.slice(args(0).toInt, args(1).toInt)

  def block: Any = {
    if (value.size == 0) {
      blocked += payload
      context.system.scheduler.scheduleOnce(args.last.toInt seconds) {
        blocked -= payload
        payload.deliver("nil")
      }; ()
    } else run(payload.command.tail)
  }

  def unblock = {
    while (value.size > 0 && blocked.size > 0) {
      payload = blocked.head
      blocked -= payload
      payload.deliver(run(payload.command.tail))
    }
  }

  def run = {
    case "lpush"      => args ++=: value; payload.deliver(run("llen")); unblock
    case "rpush"      => value ++= args; payload.deliver(run("llen")); unblock
    case "lpushx"     => run("lpush")
    case "rpushx"     => run("rpush")
    case "lpop"       => val x = value(0); value -= x; x
    case "rpop"       => val x = value.last; value.reduceToSize(value.size - 1); x
    case "lset"       => value(args(0).toInt) = args(1); payload.deliver("OK"); unblock
    case "lindex"     => value(args(0).toInt)
    case "lrem"       => value.remove(args(0).toInt)
    case "lrange"     => slice
    case "ltrim"      => value = slice; "OK"
    case "llen"       => value.size
    case "blpop"      => block
    case "brpop"      => block
    case "brpoplpush" => block
    case "rpoplpush"  => val x = run("rpop"); route(Payload("lpush" +: args :+ x.toString)); x
    case "linsert"    =>
      val i = value.indexOf(args(1)) + (if (args(0) == "AFTER") 1 else 0)
      if (i >= 0) {value.insert(i, args(2)); payload.deliver(run("llen")); unblock} else -1
  }

}

class SetNode extends Node {

  var value = Set[String]()

  def others(keys: Seq[String]) = {
    val timeout_ = 2 seconds
    implicit val timeout: Timeout = timeout_
    val futures = Future.traverse(keys.toList) {key =>
      context.system.actorSelection(s"/user/$key") ? Payload(Seq("smembers", key))
    }
    Await.result(futures, timeout_).asInstanceOf[Seq[Response]].map {response: Response =>
      response.value.asInstanceOf[Set[String]]
    }
  }

  def run = {
    case "sadd"        => val x = (args.toSet &~ value).size; value ++= args; x
    case "srem"        => val x = (args.toSet & value).size; value --= args; x
    case "scard"       => value.size
    case "sismember"   => value.contains(args(0))
    case "smembers"    => value
    case "srandmember" => value.toSeq(Random.nextInt(value.size))
    case "spop"        => val x = run("srandmember"); value -= x.toString; x
    case "sdiff"       => others(args).fold(value)(_ &~ _)
    case "sinter"      => others(args).fold(value)(_ & _)
    case "sunion"      => others(args).fold(value)(_ | _)
    case "sdiffstore"  => value = others(args).reduce(_ &~ _); run("scard")
    case "sinterstore" => value = others(args).reduce(_ & _); run("scard")
    case "sunionstore" => value = others(args).reduce(_ | _); run("scard")
    case "sscan"       => scan(value)
    case "smove"       =>
      val x = value.contains(args(1))
      if (x) {value -= args(1); route(Payload("sadd" +: args))}; x
  }

}

class Collector(keys: Seq[String], payload: Payload) extends BaseActor {

  val responses = MutableMap[String, Any]()

  keys.foreach {key => route(Payload(Seq("get", key), toNode=Option(self)))}

  def receive = {
    case response: Response =>
      responses(response.key) = response.value
      if (responses.size == keys.size) {
        payload.deliver(keys.map {key: String => responses(key)})
        context stop self
      }
  }

}

class NodeEntry(val node: ActorRef, val nodeType: String, var expiry: Option[(Long, Cancellable)] = None)

class KeyNode extends BaseHashNode[NodeEntry] {

  def expire(when: Long): Boolean = {
    val x = run("persist") != -2
    if (x) {
      val expires = ((when - System.currentTimeMillis).toInt milliseconds)
      value(args(0)).expiry = Option((when, context.system.scheduler.scheduleOnce(expires) {
        self ! Payload(Seq("del", args(0)))
      }))
    }; x
  }

  def ttl = {
    if (run("exists") == false) -2
    else value(args(0)).expiry match {
      case None => -1
      case Some((when, _)) => when - System.currentTimeMillis
    }
  }

  override def run = ({
    case "keys"      => run("hkeys")
    case "scan"      => run("hscan")
    case "exists"    => run("hexists")
    case "randomkey" => value.keys.toSeq(Random.nextInt(value.size))
    case "mget"      => context.system.actorOf(Props(new Collector(args, payload))); ()
    case "mset"      => argPairs.foreach {args => route(Payload(Seq("set", args._1, args._2)))}; "OK"
    case "msetnx"    => val x = argPairs.filter(args => !value.contains(args._1)).isEmpty; if (x) {run("mset")}; x
    case "ttl"       => ttl / 1000
    case "pttl"      => ttl
    case "expire"    => expire(System.currentTimeMillis + (args(1).toInt * 1000))
    case "pexpire"   => expire(System.currentTimeMillis + args(1).toInt)
    case "expireat"  => expire(args(1).toLong / 1000)
    case "pexpireat" => expire(args(1).toLong)
    case "sort"      => "Not implemented"
    case "type"      => if (exists) value(args(0)).nodeType else "nil"
    case "rename"    =>
      if (args(0) != args(1)) {
        if (value.contains(args(1))) value(args(1)).node ! "del"
        value(args(1)) = value(args(0))
        value -= args(0)
        "OK"
      } else "error"
    case "renamenx"  => val x = value.contains(args(1)); if (x) {run("rename")}; x
    case "del"       => val x = args.filter(value.contains(_)).map(value(_).node ! "del"); value --= args; x.size
    case "persist"   =>
      val x = exists
      if (x) {
        value(args(0)).expiry match {
          case None =>
          case Some((_, cancellable)) => cancellable.cancel()
        }
      }; x
  }: Run) orElse super.run

  override def receive = ({
    case Unrouted(payload) =>
      val exists = value.contains(payload.key)
      val cantExist = payload.command == "lpushx" || payload.command == "rpushx"
      val mustExist = payload.command == "setnx"
      val default = Commands.default(payload.command, payload.args)
      val invalid = exists && value(payload.key).nodeType != payload.nodeType
      if (payload.forKeyNode) {
        self ! payload
      } else if (invalid) {
        payload.deliver(s"Invalid command ${payload.command} for ${value(payload.key).nodeType}")
      } else if (exists && !cantExist) {
        value(payload.key).node ! payload
      } else if (!exists && default != ()) {
        payload.deliver(default)
      } else if (!exists && !mustExist) {
        val node = context.system.actorOf(payload.nodeType match {
          case "string" => Props[StringNode]
          case "hash"   => Props[HashNode]
          case "list"   => Props[ListNode]
          case "set"    => Props[SetNode]
        })
        value(payload.key) = new NodeEntry(node, payload.nodeType)
        node ! payload
      } else payload.deliver(0)
  }: Receive) orElse super.receive

}

class Connection extends BaseActor {

  val buffer = new StringBuilder()

  override def receive = {
    case Tcp.PeerClosed => log.debug("Disconnected"); context stop self
    case Tcp.Received(data) =>
      val received = data.utf8String
      buffer.append(received)
      if (received.endsWith("\n")) {
        val data = buffer.stripLineEnd; buffer.clear()
        val payload = new Payload(data.split(' '), toClient=Option(sender()))
        log.debug(s"Received ${data}".replace("\n", " "))
        if (payload.nodeType == "") {
          payload.deliver("Unknown command")
        } else if (payload.key == "") {
          payload.deliver("Missing key")
        } else if (!Commands.argsInRange(payload.command, payload.args)) {
          payload.deliver("Invalid number of args")
        } else {
          route(payload)
        }
      }
  }

}

class Server(host: String, port: Int) extends BaseActor {

  import context.system

  IO(Tcp) ! Tcp.Bind(self, new InetSocketAddress(host, port))

  def receive = {
    case Tcp.Bound(local) => log.debug(s"Listening on $local")
    case Tcp.Connected(remote, local) =>
      log.debug(s"Accepted connection from $remote")
      sender() ! Tcp.Register(context.actorOf(Props[Connection]))
  }

}

object CurioDB extends App {
  val system = ActorSystem()
  val host = system.settings.config.getString("curiodb.host")
  val port = system.settings.config.getInt("curiodb.port")
  system.actorOf(Props[KeyNode], "keys")
  system.actorOf(Props(new Server(host, port)), "server")
  system.awaitTermination()
}
