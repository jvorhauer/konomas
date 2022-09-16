package blog

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.CassandraType.Name.*
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

@Table("posts")
data class Post(
    @PrimaryKey
    @CassandraType(type = UUID)
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val body: String,
    val written: LocalDateTime = LocalDateTime.now(),
    val author: String
) {
    fun toResponse(user: UserResponse) = PostResponse(
        id.toString(), title, body, written.toString(), user
    )
}

data class CreatePost(
    val title: String,
    val body: String
) {
    init {
        validate(this) {
            validate(CreatePost::title).isNotBlank()
            validate(CreatePost::body).isNotBlank()
        }
    }
}

data class PostResponse(
    val id: String,
    val title: String,
    val body: String,
    val written: String,
    val author: UserResponse
)

@Repository
interface PostRepo: ReactiveCassandraRepository<Post, UUID>

@Service
class PostHandler(
    @Autowired private val repo: PostRepo,
    @Autowired private val userHandler: UserHandler
) {
    fun save(req: ServerRequest): Mono<ServerResponse> =
        userHandler.authenticate(req).flatMap<ServerResponse?> {tup ->
            tup.t2.bodyToMono(CreatePost::class.java)
                  .map { Post(title = it.title, body = it.body, author = tup.t1.email ) }
                  .flatMap { repo.save(it) }
                  .flatMap { ok().body(Mono.just(it)
                        .map { it.toResponse(tup.t1.toResponse()) }, PostResponse::class.java)}
        }.switchIfEmpty(UNAUTH)
}
