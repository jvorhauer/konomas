package blog.model

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import io.hypersistence.tsid.TSID

data class Comment(
  val id: TSID,
  val user: TSID,
  val owner: TSID, // Note or Task
  val parent: TSID?,
  val text: String,
  val score: Int
) {
  fun toResponse() = CommentResponse(id.toLong(), user.toLong(), owner.toLong(), parent?.toLong(), text, score)
}

data class CreateCommentRequest(
  val user: Long,
  val owner: Long,
  val parent: Long?,
  val text: String,
  val score: Int = 0
) {
  fun toCommand(replyTo: ActorRef<StatusReply<CommentResponse>>) = CreateComment(user.toTSID(), owner.toTSID(), parent.toTSID(), text, score, replyTo)
}

data class CreateComment(
  val user: TSID,
  val owner: TSID,
  val parent: TSID?,
  val text: String,
  val score: Int,
  val replyTo: ActorRef<StatusReply<CommentResponse>>,
  val id: TSID = nextId(),
): Command {
  fun toEvent() = CommentCreated(id, user, owner, parent, text, score)
}

data class CommentCreated(
  val id: TSID,
  val user: TSID,
  val owner: TSID,
  val parent: TSID?,
  val text: String,
  val score: Int,
): Event {
  fun toEntity() = Comment(id, user, owner, parent, text, score)
}

data class CommentResponse(
  override val id: Long,
  val user: Long,
  val owner: Long, // Note or Task
  val parent: Long?,
  val text: String,
  val score: Int
): Response
