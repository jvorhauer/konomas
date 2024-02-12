package blog.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply

data class Tag(override val id: Long, val label: String): Entity {
  fun toResponse() = TagResponse(id, label)
}

data class CreateTagReq(val label: String) {
  fun toCommand(replyTo: ActorRef<StatusReply<TagResponse>>) = CreateTag(nextId(), label, replyTo)
}

data class TagResponse(override val id: Long, val label: String): Response

data class CreateTag(val id: Long, val label: String, val replyTo: ActorRef<StatusReply<TagResponse>>): Command {
  fun toEvent() = TagCreated(id, label)
}

data class TagCreated(val id: Long, val label: String): Event {
  fun toEntity() = Tag(id, label)
}
