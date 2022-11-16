package blog

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import java.time.format.DateTimeFormatter
import java.util.UUID

val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

typealias ReplyTo = ActorRef<StatusReply<Response>>

interface CborSerializable
interface Command: CborSerializable
interface Event: CborSerializable
interface Entity: CborSerializable { val id: UUID }
interface Response { val id: UUID }
