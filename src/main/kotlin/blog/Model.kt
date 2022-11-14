package blog

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import java.time.format.DateTimeFormatter
import java.util.UUID

val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

typealias ReplyTo = ActorRef<StatusReply<Response>>

interface CborSerializable
interface Command: CborSerializable { val replyTo: ReplyTo }
interface Event: CborSerializable
interface Entity: CborSerializable { val id: UUID }
interface Response { val id: UUID }

data class DeleteAll(override val replyTo: ReplyTo): Command

data class DeleteResponse(override val id: UUID = UUID.randomUUID()): Response


// experimental, maybe replcae UserState with this?
data class VGraph<T>(val map: MutableMap<T, MutableList<T>> = mutableMapOf()) {
  fun addVertex(v: T): MutableList<T>? = map[v] ?: map.put(v, mutableListOf())
  fun addEdge(v: T, t: T, bid: Boolean) {
    if (!containsVertex(v)) addVertex(v)
    if (!containsVertex(t)) addVertex(t)
    map[v]?.add(t)
    if (bid) map[t]?.add(v)
  }
  fun containsVertex(v: T): Boolean = map.containsKey(v)
  fun containsEdge(v: T, t: T): Boolean = map[v]?.contains(t) ?: false
  fun vertexCount(): Int = map.size
  fun edgeCount(): Int = map.values.fold(0) { acc, l -> acc + l.size }
}
