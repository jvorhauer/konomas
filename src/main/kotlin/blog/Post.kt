package blog

import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.data.cassandra.core.ReactiveCassandraOperations
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.CassandraType.Name.*
import org.springframework.data.cassandra.core.mapping.Indexed
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.core.query.Criteria
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.domain.Pageable
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.valiktor.functions.hasSize
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
import reactor.core.publisher.Flux
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
    @Indexed
    val author: String,
    val viewed: Int = 0
) {
    companion object {
        val klass = Post::class.java
    }

    fun toResponse(user: UserResponse) =
        PostResponse(id.toString(), title, body, written.toString(), user, viewed)
}

data class CreatePost(
    val title: String,
    val body: String
) {
    init {
        validate(this) {
            validate(CreatePost::title).isNotBlank().hasSize(min = 3, max = 1024)
            validate(CreatePost::body).isNotBlank()
        }
    }
}

data class PostViewedEvent(
    val post: Post,
    val src: Any
) : ApplicationEvent(src)


data class PostResponse(
    val id: String,
    val title: String,
    val body: String,
    val written: String,
    val author: UserResponse,
    val viewed: Int
) {
    companion object {
        val klass = PostResponse::class.java
    }
}

class PostRepo(private val ops: ReactiveCassandraOperations) {
    fun save(p: Post): Mono<Post> = ops.insert(p)
    fun findById(id: UUID): Mono<Post> = ops.selectOneById(id, Post.klass)
    fun paged(author: String, page: Int = 0, size: Int = 20): Flux<Post> =
        ops.select(
            Query.query(Criteria.where("author").`is`(author))
                  .pageRequest(Pageable.ofSize(size).withPage(page))
                  .withAllowFiltering(),
            Post.klass
        )
}

class PostHandler(
    private val repo: PostRepo,
    private val users: UserRepo,
    private val authenticator: UserHandler,
    private val publisher: ApplicationEventPublisher
) {
    fun save(req: ServerRequest): Mono<ServerResponse> =
        authenticator.authenticate(req).flatMap<ServerResponse?> { tup ->
            tup.t2.bodyToMono(CreatePost::class.java)
                  .map { Post(title = it.title, body = it.body, author = tup.t1.email) }
                  .flatMap { repo.save(it) }
                  .flatMap { post ->
                      ok().body(
                          Mono.just(post)
                                .map { it.toResponse(tup.t1.toResponse()) }, PostResponse.klass
                      )
                  }
        }.switchIfEmpty(UNAUTH)

    fun one(req: ServerRequest): Mono<ServerResponse> =
        req.monoPathVar("id")
              .flatMap { id -> repo.findById(UUID.fromString(id)) }
              .doOnNext { publisher.publishEvent(PostViewedEvent(it, this)) }
              .flatMap { p ->
                  users.findById(p.author)
                        .map { u -> p.toResponse(u.toResponse()) }
              }
              .flatMap { ok().body(Mono.just(it), PostResponse.klass) }

    fun paged(req: ServerRequest): Mono<ServerResponse> {
        val flux = req.monoPathVar("id")
              .flatMapMany { id -> repo.paged(id) }
              .flatMap { post ->
                  users.findById(post.author).map { u -> post.toResponse(u.toResponse()) }
              }
        return ok().body(flux, PostResponse.klass)
    }
}

class NoteEventListener(private val repo: PostRepo) : ApplicationListener<PostViewedEvent> {
    override fun onApplicationEvent(event: PostViewedEvent) {
        val post = event.post
        repo.save(post.copy(viewed = post.viewed.plus(1))).subscribe()
    }
}
