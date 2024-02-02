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
  fun toResponse() = CommentResponse(id.toString(), user.toString(), owner.toString(), parent?.toString(), text, score)
}

data class CreateCommentRequest(
  val user: String,
  val owner: String,
  val parent: String?,
  val text: String,
  val score: Int = 0
) {
  fun validate() = Validator.validate(this)
  fun toCommand(replyTo: ActorRef<StatusReply<CommentResponse>>) = CreateComment(user.toTSID(), owner.toTSID(), parent?.toTSID(), text, score, replyTo)
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
  override val id: String,
  val user: String,
  val owner: String, // Note or Task
  val parent: String?,
  val text: String,
  val score: Int
): Response
